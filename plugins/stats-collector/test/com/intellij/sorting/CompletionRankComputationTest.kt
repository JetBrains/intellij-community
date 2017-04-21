package com.intellij.sorting

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.CompletionWeigher
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.psi.PsiMethod
import com.intellij.psi.WeigherExtensionPoint
import com.jetbrains.completion.ranker.features.CompletionState
import com.jetbrains.completion.ranker.features.FeatureUtils
import junit.framework.TestCase
import org.assertj.core.api.Assertions.assertThat


class CompletionRankComputationTest : LightFixtureCompletionTestCase() {

    lateinit var ranker: Ranker
    
    override fun setUp() {
        super.setUp()
        ranker = Ranker.getInstance()
    }

    fun `test class name completion ranking`() {
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

        checkMlRanking(1)
    }

    fun `test normal completion`() {
        val classText = """
public class Test {
    public void test() {}
    public void run() {}
    public void testMore() {}
    public void check() {}
}
"""
        myFixture.addClass(classText)

        val text = """
class X {
    public void t() {
        Test test = new Test();
        test.<caret>
    }
}
"""
        myFixture.configureByText(JavaFileType.INSTANCE, text)
        myFixture.completeBasic()
        
        checkMlRanking(0)
        
        myFixture.type('t')
        checkMlRanking(1)
        
        myFixture.type('e')
        checkMlRanking(2)
        
        myFixture.type('s')
        checkMlRanking(3)
    }


    fun `test do not rerank if encountered unknown features`() {
        myFixture.addClass("""
public class Test {
    public void test(int a, int b) {}
    public void runq(int c) {}
    public void qqqq() {}
    public void qwrt(int a, int b, int c) {}
}
""")

        val fakeWeigherExt = WeigherExtensionPoint().apply { 
            id = "fake"
            key = "completion"
            implementationClass = "com.intellij.sorting.FakeWeighter"
        }
        
        val name = ExtensionPointName<WeigherExtensionPoint>("com.intellij.weigher")
        val point = Extensions.getRootArea().getExtensionPoint(name)
        point.registerExtension(fakeWeigherExt, LoadingOrder.before("templates"))
        
        try {
            myFixture.configureByText(JavaFileType.INSTANCE, """
class X {
    public void t() {
        Test test = new Test();
        test.<caret>
    }
}
""")
            checkNormalCompletionOrder()
        }
        finally {
            point.unregisterExtension(fakeWeigherExt)
        }
    }

    private fun checkNormalCompletionOrder() {
        myFixture.completeBasic()
        val lookup = myFixture.lookup as LookupImpl
        val objects = lookup.getRelevanceObjects(lookup.items, false)
        val ranks = objects.map { it.value.find { it.first == FeatureUtils.ML_RANK }!!.second }.toSet()

        assert(ranks.size == 1)
        assert(ranks.first() == FeatureUtils.UNDEFINED)

        val items = lookup.items.map { it.lookupString }
        assertThat(items).isEqualTo(listOf("qqqq", "runq", "test", "qwrt"))
    }


    private fun checkMlRanking(prefixLength: Int) {
        val lookup = myFixture.lookup as LookupImpl
        assertThat(lookup.items.size > 0)

        val items = myFixture.lookupElements!!.toList()
        val lookupElements = lookup.getRelevanceObjects(items, false)

        lookupElements.forEach { element, relevance ->
            val weights = relevance.associate { it.first to it.second }
            val ml_rank = weights["ml_rank"]?.toString()
            if (ml_rank == "UNDEFINED") {
                throw UnsupportedOperationException("Ranking failed")
            }
            
            val old_order = weights["before_rerank_order"].toString().toInt()

            val state = CompletionState(old_order, prefixLength, 0, element.lookupString.length)
            
            //todo check this shit
            val calculated_ml_rank = ranker.rank(state, relevance)
            
            TestCase.assertTrue(
                    "Calculated: $calculated_ml_rank Regular: ${ml_rank?.toDouble()}", 
                    calculated_ml_rank == ml_rank?.toDouble())
        }
    }
}


@Suppress("unused")
class FakeWeighter : CompletionWeigher() {

    override fun weigh(element: LookupElement, location: CompletionLocation): Comparable<Nothing> {
        val psiElement = element.psiElement as? PsiMethod ?: return 0
        return psiElement.name.length - psiElement.parameterList.parametersCount
    }

}

