package `in`.sajag.assess

import `in`.sajag.i18n.T

/**
 * What the worker sees and does in each beat, mapped onto the rubric item ids
 * in the core/scenarios JSON files. The scenario file owns HOW an action is scored;
 * this file owns HOW it is presented. Every task emits exactly the payload its
 * rubric kind expects — see Scoring.kt.
 *
 * TODO(domain reviewer): every instruction, option and quiz question here must
 * be checked by the mining-safety reviewer before it appears in a demo video.
 * Hindi lines need a native-speaker pass as well.
 */
enum class Hazard {
    ELECTRICAL_FIRE, EXTINGUISHER_RACK, FIRE_ATTACK, SMOKE_EGRESS, ALARM,
    GAS_LAYERS, GAS_DETECTOR, PPE_RACK, PERMIT_BOARD, SUMP_RESCUE,
}

/** [icon] is a Material icon name (see ui/Icons.kt), or a letter for quiz answers. */
data class Opt(val id: String, val icon: String, val label: T)

data class Question(val id: String, val prompt: T, val options: List<Opt>)

sealed interface Task {
    val itemId: String
    val prompt: T
}

data class ChoiceTask(
    override val itemId: String,
    override val prompt: T,
    val options: List<Opt>,
    val multi: Boolean = false,
    /** Rubric item that receives the reaction time for this choice. */
    val latencyItem: String? = null,
    /** option id -> hard-fail event type */
    val hardFailOn: Map<String, String> = emptyMap(),
) : Task

data class SequenceTask(
    override val itemId: String,
    override val prompt: T,
    val steps: List<Opt>,
    val latencyItem: String? = null,
    /** Reaction time is measured to the moment this step is tapped. */
    val latencyStep: String? = null,
    /** (required first step, hard-fail type if it is not first) */
    val mustStartWith: Pair<String, String>? = null,
) : Task

data class FlagTask(
    override val itemId: String,
    override val prompt: T,
    val yes: T,
    val no: T,
    val hardFailIfNo: String? = null,
) : Task

data class RangeTask(
    override val itemId: String,
    override val prompt: T,
    val min: Float,
    val max: Float,
    val step: Float,
    val unit: String,
    val start: Float,
    val hardFailAbove: Pair<Float, String>? = null,
    val hardFailBelow: Pair<Float, String>? = null,
) : Task

data class SweepTask(
    override val itemId: String,
    override val prompt: T,
    val vertical: Boolean = false,
) : Task

data class QuizTask(
    override val itemId: String,
    override val prompt: T,
    val questions: List<Question>,
) : Task

data class BeatContent(
    val beatId: String,
    val hazard: Hazard,
    val title: T,
    val teach: T,
    val tasks: List<Task>,
    /** Rubric item timed across the whole beat (hand-over to last task). */
    val beatLatencyItem: String? = null,
)

data class ModuleContent(
    val id: String,
    val asset: String,
    val icon: String,
    val title: T,
    val minutes: Int,
    val beats: List<BeatContent>,
)

data class ModuleCard(val id: String, val icon: String, val title: T, val available: Boolean)

/**
 * What to do in a real emergency, drawn from the same lessons as the drills.
 * [reportType] is the hazard type the report form starts with.
 */
data class EmergencyGuide(val id: String, val icon: String, val title: T, val steps: List<T>, val reportType: String)

object Modules {
    val cards = listOf(
        ModuleCard("FIRE-01", "local_fire_department", T("Fire & Explosion Response", "आग और विस्फोट प्रतिक्रिया"), true),
        ModuleCard("GAS-01", "cloud", T("Gas Leak & Confined Space", "गैस रिसाव और संवृत स्थान"), true),
        ModuleCard("MACH-01", "precision_manufacturing", T("Machinery & Conveyor Safety", "मशीनरी और कन्वेयर सुरक्षा"), false),
        ModuleCard("HEIGHT-01", "stairs", T("Working at Height", "ऊँचाई पर काम"), false),
        ModuleCard("ELEC-01", "electrical_services", T("Electrical Isolation (LOTO)", "विद्युत पृथक्करण (LOTO)"), false),
    )

