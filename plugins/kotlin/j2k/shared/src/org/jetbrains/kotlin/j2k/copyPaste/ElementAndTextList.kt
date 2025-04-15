// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.copyPaste

import com.intellij.psi.PsiElement

/**
 * A list of `PsiElement`s to convert with J2K, intermixed with plain text strings,
 * that will be inserted into the J2K result as is.
 */
class ElementAndTextList() {
    private val elementsAndTexts: MutableList<Any> = mutableListOf()

    constructor(items: List<Any>) : this() {
        val filteredItems = items.filter {
            it is PsiElement || (it is String && it.isNotEmpty())
        }
        elementsAndTexts.addAll(filteredItems)
    }

    fun addText(text: String) {
        if (text.isNotEmpty()) elementsAndTexts.add(text)
    }

    fun addElement(element: PsiElement) {
        elementsAndTexts.add(element)
    }

    fun addElements(elements: Collection<PsiElement>) {
        elementsAndTexts.addAll(elements)
    }

    fun toList(): List<Any> = elementsAndTexts.toList()

    fun process(processor: ElementsAndTextsProcessor) {
        for (item in elementsAndTexts) {
            when (item) {
                is PsiElement -> processor.processElement(item)
                is String -> processor.processText(item)
            }
        }
    }
}

interface ElementsAndTextsProcessor {
    fun processElement(element: PsiElement)
    fun processText(text: String)
}
