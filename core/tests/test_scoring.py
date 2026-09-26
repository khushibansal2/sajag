"""Scoring engine tests.

The behaviours worth locking down are the ones a jury will probe: that the
wrong extinguisher cannot be rescued by a good average, that a hard fail stops
the module rather than deducting from it, and that the written check alone
never certifies anyone.
"""

import sys, os, json, unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from sajag_core.scoring import score_attempt, canonical_event_log, DEFAULT_MODEL

HERE = os.path.dirname(__file__)
SCENARIOS = os.path.join(HERE, "..", "scenarios")


def load(name):
    with open(os.path.join(SCENARIOS, name), encoding="utf-8") as fh:
        return json.load(fh)


def clean_fire_run():
    """A competent worker's attempt at FIRE-01."""
    return [
        {"beat": "1.1", "item": "fire-class", "chosen": ["electrical"]},
        {"beat": "1.1", "item": "classify-latency", "ms": 4200},
        {"beat": "1.1", "item": "safe-standoff", "value": 3.1},
        {"beat": "1.2", "item": "ext-choice", "chosen": ["co2"]},
        {"beat": "1.2", "item": "gauge-check", "value": True},
        {"beat": "1.2", "item": "select-latency", "ms": 3800},
        {"beat": "1.3", "item": "pass-sequence", "order": ["pull", "aim", "squeeze", "sweep"]},
        {"beat": "1.3", "item": "aim-angle", "value": 9.0},
        {"beat": "1.3", "item": "sweep-coverage", "value": 0.82},
        {"beat": "1.3", "item": "standoff", "value": 2.4},
        {"beat": "1.4", "item": "exit-choice", "chosen": ["exit_north"]},
        {"beat": "1.4", "item": "wall-contact", "value": 0.88},
        {"beat": "1.4", "item": "head-height", "value": 0.9},
        {"beat": "1.4", "item": "door-heat-check", "value": True},
        {"beat": "1.4", "item": "egress-latency", "ms": 21000},
        {"beat": "1.5", "item": "response-order",
         "order": ["alarm", "trip_conveyor", "alert_buddy", "assembly_point", "report_headcount"]},
        {"beat": "1.5", "item": "alarm-latency", "ms": 7400},
        {"beat": "1.5", "item": "written-check",
         "chosen": ["q1_b", "q2_a", "q3_c", "q4_a", "q5_b", "q6_c"]},
    ]


class TestFireModule(unittest.TestCase):
    def test_clean_run_passes(self):
        res = score_attempt(clean_fire_run(), load("fire-01.json"))
        self.assertTrue(res.passed, res.reasons)
        self.assertGreaterEqual(res.aggregate, 85)
        self.assertEqual(res.competencies["EQP-SEL"], 100)

    def test_water_on_electrical_is_a_hard_fail_not_a_deduction(self):
        events = clean_fire_run()
        events[3] = {"beat": "1.2", "item": "ext-choice", "chosen": ["water"]}
        events.append({"beat": "1.2", "type": "WATER_ON_ELECTRICAL"})
        res = score_attempt(events, load("fire-01.json"))
        self.assertFalse(res.passed)
        self.assertIn("1.2", res.replay_beats)
        self.assertTrue(any("hard fail" in r for r in res.reasons))

    def test_good_average_cannot_rescue_a_failed_floor(self):
        """Everything excellent except the cylinder choice. Aggregate stays high;
        the attempt still fails, because EQP-SEL has a floor of 100."""
        events = clean_fire_run()
        events[3] = {"beat": "1.2", "item": "ext-choice", "chosen": ["co2", "water"]}
        res = score_attempt(events, load("fire-01.json"))
        self.assertGreater(res.aggregate, 70)
        self.assertFalse(res.passed)
        self.assertTrue(any("EQP-SEL" in r for r in res.reasons))

    def test_written_check_alone_never_certifies(self):
        events = [{"beat": "1.5", "item": "written-check",
                   "chosen": ["q1_b", "q2_a", "q3_c", "q4_a", "q5_b", "q6_c"]}]
        res = score_attempt(events, load("fire-01.json"))
        self.assertFalse(res.passed)
        self.assertEqual(res.competencies["KNW"], 100)

    def test_aim_at_the_flames_loses_technique_marks(self):
        events = clean_fire_run()
        events[7] = {"beat": "1.3", "item": "aim-angle", "value": 34.0}
        res = score_attempt(events, load("fire-01.json"))
        self.assertLess(res.competencies["TECH"],
                        score_attempt(clean_fire_run(), load("fire-01.json")).competencies["TECH"])

    def test_out_of_order_response_gets_partial_credit(self):
        events = clean_fire_run()
        events[15] = {"beat": "1.5", "item": "response-order",
                      "order": ["trip_conveyor", "alarm", "alert_buddy",
                                "assembly_point", "report_headcount"]}
        res = score_attempt(events, load("fire-01.json"))
        self.assertLess(res.competencies["SEQ"], 100)
        self.assertGreater(res.competencies["SEQ"], 50)

    def test_missing_items_score_zero_not_crash(self):
        res = score_attempt([], load("fire-01.json"))
        self.assertFalse(res.passed)
        self.assertEqual(res.aggregate, 0)


