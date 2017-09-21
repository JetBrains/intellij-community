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

package com.intellij.completion.enhancer

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.AutoCompletionPolicy
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.util.ClassConditionKey
import com.intellij.psi.PsiElement

class UnmatchableLookupElement(
        private val original: LookupElement
): LookupElement() {

    override fun getObject(): Any {
        return original.`object`
    }

    override fun handleInsert(context: InsertionContext?) {
        original.handleInsert(context)
    }

    override fun <T : Any?> `as`(conditionKey: ClassConditionKey<T>?): T? {
        return original.`as`(conditionKey)
    }

    override fun renderElement(presentation: LookupElementPresentation?) {
        original.renderElement(presentation)
    }

    override fun isWorthShowingInAutoPopup(): Boolean {
        return original.isWorthShowingInAutoPopup
    }

    override fun isCaseSensitive(): Boolean {
        return original.isCaseSensitive
    }

    override fun isValid(): Boolean {
        return original.isValid
    }

    override fun getLookupString(): String {
        return original.lookupString
    }

    override fun toString(): String {
        return original.toString()
    }

    override fun getAllLookupStrings(): Set<String> {
        val original = original.allLookupStrings
        return original.map { "$it#" }.toSet()
    }

    override fun getAutoCompletionPolicy(): AutoCompletionPolicy {
        return original.autoCompletionPolicy
    }

    override fun getPsiElement(): PsiElement? {
        return original.psiElement
    }
}