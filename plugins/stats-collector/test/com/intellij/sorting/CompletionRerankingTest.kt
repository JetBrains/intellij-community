package com.intellij.sorting

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.testFramework.registerServiceInstance
import com.jetbrains.completion.ranker.features.LookupElementInfo
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

    private lateinit var fakeRanker: FakeRanker
    private lateinit var realRanker: Ranker
    
    override fun setUp() {
        super.setUp()

        realRanker = ServiceManager.getService(Ranker::class.java)
        fakeRanker = FakeRanker()
        ApplicationManager.getApplication().registerServiceInstance(Ranker::class.java, fakeRanker)
        
        myFixture.addClass(testInterface)
        myFixture.configureByText(JavaFileType.INSTANCE, text)
    }

    override fun tearDown() {
        ApplicationManager.getApplication().registerServiceInstance(Ranker::class.java, realRanker)
        super.tearDown()
    }

    fun `test ensure items sorted on first time and resorted on each typing`() {
        fakeRanker.isShortFirst = true
        myFixture.completeBasic()
        assertThat(myFixture.lookupElementStrings).isEqualTo(listOf(
                "check",
                "checkMe",
                "checkYou",
                "checkWhatever"
        ))
        
        fakeRanker.isShortFirst = false
        myFixture.type('c')
        assertThat(myFixture.lookupElementStrings).isEqualTo(listOf(
                "checkWhatever",
                "checkYou",
                "checkMe",
                "check"
        ))
    }

}


class FakeRanker: Ranker {
    
    var isShortFirst = true

    /**
     * Items are sorted by descending order, so item with the highest rank will be on top
     */
    override fun rank(state: LookupElementInfo, relevance: Map<String, Any?>): Double? {
        val lookupElementLength = state.result_length!!.toDouble()
        return if (isShortFirst) -lookupElementLength else lookupElementLength
    }
    
}