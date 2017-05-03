package com.intellij.mocks

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.CompletionWeigher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiMethod
import com.intellij.sorting.Ranker
import com.jetbrains.completion.ranker.features.LookupElementInfo


internal class FakeRanker: Ranker {

    var isShortFirst = true

    /**
     * Items are sorted by descending order, so item with the highest rank will be on top
     */
    override fun rank(state: LookupElementInfo, relevance: Map<String, Any?>): Double? {
        val lookupElementLength = state.result_length!!.toDouble()
        return if (isShortFirst) -lookupElementLength else lookupElementLength
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


