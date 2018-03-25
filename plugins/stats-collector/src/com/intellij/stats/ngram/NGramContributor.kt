package com.intellij.stats.ngram

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResult
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope

class NGramContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        result.runRemainingContributors(parameters, { completionResult ->
            val wrapped = CompletionResult.wrap(completionResult.lookupElement,
                    completionResult.prefixMatcher,
                    completionResult.sorter.weigh(NaiveNGramWeigher(parameters)))
            if (wrapped != null) {
                result.passResult(wrapped)
            }
        })
    }
}

abstract class NGramWeigher(parameters: CompletionParameters, name : String) : LookupElementWeigher(name) {
    val nGram: NGram
    val project: Project

    init {
        val originalPosition = parameters.position
        nGram = NGram.getNGramForElement(originalPosition)
        project = originalPosition.project
    }

}


private class NaiveNGramWeigher(parameters: CompletionParameters) : NGramWeigher(parameters,"naive ngram weigher") {

    override fun weigh(element: LookupElement): Comparable<*> {
        if (nGram == NGram.INVALID) {
            return 0.0
        }
        var currentNGram = nGram.append(element.lookupString)
        var sum = 0.0
        for (j in 0..NGram.N) {
            val coefficient = 1 shl j
            val rate = NGramFileBasedIndex.getNumberOfOccurrences(currentNGram, GlobalSearchScope.allScope(project))
            sum += rate / (coefficient + 0.0)
            currentNGram = currentNGram.dropHead()
        }
        return sum // put your score evaluation here
    }
}