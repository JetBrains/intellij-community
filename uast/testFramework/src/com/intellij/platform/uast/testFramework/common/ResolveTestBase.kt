// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.uast.testFramework.common

import com.intellij.psi.PsiNamedElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UResolvable
import com.intellij.platform.uast.testFramework.env.findElementByText
import org.junit.Assert.assertEquals

interface ResolveTestBase {
  fun check(testName: String, file: UFile) {
    val refComment = file.allCommentsInFile.find { it.text.startsWith("// REF:") } ?: throw IllegalArgumentException(
      "No // REF tag in file")
    val resultComment = file.allCommentsInFile.find { it.text.startsWith("// RESULT:") } ?: throw IllegalArgumentException(
      "No // RESULT tag in file")

    val refText = refComment.text.substringAfter("REF:")
    val parent = refComment.uastParent!!
    val matchingElement = parent.findElementByText<UResolvable>(refText)
    val resolveResult = matchingElement.resolve() ?: throw IllegalArgumentException("Unresolved reference")
    val resultText = resolveResult.javaClass.simpleName + (if (resolveResult is PsiNamedElement) ":${resolveResult.name}" else "")
    assertEquals(resultComment.text.substringAfter("RESULT:"), resultText)
  }
}
