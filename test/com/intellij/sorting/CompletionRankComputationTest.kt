package com.intellij.sorting

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.stats.completion.sorting.Ranker
import com.jetbrains.completion.ranker.features.CompletionState
import junit.framework.TestCase


class CompletionRankComputationTest : LightFixtureCompletionTestCase() {

    lateinit var ranker: Ranker
    
    override fun setUp() {
        super.setUp()
        ranker = Ranker.getInstance()
    }

    fun `test shit`() {
        myFixture.addClass("public interface Foo {}")
        myFixture.addClass("public interface Fowo {}")
        myFixture.addClass("package com; public interface Foao {}")
        myFixture.addClass("package com.intellij; public interface Fobo {}")
        
        
        val text = """
class Test {
  public void run() {
    F<caret>
  }
}
"""
        myFixture.configureByText(JavaFileType.INSTANCE, text)
        myFixture.complete(CompletionType.BASIC, 2)

        
        val lookup = myFixture.lookup as LookupImpl
        val items = myFixture.lookupElements!!.toList()
        val lookupElements = lookup.getRelevanceObjects(items, false)
        
        lookupElements.forEach { element, relevance ->
            val weights = relevance.associate { it.first to it.second }
            val old_order = weights["before_rerank_order"].toString().toInt()
            
            val state = CompletionState(old_order, 1, 0, element.lookupString.length)    
            val calculated_ml_rank = ranker.rank(state, weights)
            
            val ml_rank_from_weights = weights["ml_rank"].toString().toDouble()

            TestCase.assertTrue(calculated_ml_rank == ml_rank_from_weights)
        }
    }
    
}