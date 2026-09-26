package `in`.sajag.i18n

import `in`.sajag.assess.ChoiceTask
import `in`.sajag.assess.FlagTask
import `in`.sajag.assess.Modules
import `in`.sajag.assess.QuizTask
import `in`.sajag.assess.SequenceTask
import `in`.sajag.geo.Jharkhand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Choosing Santali must show Santali everywhere, never a silent fall back to Hindi. */
class SantaliCoverageTest {
    private fun isOlChiki(c: Char) = c in '᱐'..'᱿'

    private fun allAppText(): List<T> {
        val out = mutableListOf<T>()
        for (f in S::class.java.declaredFields) {
            f.isAccessible = true
            (f.get(S) as? T)?.let(out::add)
        }
        for (card in Modules.cards) out += card.title
        for (e in Modules.EMERGENCIES) { out += e.title; out += e.steps }
        for (id in listOf("FIRE-01", "GAS-01")) {
            val m = Modules.content(id)
            out += m.title
            for (beat in m.beats) {
                out += beat.title
                out += beat.teach
                for (task in beat.tasks) {
                    out += task.prompt
                    when (task) {
                        is ChoiceTask -> out += task.options.map { it.label }
                        is SequenceTask -> out += task.steps.map { it.label }
                        is FlagTask -> { out += task.yes; out += task.no }
                        is QuizTask -> for (q in task.questions) { out += q.prompt; out += q.options.map { it.label } }
                        else -> {}
                    }
                }
            }
        }
        for (d in Jharkhand.districts) out += d.name
        return out
    }

    @Test
    fun everyLineHasSantali() {
        val text = allAppText()
        assertTrue("found too few lines to be checking the real app", text.size > 300)
        val missing = text.filter { it.santali == null }.map { it.en }
        assertEquals("no Santali for: $missing", emptyList<String>(), missing)
    }

    @Test
    fun santaliIsWrittenInOlChiki() {
        val notOlChiki = allAppText().mapNotNull { it.santali }
            .filter { s -> s.any { it.isLetter() } && s.none(::isOlChiki) && s.any { it.isLowerCase() } }
        assertEquals("not in Ol Chiki: $notOlChiki", emptyList<String>(), notOlChiki)
    }

    @Test
    fun santaliDoesNotFallBackToHindi() {
        assertEquals(SantaliText.lines["Back"], S.back.of(Lang.SAT))
        assertTrue(S.stopTitle.of(Lang.SAT).any(::isOlChiki))
    }
}