    fun content(id: String): ModuleContent = when (id) {
        "FIRE-01" -> FIRE
        "GAS-01" -> GAS
        else -> throw IllegalArgumentException("module $id has no content yet")
    }

    fun title(id: String): T = cards.first { it.id == id }.title

    /** Words for a rubric item, for the training centre screen. */
    fun itemLabel(moduleId: String, itemId: String): T {
        val content = runCatching { content(moduleId) }.getOrNull() ?: return T(itemId, itemId)
        for (beat in content.beats) {
            for (task in beat.tasks) if (task.itemId == itemId) return task.prompt
        }
        for (beat in content.beats) {
            for (task in beat.tasks) {
                val latency = when (task) {
                    is ChoiceTask -> task.latencyItem
                    is SequenceTask -> task.latencyItem
                    else -> null
                }
                if (latency == itemId) return T("Speed: ${task.prompt.en}", "गति: ${task.prompt.hi}")
            }
            if (beat.beatLatencyItem == itemId) return T("Speed: ${beat.title.en}", "गति: ${beat.title.hi}")
        }
        return T(itemId, itemId)
    }

    /** Words for a STOP event: an action that would kill in a real mine. */
    fun stopLabel(type: String): T = STOP_LABELS[type] ?: T(type, type)

    private val STOP_LABELS = mapOf(
        "APPROACH_ENERGISED_PANEL" to T("Went too close to a live panel", "चालू पैनल के बहुत पास गए"),
        "WATER_ON_ELECTRICAL" to T("Water or foam on a live electrical fire", "चालू बिजली की आग पर पानी या फोम"),
        "UPRIGHT_IN_SMOKE" to T("Walked upright in thick smoke", "घने धुएँ में सीधे खड़े होकर चले"),
        "OPENED_HOT_DOOR" to T("Opened a door without feeling it first", "छूकर जाँचे बिना दरवाज़ा खोला"),
        "EXIT_PAST_SEAT_OF_FIRE" to T("Took the exit past the fire", "आग के पास वाले निकास से गए"),
        "DUST_MASK_AS_RESPIRATOR" to T("Chose a dust mask against gas", "गैस से बचने के लिए धूल मास्क चुना"),
        "ENTRY_BEFORE_ATMOSPHERE_TEST" to T("Entered before testing the air", "हवा जाँचे बिना प्रवेश किया"),
        "SOLO_ENTRY" to T("Entered without a standby person", "स्टैंडबाय व्यक्ति के बिना प्रवेश किया"),
        "UNPROTECTED_RESCUE_ENTRY" to T("Went in to rescue without protection", "बिना सुरक्षा बचाने अंदर गए"),
    )

    /**
     * TODO(domain reviewer): these steps repeat what the drills teach; the
     * mining-safety reviewer checks them with the drill content, and each site
     * adds its own emergency plan.
     */
    val EMERGENCIES = listOf(
        EmergencyGuide(
            "fire", "local_fire_department", T("Fire or smoke", "आग या धुआँ"),
            listOf(
                T("Raise the alarm. Shout FIRE and warn everyone near you.", "अलार्म बजाएँ। आग-आग चिल्लाएँ और आसपास सबको सावधान करें।"),
                T("If it is safe, stop the conveyor or machine.", "सुरक्षित हो तो कन्वेयर या मशीन बंद करें।"),
                T("A live electrical fire gets no water and no foam. Only a CO₂ extinguisher.", "चालू बिजली की आग पर पानी या फोम नहीं। सिर्फ CO₂ अग्निशामक।"),
                T("In smoke, stay low, keep one hand on the wall, and feel every door before you open it.", "धुएँ में नीचे झुके रहें, एक हाथ दीवार पर रखें, और हर दरवाज़ा खोलने से पहले छूकर देखें।"),
                T("Leave by the route away from the fire. Report at the assembly point for the headcount.", "आग से दूर वाले रास्ते से निकलें। गिनती के लिए असेंबली पॉइंट पर रिपोर्ट करें।"),
            ),
            reportType = "FIRE",
        ),
        EmergencyGuide(
            "gas", "air", T("Gas or bad air", "गैस या खराब हवा"),
            listOf(
                T("Stop work. Do not switch anything on or off.", "काम रोकें। कोई भी स्विच चालू या बंद न करें।"),
                T("Warn everyone near you and withdraw to fresh air.", "आसपास सबको सावधान करें और ताज़ी हवा में निकलें।"),
                T("If the air is not safe to breathe, put on your self-rescuer.", "हवा साँस लेने लायक न हो तो सेल्फ-रेस्क्यूअर पहनें।"),
                T("Tell your supervisor the reading and the place.", "रीडिंग और जगह अपने सुपरवाइज़र को बताएँ।"),
                T("Nobody goes back in until the air has been tested again.", "हवा दोबारा जाँचे जाने तक कोई वापस अंदर न जाए।"),
            ),
            reportType = "GAS",
        ),
        EmergencyGuide(
            "collapse", "personal_injury", T("Someone collapsed", "कोई गिर गया"),
            listOf(
                T("Do not rush in. The same bad air can take you too.", "दौड़कर अंदर न जाएँ। वही खराब हवा आपको भी ले सकती है।"),
                T("Raise the alarm and call the control room.", "अलार्म बजाएँ और कंट्रोल रूम को फ़ोन करें।"),
                T("From a confined space, pull them out with the lifeline from outside.", "संवृत स्थान से बाहर से ही लाइफ़लाइन से खींचें।"),
                T("Only trained rescuers with breathing sets go in.", "सिर्फ साँस सेट वाले प्रशिक्षित बचावकर्मी अंदर जाएँ।"),
            ),
            reportType = "OTHER",
        ),
    )

