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
import com.intellij.openapi.util.Pair
import com.intellij.psi.PsiMethod
import com.intellij.psi.WeigherExtensionPoint
import com.intellij.sorting.Samples.callCompletionOnClass
import com.intellij.sorting.Samples.classNameCompletion
import com.intellij.sorting.Samples.classText
import com.intellij.sorting.Samples.methodCompletion
import com.jetbrains.completion.ranker.features.LookupElementInfo
import com.jetbrains.completion.ranker.features.FeatureUtils
import org.assertj.core.api.Assertions.assertThat


class CompletionRankComputationTest : LightFixtureCompletionTestCase() {

    lateinit var ranker: Ranker

    override fun setUp() {
        super.setUp()
        ranker = Ranker.getInstance()
    }

    fun `test class name completion reranking`() {
        myFixture.addClass("public interface Foo {}")
        myFixture.addClass("public interface Fowo {}")
        myFixture.addClass("package com; public interface Foao {}")
        myFixture.addClass("package com.intellij; public interface Fobo {}")

        myFixture.configureByText(JavaFileType.INSTANCE, classNameCompletion)
        myFixture.complete(CompletionType.BASIC, 2)

        checkMlRanking(prefixLength = 1)
    }

    fun `test normal completion reranking`() {
        myFixture.addClass(classText)

        myFixture.configureByText(JavaFileType.INSTANCE, methodCompletion)
        myFixture.completeBasic()
        
        checkMlRanking(prefixLength = 0)
        
        myFixture.type('t')
        checkMlRanking(1)
        
        myFixture.type('e')
        checkMlRanking(prefixLength = 2)
        
        myFixture.type('s')
        checkMlRanking(prefixLength = 3)
    }


    fun `test do not rerank if encountered unknown features`() {
        myFixture.addClass(callCompletionOnClass)

        val fakeWeigherExt = fakeWeigher()
        
        val name = ExtensionPointName<WeigherExtensionPoint>("com.intellij.weigher")
        val point = Extensions.getRootArea().getExtensionPoint(name)
        point.registerExtension(fakeWeigherExt, LoadingOrder.before("templates"))
        
        try {
            myFixture.configureByText(JavaFileType.INSTANCE, methodCompletion)
            assertNormalCompletionSortingOrder()
        }
        finally {
            point.unregisterExtension(fakeWeigherExt)
        }
    }


    fun `test features with null values are ignored even if unknown`() {
        myFixture.addClass(callCompletionOnClass)

        val nullFakeWeigher = fakeWeigher()
        FakeWeighter.isReturnNull = true
        val name = ExtensionPointName<WeigherExtensionPoint>("com.intellij.weigher")
        val point = Extensions.getRootArea().getExtensionPoint(name)
        point.registerExtension(nullFakeWeigher, LoadingOrder.before("templates"))


        try {
            myFixture.configureByText(JavaFileType.INSTANCE, methodCompletion)
            myFixture.completeBasic()
            checkMlRanking(0)
        }
        finally {
            FakeWeighter.isReturnNull = false
            point.unregisterExtension(nullFakeWeigher)
        }
    }

    private fun fakeWeigher() = WeigherExtensionPoint().apply {
        id = "fake"
        key = "completion"
        implementationClass = "com.intellij.sorting.FakeWeighter"
    }


    private fun assertNormalCompletionSortingOrder() {
        myFixture.completeBasic()
        val lookup = myFixture.lookup as LookupImpl
        val objects: Map<LookupElement, List<Pair<String, Any>>> = lookup.getRelevanceObjects(lookup.items, false)
        val ranks = objects
                .mapNotNull { it.value.find { it.first == FeatureUtils.ML_RANK } }
                .map { it.second }
                .toSet()

        assertThat(ranks.size).withFailMessage("Ranks size: ${ranks.size} expected: 1\nRanks $ranks").isEqualTo(1)
        assertThat(ranks.first()).isEqualTo(FeatureUtils.UNDEFINED)

        val items = lookup.items.map { it.lookupString }
        assertThat(items).isEqualTo(listOf("qqqq", "runq", "test", "qwrt"))
    }


    private fun checkMlRanking(prefixLength: Int) {
        val lookup = myFixture.lookup as LookupImpl
        assertThat(lookup.items.size > 0)

        val items = myFixture.lookupElements!!.toList()
        val lookupElements = lookup.getRelevanceObjects(items, false)

        lookupElements.forEach { element, relevance ->
            val weights: Map<String, Any?> = relevance.associate { it.first to it.second }
            val ml_rank = weights["ml_rank"]?.toString()
            if (ml_rank == "UNDEFINED") {
                throw UnsupportedOperationException("Ranking failed")
            }
            
            val old_order = weights["before_rerank_order"].toString().toInt()

            val state = LookupElementInfo(old_order, prefixLength, element.lookupString.length)
            
            val calculated_ml_rank = ranker.rank(state, weights)
            assertThat(calculated_ml_rank).isEqualTo(ml_rank?.toDouble())
                    .withFailMessage("Calculated: $calculated_ml_rank Regular: ${ml_rank?.toDouble()}")
        }
    }
}


@Suppress("unused")
class FakeWeighter : CompletionWeigher() {

    companion object {
        var isReturnNull = false
    }

    override fun weigh(element: LookupElement, location: CompletionLocation): Comparable<Nothing>? {
        if (isReturnNull) return null
        val psiElement = element.psiElement as? PsiMethod ?: return 0
        return psiElement.name.length - psiElement.parameterList.parametersCount
    }

}


object Samples {

    val callCompletionOnClass = """
    public class Test {
        public void test(int a, int b) {}
        public void runq(int c) {}
        public void qqqq() {}
        public void qwrt(int a, int b, int c) {}
    }
    """

    val methodCompletion = """
    class X {
        public void t() {
            Test test = new Test();
            test.<caret>
        }
    }
    """

    val classNameCompletion = """
    class Test {
      public void run() {
        F<caret>
      }
    }
    """

    val classText = """
public class Test {
    public void test() {}
    public void run() {}
    public void testMore() {}
    public void check() {}
}
"""


}
