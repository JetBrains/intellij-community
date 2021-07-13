// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.test.env.kotlin

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.UastVisitor
import kotlin.test.fail

abstract class AbstractUastTest : AbstractTestWithCoreEnvironment() {
    abstract fun getVirtualFile(testName: String): VirtualFile
    abstract fun check(testName: String, file: UFile)

    fun doTest(testName: String, checkCallback: (String, UFile) -> Unit = { testName, file -> check(testName, file) }) {
        val virtualFile = getVirtualFile(testName)

        val psiFile = psiManager.findFile(virtualFile) ?: error("Can't get psi file for $testName")
        val uFile = UastFacade.convertElementWithParent(psiFile, null) ?: error("Can't get UFile for $testName")
        checkCallback(testName, uFile as UFile)
    }
}

fun <T> UElement.findElementByText(refText: String, cls: Class<T>): T {
    val matchingElements = mutableListOf<T>()
    accept(object : UastVisitor {
        override fun visitElement(node: UElement): Boolean {
            if (cls.isInstance(node) && node.psi?.text == refText) {
                matchingElements.add(node as T)
            }
            return false
        }
    })

    if (matchingElements.isEmpty()) {
        throw IllegalArgumentException("Reference '$refText' not found")
    }
    if (matchingElements.size != 1) {
        throw IllegalArgumentException("Reference '$refText' is ambiguous")
    }
    return matchingElements.single()
}

inline fun <reified T : Any> UElement.findElementByText(refText: String): T = findElementByText(refText, T::class.java)

inline fun <reified T : UElement> UElement.findElementByTextFromPsi(refText: String, strict: Boolean = false): T =
    (this.psi ?: fail("no psi for $this")).findUElementByTextFromPsi(refText, strict)

inline fun <reified T : UElement> PsiElement.findUElementByTextFromPsi(refText: String, strict: Boolean = false): T {
    val elementAtStart = this.findElementAt(this.text.indexOf(refText))
            ?: throw AssertionError("requested text '$refText' was not found in $this")

    val targetElements = elementAtStart.parentsWithSelf
    var uElementContainingText: T? = null

    for (targetElement in targetElements) {
        if (strict && !targetElement.text.contains(refText)) {
            continue
        }

        val foundElement = targetElement.toUElementOfType<T>()
        if (foundElement != null) {
            uElementContainingText = foundElement
            break
        }
    }

    if (uElementContainingText == null) {
        throw AssertionError("requested text '$refText' not found as '${T::class.java.canonicalName}' in $this")
    }

    if (strict && uElementContainingText.psi != null && uElementContainingText.psi?.text != refText) {
        throw AssertionError("requested text '$refText' found as '${uElementContainingText.psi?.text}' in $uElementContainingText")
    }
    return uElementContainingText
}
