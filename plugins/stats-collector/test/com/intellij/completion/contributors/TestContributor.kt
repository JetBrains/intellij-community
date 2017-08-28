package com.intellij.completion.contributors

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder

class TestContributor : CompletionContributor() {

    companion object {
        var isEnabled = false
    }

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        if (!isEnabled) return

        val type = parameters.completionType
        val prefix = "EC_$type"

        val invocationCount = parameters.invocationCount
        if (invocationCount >= 0) {
            result.consume(LookupElementBuilder.create("${prefix}_COUNT_0"))
        }
        if (invocationCount >= 1) {
            result.consume(LookupElementBuilder.create("${prefix}_COUNT_1"))
        }
        if (invocationCount >= 2) {
            result.consume(LookupElementBuilder.create("${prefix}_COUNT_2"))
        }
        if (invocationCount >= 3) {
            result.consume(LookupElementBuilder.create("${prefix}_COUNT_3"))
        }
        if (invocationCount >= 4) {
            result.consume(LookupElementBuilder.create("${prefix}_COUNT_4"))
        }
    }

}