    private fun q(id: String, prompt: T, a: T, b: T, c: T) =
        Question(id, prompt, listOf(Opt("a", "A", a), Opt("b", "B", b), Opt("c", "C", c)))

    // ------------------------------------------------------------------ FIRE-01

    private val FIRE = ModuleContent(
        id = "FIRE-01", asset = "fire-01.json", icon = "local_fire_department", minutes = 9,
        title = T("Fire & Explosion Response", "आग और विस्फोट प्रतिक्रिया"),
        beats = listOf(
            BeatContent(
                "1.1", Hazard.ELECTRICAL_FIRE,
                T("Read the fire before you touch anything", "कुछ भी छूने से पहले आग को पहचानें"),
                T("A fire has started at the conveyor switchgear panel. First decide what kind of fire it is. A live electrical fire is not an ordinary fire. Keep your distance from the panel.",
                  "कन्वेयर के स्विचगियर पैनल में आग लगी है। पहले तय करें कि यह किस तरह की आग है। चालू बिजली की आग साधारण आग नहीं होती। पैनल से दूरी बनाए रखें।"),
                listOf(
                    ChoiceTask(
                        "fire-class", T("What kind of fire is this?", "यह किस तरह की आग है?"),
                        listOf(
                            Opt("electrical", "bolt", T("Electrical fire — live panel", "बिजली की आग — चालू पैनल")),
                            Opt("class_a", "forest", T("Ordinary fire — wood, paper, cloth", "साधारण आग — लकड़ी, कागज़, कपड़ा")),
                            Opt("class_b", "local_gas_station", T("Oil or fuel fire", "तेल या ईंधन की आग")),
                        ),
                        latencyItem = "classify-latency",
                    ),
                    RangeTask(
                        "safe-standoff", T("How far from the burning panel will you stand?", "जलते पैनल से आप कितनी दूर खड़े होंगे?"),
                        min = 0.5f, max = 8f, step = 0.5f, unit = "m", start = 0.5f,
                        hardFailBelow = 1.0f to "APPROACH_ENERGISED_PANEL",
                    ),
                ),
            ),
            BeatContent(
                "1.2", Hazard.EXTINGUISHER_RACK,
                T("Pick the right extinguisher", "सही अग्निशामक चुनें"),
                T("Water and foam conduct electricity. On a live panel the answer is a carbon dioxide extinguisher. Always check the pressure gauge before you take it.",
                  "पानी और फोम बिजली का प्रवाह करते हैं। चालू पैनल पर कार्बन डाइऑक्साइड अग्निशामक ही सही है। उठाने से पहले हमेशा प्रेशर गेज जाँचें।"),
                listOf(
                    FlagTask(
                        "gauge-check", T("Before you pick a cylinder — check its pressure gauge?", "सिलेंडर उठाने से पहले — क्या उसका प्रेशर गेज जाँचेंगे?"),
                        yes = T("Check the gauge", "गेज जाँचें"), no = T("Skip, no time", "छोड़ें, समय नहीं"),
                    ),
                    ChoiceTask(
                        "ext-choice", T("Which extinguisher do you take?", "आप कौन-सा अग्निशामक लेंगे?"),
                        listOf(
                            Opt("water", "water_drop", T("Water hose", "पानी का पाइप")),
                            Opt("foam", "bubble_chart", T("Foam extinguisher", "फोम अग्निशामक")),
                            Opt("co2", "fire_extinguisher", T("CO₂ extinguisher", "CO₂ अग्निशामक")),
                            Opt("sand", "grain", T("Sand bucket", "रेत की बाल्टी")),
                        ),
                        latencyItem = "select-latency",
                        hardFailOn = mapOf("water" to "WATER_ON_ELECTRICAL", "foam" to "WATER_ON_ELECTRICAL"),
                    ),
                ),
            ),
            BeatContent(
                "1.3", Hazard.FIRE_ATTACK,
                T("Pull, Aim, Squeeze, Sweep", "खींचें, निशाना, दबाएँ, घुमाएँ"),
                T("Aim at the base of the fire, not the flames. Sweep side to side across it. Stay two to three metres away.",
                  "लपटों पर नहीं, आग की जड़ पर निशाना लगाएँ। उसके आर-पार दाएँ-बाएँ घुमाएँ। दो से तीन मीटर दूर रहें।"),
                listOf(
                    SequenceTask(
                        "pass-sequence", T("Put the extinguisher actions in order", "अग्निशामक के कदम क्रम में लगाएँ"),
                        listOf(
                            Opt("pull", "pan_tool", T("Pull the cylinder off the rack", "सिलेंडर रैक से निकालें")),
                            Opt("aim", "gps_fixed", T("Aim at the base of the fire", "आग की जड़ पर निशाना")),
                            Opt("squeeze", "touch_app", T("Squeeze the lever", "लीवर दबाएँ")),
                            Opt("sweep", "swap_horiz", T("Sweep side to side", "दाएँ-बाएँ घुमाएँ")),
                        ),
                    ),
                    RangeTask(
                        "aim-angle", T("How far above the base of the fire will you aim?", "आग की जड़ से कितना ऊपर निशाना लगाएँगे?"),
                        min = 0f, max = 45f, step = 5f, unit = "°", start = 30f,
                    ),
                    SweepTask(
                        "sweep-coverage", T("Drag your finger across the base of the fire to spray it", "आग की जड़ पर उंगली घुमाकर स्प्रे करें"),
                    ),
                    RangeTask(
                        "standoff", T("Your distance while spraying?", "स्प्रे करते समय आपकी दूरी?"),
                        min = 0.5f, max = 6f, step = 0.5f, unit = "m", start = 1f,
                    ),
                ),
            ),
            BeatContent(
                "1.4", Hazard.SMOKE_EGRESS,
                T("Escape through thick smoke", "घने धुएँ से बाहर निकलें"),
                T("The fire is spreading and smoke is filling the gallery. When you cannot see: stay low, keep one hand on the wall, follow the exit beeps, and feel every door before you open it.",
                  "आग फैल रही है और गैलरी में धुआँ भर रहा है। जब दिखाई न दे: नीचे झुके रहें, एक हाथ दीवार पर रखें, निकास की बीप का पीछा करें, और हर दरवाज़ा खोलने से पहले छूकर देखें।"),
                listOf(
                    RangeTask(
                        "head-height", T("How high will your head be while you move?", "चलते समय आपका सिर कितनी ऊँचाई पर होगा?"),
                        min = 0.2f, max = 1.8f, step = 0.1f, unit = "m", start = 1.7f,
                        hardFailAbove = 1.4f to "UPRIGHT_IN_SMOKE",
                    ),
                    SweepTask(
                        "wall-contact", T("Keep your finger on the wall — drag all the way along it", "उंगली दीवार पर रखें — पूरी दीवार के साथ खींचें"),
                    ),
                    FlagTask(
                        "door-heat-check", T("You reach a closed door. What do you do?", "आप एक बंद दरवाज़े तक पहुँचे। क्या करेंगे?"),
                        yes = T("Feel it with the back of my hand first", "पहले हाथ के पिछले हिस्से से छूकर देखूँगा"),
                        no = T("Open it straight away", "तुरंत खोल दूँगा"),
                        hardFailIfNo = "OPENED_HOT_DOOR",
                    ),
                    ChoiceTask(
                        "exit-choice", T("Which exit do you take?", "आप कौन-सा निकास लेंगे?"),
                        listOf(
                            Opt("exit_north", "north", T("North exit — away from the fire, follow the beeps", "उत्तर निकास — आग से दूर, बीप का पीछा करें")),
                            Opt("exit_past_fire", "south", T("South exit — closer, but past the fire", "दक्षिण निकास — पास है, पर आग के पास से")),
                        ),
                        hardFailOn = mapOf("exit_past_fire" to "EXIT_PAST_SEAT_OF_FIRE"),
                    ),
                ),
                beatLatencyItem = "egress-latency",
            ),
            BeatContent(
                "1.5", Hazard.ALARM,
                T("Sequence the response", "प्रतिक्रिया का क्रम"),
                T("Surviving is not enough. Raise the alarm, trip the conveyor, alert your buddy, go to the assembly point, and report for the headcount.",
                  "सिर्फ बच निकलना काफ़ी नहीं। अलार्म बजाएँ, कन्वेयर बंद करें, साथी को सावधान करें, असेंबली पॉइंट पर जाएँ, और गिनती के लिए रिपोर्ट करें।"),
                listOf(
                    SequenceTask(
                        "response-order", T("Put the emergency response in order", "आपातकालीन प्रतिक्रिया क्रम में लगाएँ"),
                        listOf(
                            Opt("alarm", "notifications_active", T("Raise the alarm", "अलार्म बजाएँ")),
                            Opt("trip_conveyor", "stop_circle", T("Trip the conveyor", "कन्वेयर बंद करें")),
                            Opt("alert_buddy", "group", T("Alert your buddy", "साथी को सावधान करें")),
                            Opt("assembly_point", "place", T("Go to the assembly point", "असेंबली पॉइंट पर जाएँ")),
                            Opt("report_headcount", "fact_check", T("Report for headcount", "गिनती के लिए रिपोर्ट करें")),
                        ),
                        latencyItem = "alarm-latency", latencyStep = "alarm",
                    ),
                    QuizTask(
                        "written-check", T("Six quick questions", "छह छोटे सवाल"),
                        listOf(
                            q("q1", T("Which extinguisher is safe on a live electrical panel?", "चालू बिजली पैनल पर कौन-सा अग्निशामक सुरक्षित है?"),
                                T("Water", "पानी"), T("CO₂", "CO₂"), T("Foam", "फोम")),
                            q("q2", T("In thick smoke you should…", "घने धुएँ में आपको…"),
                                T("Stay low and keep a hand on the wall", "नीचे झुके रहें और हाथ दीवार पर रखें"), T("Stand up and run", "खड़े होकर दौड़ें"), T("Wait for the smoke to clear", "धुआँ छँटने का इंतज़ार करें")),
                            q("q3", T("Before opening a door during a fire…", "आग के दौरान दरवाज़ा खोलने से पहले…"),
                                T("Open it quickly", "जल्दी खोलें"), T("Kick it open", "लात मारकर खोलें"), T("Feel it with the back of your hand", "हाथ के पिछले हिस्से से छूकर देखें")),
                            q("q4", T("Where do you aim the extinguisher?", "अग्निशामक का निशाना कहाँ लगाएँ?"),
                                T("At the base of the fire", "आग की जड़ पर"), T("At the top of the flames", "लपटों के ऊपर"), T("Into the smoke", "धुएँ में")),
                            q("q5", T("What is the FIRST thing to do when you find a fire?", "आग दिखने पर सबसे पहला काम क्या है?"),
                                T("Collect your tools", "अपने औज़ार इकट्ठा करें"), T("Raise the alarm", "अलार्म बजाएँ"), T("Phone your family", "परिवार को फ़ोन करें")),
                            q("q6", T("After you evacuate, you must…", "बाहर निकलने के बाद आपको…"),
                                T("Go home", "घर जाना है"), T("Go back for your belongings", "सामान लेने वापस जाना है"), T("Report at the assembly point for headcount", "असेंबली पॉइंट पर गिनती के लिए रिपोर्ट करना है")),
                        ),
                    ),
                ),
            ),
        ),
    )

