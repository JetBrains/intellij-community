// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.fir.uast.test

import com.intellij.platform.uast.testFramework.env.findElementByText
import com.intellij.psi.PsiNamedElement
import org.jetbrains.fir.uast.test.env.kotlin.AbstractFirUastTest
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.test.kotlin.TEST_KOTLIN_MODEL_PATH
import org.junit.Assert
import org.junit.Test
import java.nio.file.Path

class KotlinUastResolveTest : AbstractFirUastTest() {

    override val testBasePath: Path = TEST_KOTLIN_MODEL_PATH

    override fun check(filePath: String, file: UFile) {
        val refComment = file.allCommentsInFile.find { it.text.startsWith("// REF:") } ?: throw IllegalArgumentException(
            "No // REF tag in file")
        val resultComment = file.allCommentsInFile.find { it.text.startsWith("// RESULT:") } ?: throw IllegalArgumentException(
            "No // RESULT tag in file")

        val refText = refComment.text.substringAfter("REF:")
        val parent = refComment.uastParent!!
        val matchingElement = parent.findElementByText<UResolvable>(refText)
        val resolveResult = matchingElement.resolve() ?: throw IllegalArgumentException("Unresolved reference")
        val resultText = resolveResult.javaClass.simpleName + (if (resolveResult is PsiNamedElement) ":${resolveResult.name}" else "")
        Assert.assertEquals(resultComment.text.substringAfter("RESULT:"), resultText)
    }

    @Test
    fun testMethodReference() = doCheck("MethodReference.kt")
}