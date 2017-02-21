package com.intellij.sorting

import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.codeInsight.lookup.Classifier
import com.intellij.codeInsight.lookup.ClassifierFactory
import com.intellij.codeInsight.lookup.LookupArranger
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.util.Pair
import com.intellij.stats.completion.sorting.MLCompletionContributor
import com.intellij.util.ProcessingContext
import org.assertj.core.api.Assertions.assertThat


private val testInterface = """
interface Whatever {
    void checkMe() {}
    void check() {}
    void checkYou() {}
    void checkWhatever() {}
}
"""
    

private val text = """
class Test {
    public void run() {
        Whatever w;
        w.<caret>
    }
}
"""


class CompletionRerankingTest : LightFixtureCompletionTestCase() {

    private var pointIndex = 0
    
    override fun setUp() {
        super.setUp()

        val point = getExtensionPoint<CompletionContributorEP>("com.intellij.completion.contributor")
        val extensions = point.extensions
        
        val originalClass = MLCompletionContributor::class.java.canonicalName
        pointIndex = extensions.indexOfFirst { it.implementationClass == originalClass }
        extensions[pointIndex].implementationClass = TestCompletionContributor::class.java.canonicalName

        myFixture.addClass(testInterface)
        myFixture.configureByText(JavaFileType.INSTANCE, text)
    }

    override fun tearDown() {
        val point = getExtensionPoint<CompletionContributorEP>("com.intellij.completion.contributor")
        val contributorEP = point.extensions[pointIndex]
        contributorEP.implementationClass = MLCompletionContributor::class.java.canonicalName
        
        super.tearDown()
    }

    fun `test ensure items sorted on first time and resorted on each typing`() {
        ByLengthClassifier.isShortFirst = true
        myFixture.completeBasic()
        assertThat(myFixture.lookupElementStrings).isEqualTo(listOf(
                "check",
                "checkMe",
                "checkYou",
                "checkWhatever"
        ))
        
        ByLengthClassifier.isShortFirst = false
        myFixture.type('c')
        assertThat(myFixture.lookupElementStrings).isEqualTo(listOf(
                "checkWhatever",
                "checkYou",
                "checkMe",
                "check"
        ))
    }

}


class TestCompletionContributor : MLCompletionContributor() {
    
    override fun newClassifierFactory(lookupArranger: LookupArranger, lookup: LookupImpl): ClassifierFactory<LookupElement> {
        return object : ClassifierFactory<LookupElement>("fake_ml_classifier") {
            override fun createClassifier(next: Classifier<LookupElement>): Classifier<LookupElement> {
                return ByLengthClassifier(next)
            }
        }
    }
    
}


class ByLengthClassifier(next: Classifier<LookupElement>) : Classifier<LookupElement>(next, "test_classifier") {
    
    companion object {
        var isShortFirst = true
    }
    
    override fun classify(items: MutableIterable<LookupElement>, context: ProcessingContext): MutableIterable<LookupElement> {
        return items.sortedBy {
            val length = it.lookupString.length
            if (isShortFirst) length else -length
        }.toMutableList()
    }
    
    override fun getSortingWeights(items: MutableIterable<LookupElement>, context: ProcessingContext): MutableList<Pair<LookupElement, Any>> {
        return items.map {
            val length = it.lookupString.length
            Pair.create(it, (if (isShortFirst) length else -length) as Any) 
        }.toMutableList()
    }
    
}

fun <T> getExtensionPoint(name: String) = Extensions.getRootArea().getExtensionPoint<T>(name)