    // ------------------------------------------------------------------ GAS-01

    private val GAS = ModuleContent(
        id = "GAS-01", asset = "gas-01.json", icon = "cloud", minutes = 11,
        title = T("Gas Leak & Confined Space", "गैस रिसाव और संवृत स्थान"),
        beats = listOf(
            BeatContent(
                "2.1", Hazard.GAS_LAYERS,
                T("Gas has a shape", "गैस का एक आकार होता है"),
                T("Methane is lighter than air and collects at the roof and in cavities. Carbon dioxide is heavier and pools on the floor of the sump. Test where gas collects, not where it is convenient.",
                  "मीथेन हवा से हल्की होती है और छत व खोखलों में जमा होती है। कार्बन डाइऑक्साइड भारी होती है और गड्ढे के तल पर जमा होती है। वहाँ जाँचें जहाँ गैस जमा होती है, न कि जहाँ आसान हो।"),
                listOf(
                    ChoiceTask(
                        "zone-id", T("Where will you test for gas? Pick every place that applies.", "गैस की जाँच कहाँ करेंगे? सभी सही जगहें चुनें।"),
                        listOf(
                            Opt("roof_cavity", "arrow_upward", T("Roof cavity — methane rises", "छत का खोखला — मीथेन ऊपर जाती है")),
                            Opt("sump_floor", "arrow_downward", T("Sump floor — CO₂ sinks", "गड्ढे का तल — CO₂ नीचे बैठती है")),
                            Opt("mid_gallery", "horizontal_rule", T("Middle of the gallery at waist height", "गैलरी के बीच, कमर की ऊँचाई पर")),
                        ),
                        multi = true,
                    ),
                    SweepTask(
                        "sample-heights", T("Move the detector from floor to roof — drag up through every height", "डिटेक्टर को फ़र्श से छत तक ले जाएँ — हर ऊँचाई से ऊपर खींचें"),
                        vertical = true,
                    ),
                ),
            ),
            BeatContent(
                "2.2", Hazard.GAS_DETECTOR,
                T("Read the detector against the standing orders", "डिटेक्टर को स्थायी आदेशों से मिलाएँ"),
                T("Your detector reads methane at 1.6 percent. The site withdrawal limit is 1.25 percent. The decision belongs to the approved limit, not to your judgement.",
                  "आपका डिटेक्टर मीथेन 1.6 प्रतिशत दिखा रहा है। साइट की निकासी सीमा 1.25 प्रतिशत है। फ़ैसला स्वीकृत सीमा से होगा, आपके अंदाज़े से नहीं।"),
                listOf(
                    ChoiceTask(
                        "withdraw-decision", T("CH₄ is 1.6%. Limit is 1.25%. What do you do?", "CH₄ 1.6% है। सीमा 1.25% है। आप क्या करेंगे?"),
                        listOf(
                            Opt("withdraw", "directions_run", T("Withdraw everyone and report", "सबको बाहर निकालें और रिपोर्ट करें")),
                            Opt("proceed", "construction", T("Carry on carefully", "सावधानी से काम जारी रखें")),
                            Opt("ventilate_and_proceed", "air", T("Switch on the fan and keep working", "पंखा चालू करके काम करते रहें")),
                        ),
                    ),
                    FlagTask(
                        "retest-after-ventilation", T("Ventilation has run. Before anyone goes back in…", "वेंटिलेशन चल चुका है। किसी के वापस जाने से पहले…"),
                        yes = T("Re-test the atmosphere", "हवा की दोबारा जाँच करें"), no = T("It should be fine now", "अब ठीक होगा"),
                    ),
                ),
            ),
            BeatContent(
                "2.3", Hazard.PPE_RACK,
                T("Kit up from the rack", "रैक से सुरक्षा उपकरण लें"),
                T("One item on this rack will not protect you from gas. Choose the right kit, check the self-rescuer seal, and put it on in the right order.",
                  "इस रैक पर एक चीज़ गैस से आपकी रक्षा नहीं करेगी। सही उपकरण चुनें, सेल्फ-रेस्क्यूअर की सील जाँचें, और सही क्रम में पहनें।"),
                listOf(
                    ChoiceTask(
                        "ppe-set", T("Pick everything you need to enter", "अंदर जाने के लिए ज़रूरी सब कुछ चुनें"),
                        listOf(
                            Opt("scsr", "health_and_safety", T("Self-rescuer (SCSR)", "सेल्फ-रेस्क्यूअर (SCSR)")),
                            Opt("four_gas_detector", "sensors", T("Four-gas detector", "चार-गैस डिटेक्टर")),
                            Opt("harness_lifeline", "link", T("Harness and lifeline", "हार्नेस और लाइफ़लाइन")),
                            Opt("is_cap_lamp", "flashlight_on", T("Intrinsically safe cap lamp", "सुरक्षित कैप लैंप")),
                            Opt("dust_mask", "masks", T("Dust mask", "धूल मास्क")),
                        ),
                        multi = true,
                        hardFailOn = mapOf("dust_mask" to "DUST_MASK_AS_RESPIRATOR"),
                    ),
                    FlagTask(
                        "scsr-seal-check", T("Check the self-rescuer's seal and indicator?", "सेल्फ-रेस्क्यूअर की सील और संकेतक जाँचेंगे?"),
                        yes = T("Check the seal", "सील जाँचें"), no = T("Skip", "छोड़ें"),
                    ),
                    SequenceTask(
                        "donning-order", T("Put your kit on in order", "उपकरण क्रम से पहनें"),
                        listOf(
                            Opt("is_cap_lamp", "flashlight_on", T("Cap lamp", "कैप लैंप")),
                            Opt("four_gas_detector", "sensors", T("Gas detector", "गैस डिटेक्टर")),
                            Opt("scsr", "health_and_safety", T("Self-rescuer", "सेल्फ-रेस्क्यूअर")),
                            Opt("harness_lifeline", "link", T("Harness and lifeline", "हार्नेस और लाइफ़लाइन")),
                        ),
                    ),
                ),
            ),
            BeatContent(
                "2.4", Hazard.PERMIT_BOARD,
                T("The permit and the standby person", "परमिट और स्टैंडबाय व्यक्ति"),
                T("No entry without a tested atmosphere, running ventilation, a standby person posted outside, agreed communication, and a named rescue plan.",
                  "जाँची हुई हवा, चालू वेंटिलेशन, बाहर तैनात स्टैंडबाय व्यक्ति, तय संचार, और तय बचाव योजना के बिना प्रवेश नहीं।"),
                listOf(
                    SequenceTask(
                        "permit-order", T("Complete the entry permit in order", "प्रवेश परमिट क्रम से पूरा करें"),
                        listOf(
                            Opt("test_atmosphere", "sensors", T("Test the atmosphere", "हवा की जाँच करें")),
                            Opt("start_ventilation", "air", T("Start ventilation", "वेंटिलेशन चालू करें")),
                            Opt("post_standby", "accessibility", T("Post a standby person", "स्टैंडबाय व्यक्ति तैनात करें")),
                            Opt("agree_comms", "radio", T("Agree communication signals", "संचार संकेत तय करें")),
                            Opt("name_rescue_plan", "sos", T("Name the rescue plan", "बचाव योजना तय करें")),
                        ),
                        mustStartWith = "test_atmosphere" to "ENTRY_BEFORE_ATMOSPHERE_TEST",
                    ),
                    FlagTask(
                        "standby-posted", T("Your standby person has not arrived yet. Do you…", "आपका स्टैंडबाय व्यक्ति अभी नहीं आया। क्या आप…"),
                        yes = T("Wait until they are posted outside", "बाहर तैनात होने तक रुकेंगे"),
                        no = T("Go in alone, it's a quick job", "अकेले अंदर जाएँगे, छोटा काम है"),
                        hardFailIfNo = "SOLO_ENTRY",
                    ),
                    FlagTask(
                        "comms-check", T("Test the radio or rope signals with your standby?", "स्टैंडबाय के साथ रेडियो या रस्सी संकेत जाँचेंगे?"),
                        yes = T("Test comms", "संचार जाँचें"), no = T("Skip", "छोड़ें"),
                    ),
                ),
            ),
            BeatContent(
                "2.5", Hazard.SUMP_RESCUE,
                T("Your buddy collapses inside", "आपका साथी अंदर गिर गया"),
                T("Most people who die in confined spaces are would-be rescuers. You do not go in. Raise the alarm and pull your buddy out with the lifeline from outside.",
                  "संवृत स्थानों में मरने वाले ज़्यादातर लोग बचाने वाले होते हैं। आप अंदर नहीं जाएँगे। अलार्म बजाएँ और लाइफ़लाइन से बाहर से ही साथी को खींचें।"),
                listOf(
                    ChoiceTask(
                        "rescue-choice", T("Your buddy is down inside the sump. What do you do?", "आपका साथी गड्ढे के अंदर गिरा है। आप क्या करेंगे?"),
                        listOf(
                            Opt("non_entry_rescue", "link", T("Raise the alarm and pull them out with the lifeline from outside", "अलार्म बजाएँ और बाहर से लाइफ़लाइन से खींचें")),
                            Opt("enter_to_rescue", "directions_run", T("Climb in straight away to help", "तुरंत अंदर जाकर मदद करें")),
                        ),
                        latencyItem = "alarm-latency",
                        hardFailOn = mapOf("enter_to_rescue" to "UNPROTECTED_RESCUE_ENTRY"),
                    ),
                    SweepTask(
                        "retrieval-use", T("Pull the lifeline hand over hand — drag up steadily", "लाइफ़लाइन हाथ-दर-हाथ खींचें — लगातार ऊपर खींचें"),
                        vertical = true,
                    ),
                    QuizTask(
                        "written-check", T("Six quick questions", "छह छोटे सवाल"),
                        listOf(
                            q("q1", T("Methane is…", "मीथेन…"),
                                T("Lighter than air — it collects at the roof", "हवा से हल्की — छत पर जमा होती है"), T("Heavier than air — it collects on the floor", "हवा से भारी — फ़र्श पर जमा होती है"), T("The same everywhere", "हर जगह एक जैसी")),
                            q("q2", T("A dust mask protects you from…", "धूल मास्क आपको किससे बचाता है?"),
                                T("Methane", "मीथेन"), T("Carbon monoxide", "कार्बन मोनोऑक्साइड"), T("Only dust — not gases", "सिर्फ धूल — गैसों से नहीं")),
                            q("q3", T("If methane reaches the withdrawal limit you…", "मीथेन निकासी सीमा तक पहुँचे तो आप…"),
                                T("Continue carefully", "सावधानी से जारी रखें"), T("Withdraw immediately and report", "तुरंत बाहर निकलें और रिपोर्ट करें"), T("Switch on a fan and keep working", "पंखा चालू कर काम जारी रखें")),
                            q("q4", T("Before entering a confined space…", "संवृत स्थान में जाने से पहले…"),
                                T("Go in alone, quickly", "अकेले, जल्दी जाएँ"), T("Post a standby person outside", "बाहर स्टैंडबाय व्यक्ति तैनात करें"), T("Remove your detector", "डिटेक्टर हटा दें")),
                            q("q5", T("If your buddy collapses inside a sump…", "अगर साथी गड्ढे के अंदर गिर जाए…"),
                                T("Do not enter — rescue from outside", "अंदर न जाएँ — बाहर से बचाएँ"), T("Jump in to help", "मदद के लिए कूद जाएँ"), T("Wait for the shift to end", "शिफ़्ट खत्म होने का इंतज़ार करें")),
                            q("q6", T("A self-rescuer (SCSR) is for…", "सेल्फ-रेस्क्यूअर (SCSR) किसलिए है?"),
                                T("Breathing safely when the air is unbreathable", "जब हवा साँस लेने लायक न हो तब सुरक्षित साँस लेना"), T("Filtering dust", "धूल छानना"), T("Use as a torch", "टॉर्च की तरह")),
                        ),
                    ),
                ),
            ),
        ),
    )
}
