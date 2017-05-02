package com.intellij.sorting

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.CompletionWeigher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.psi.PsiMethod
import com.intellij.stats.completion.RequestService
import com.intellij.stats.completion.ResponseData
import com.intellij.stats.completion.experiment.ExperimentDecision
import com.jetbrains.completion.ranker.features.LookupElementInfo
import org.assertj.core.api.Assertions
import java.io.File


internal fun LookupImpl.checkMlRanking(ranker: Ranker, prefix_length: Int) {
    val items = this.items.toList()
    val lookupElements = getRelevanceObjects(items, false)

    lookupElements.forEach { element, relevance ->
        val weights: Map<String, Any?> = relevance.associate { it.first to it.second }
        val ml_rank = weights["ml_rank"]?.toString()
        if (ml_rank == "UNDEFINED" || weights["before_rerank_order"] == null) {
            throw UnsupportedOperationException("Ranking failed")
        }

        val old_order = weights["before_rerank_order"].toString().toInt()

        val state = LookupElementInfo(old_order, prefix_length, element.lookupString.length)

        val calculated_ml_rank = ranker.rank(state, weights)
        Assertions.assertThat(calculated_ml_rank).isEqualTo(ml_rank?.toDouble())
                .withFailMessage("Calculated: $calculated_ml_rank Regular: ${ml_rank?.toDouble()}")
    }
}

@Suppress("unused")
internal class FakeWeighter : CompletionWeigher() {

    companion object {
        var isReturnNull = false
    }

    override fun weigh(element: LookupElement, location: CompletionLocation): Comparable<Nothing>? {
        if (isReturnNull) return null
        val psiElement = element.psiElement as? PsiMethod ?: return 0
        return psiElement.name.length - psiElement.parameterList.parametersCount
    }

}


internal class TestExperimentDecision: ExperimentDecision {
    companion object {
        var isPerformExperiment = true
    }
    override fun isPerformExperiment(salt: String) = isPerformExperiment
}

internal class DumbRequestService : RequestService() {

    companion object {
        var onAnyRequestReturn: ResponseData? = null
    }

    override fun post(url: String, params: Map<String, String>) = onAnyRequestReturn
    override fun post(url: String, file: File) = onAnyRequestReturn
    override fun postZipped(url: String, file: File) = onAnyRequestReturn
    override fun get(url: String) = onAnyRequestReturn

}


internal object Samples {

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