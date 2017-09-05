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


