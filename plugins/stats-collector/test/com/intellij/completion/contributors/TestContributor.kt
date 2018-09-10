/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
