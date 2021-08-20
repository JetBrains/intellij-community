// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.test.env.kotlin

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.uast.*
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

inline fun <reified T : UElement> UElement.findElementByTextFromPsi(refText: String, strict: Boolean = false): T =
    (this.psi ?: fail("no psi for $this")).findUElementByTextFromPsi(refText, strict)

inline fun <reified T : UElement> PsiElement.findUElementByTextFromPsi(refText: String, strict: Boolean = false): T {
    val elementAtStart = this.findElementAt(this.text.indexOf(refText))
        ?: throw AssertionError("requested text '$refText' was not found in $this")
    val uElementContainingText = elementAtStart.parentsWithSelf.let {
        if (strict) it.dropWhile { !it.text.contains(refText) } else it
    }.mapNotNull { it.toUElementOfType<T>() }.firstOrNull()
        ?: throw AssertionError("requested text '$refText' not found as '${T::class.java.canonicalName}' in $this")
    if (strict && uElementContainingText.psi != null && uElementContainingText.psi?.text != refText) {
        throw AssertionError("requested text '$refText' found as '${uElementContainingText.psi?.text}' in $uElementContainingText")
    }
    return uElementContainingText;
}
