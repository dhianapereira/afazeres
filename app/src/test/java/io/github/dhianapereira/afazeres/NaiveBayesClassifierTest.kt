package io.github.dhianapereira.afazeres

import io.github.dhianapereira.afazeres.model.nlp.NaiveBayesClassifier
import io.github.dhianapereira.afazeres.model.nlp.NaiveBayesClassifier.Example
import io.github.dhianapereira.afazeres.model.nlp.TextTokenizer
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class NaiveBayesClassifierTest {
    private val examples = List(4) { Example("Revisar contrato cliente", "work") } + List(4) { Example("Comprar frutas mercado", "personal") }
    @Test fun abstainsWithoutEnoughManualHistory() {
        assertNull(NaiveBayesClassifier(emptyList()).predict("contrato cliente"))
        assertNull(NaiveBayesClassifier(examples.take(7)).predict("contrato cliente"))
    }
    @Test fun abstainsWithOnlyOneClass() {
        assertNull(NaiveBayesClassifier(List(20) { examples.first() }).predict("contrato cliente"))
    }
    @Test fun learnsDifferentVocabularyForEachLabel() {
        val model = NaiveBayesClassifier(examples)
        assertEquals("work", model.predict("Revisar contrato do cliente")?.label)
        assertEquals("personal", model.predict("Comprar frutas no mercado")?.label)
    }
    @Test fun abstainsOnUnknownOrMostlyUnknownText() {
        val model = NaiveBayesClassifier(examples)
        assertNull(model.predict("Agendar consulta dentista"))
        assertNull(model.predict("cliente consulta dentista horario"))
        assertNull(model.predict("123 !"))
    }
    @Test fun abstainsOnConflictingEvidence() {
        assertNull(NaiveBayesClassifier(examples).predict("contrato cliente frutas mercado"))
    }
    @Test fun classImbalanceAloneIsNotEnough() {
        val model = NaiveBayesClassifier(List(100) { Example("revisar tarefa", "a") } + List(4) { Example("revisar tarefa", "b") })
        assertNull(model.predict("revisar tarefa"))
    }
    @Test fun tokenizesAccentsCasePunctuationAndKeepsNegation() {
        assertEquals(mapOf("revisar" to 1, "orcamento" to 1, "nao" to 1, "urgente" to 1), TextTokenizer.counts("REVISAR o orçamento: não urgente!"))
        assertTrue("not" in TextTokenizer.counts("Not urgent"))
        assertTrue("no" in TextTokenizer.counts("No urgency"))
    }
    @Test fun tokenizationDoesNotDependOnDeviceLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            assertEquals(mapOf("invoice" to 1), TextTokenizer.counts("INVOICE"))
        } finally { Locale.setDefault(original) }
    }
    @Test fun boundsRepeatedTermsAndLongInputs() {
        assertEquals(3, TextTokenizer.counts("urgent ".repeat(1000))["urgent"])
    }
    @Test fun trainingOrderDoesNotAffectPrediction() {
        assertEquals(NaiveBayesClassifier(examples).predict("contrato cliente"), NaiveBayesClassifier(examples.reversed()).predict("contrato cliente"))
    }
}