class TestTierCalibration(unittest.TestCase):
    def test_same_elapsed_time_scores_higher_at_tier_3(self):
        """A worker who takes 15 s is not penalised the same way on a 2D drill
        as on a 6DoF walk-around — the thresholds scale, the floors don't."""
        events = clean_fire_run()
        events[1] = {"beat": "1.1", "item": "classify-latency", "ms": 15000}
        t1 = load("fire-01.json"); t1["_tier"] = 1
        t3 = load("fire-01.json"); t3["_tier"] = 3
        self.assertGreater(
            score_attempt(events, t3).competencies["HAZ-ID"],
            score_attempt(events, t1).competencies["HAZ-ID"],
        )

    def test_floors_are_identical_across_tiers(self):
        for tier in (1, 2, 3):
            sc = load("fire-01.json"); sc["_tier"] = tier
            res = score_attempt(clean_fire_run(), sc)
            self.assertTrue(res.passed)
        self.assertEqual(DEFAULT_MODEL["EQP-SEL"]["floor"], 100)


class TestGasModule(unittest.TestCase):
    def test_solo_entry_is_a_hard_fail(self):
        events = [
            {"beat": "2.4", "item": "standby-posted", "value": False},
            {"beat": "2.4", "type": "SOLO_ENTRY"},
        ]
        res = score_attempt(events, load("gas-01.json"))
        self.assertFalse(res.passed)
        self.assertIn("2.4", res.replay_beats)

    def test_dust_mask_is_forbidden_equipment(self):
        events = [{"beat": "2.3", "item": "ppe-set",
                   "chosen": ["scsr", "four_gas_detector", "dust_mask"]}]
        res = score_attempt(events, load("gas-01.json"))
        self.assertEqual(res.competencies["EQP-SEL"], 0)

    def test_entering_to_rescue_fails(self):
        events = [
            {"beat": "2.5", "item": "rescue-choice", "chosen": ["enter_to_rescue"]},
            {"beat": "2.5", "type": "UNPROTECTED_RESCUE_ENTRY"},
        ]
        res = score_attempt(events, load("gas-01.json"))
        self.assertFalse(res.passed)

    def test_thresholds_are_configuration(self):
        sc = load("gas-01.json")
        self.assertIn("site_thresholds", sc)
        self.assertIn("_note", sc["site_thresholds"])


class TestDeterminism(unittest.TestCase):
    def test_canonical_log_is_stable_across_key_order(self):
        a = [{"beat": "1.1", "item": "x", "value": 1.23456789}]
        b = [{"value": 1.23456789, "item": "x", "beat": "1.1"}]
        self.assertEqual(canonical_event_log(a), canonical_event_log(b))

    def test_scoring_is_pure(self):
        sc = load("fire-01.json")
        first = score_attempt(clean_fire_run(), sc)
        second = score_attempt(clean_fire_run(), sc)
        self.assertEqual(first.aggregate, second.aggregate)
        self.assertEqual(first.competencies, second.competencies)


if __name__ == "__main__":
    unittest.main(verbosity=2)
