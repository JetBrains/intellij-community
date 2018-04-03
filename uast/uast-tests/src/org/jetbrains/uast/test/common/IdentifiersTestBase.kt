/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.uast.test.common

import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UIdentifier
import org.jetbrains.uast.sourcePsiElement
import org.jetbrains.uast.test.env.assertEqualsToFile
import org.jetbrains.uast.toUElementOfType
import java.io.File

interface IdentifiersTestBase {
  fun getIdentifiersFile(testName: String): File

  private fun UFile.asIdentifiers(): String {
    val builder = StringBuilder()
    var level = 0
    (this.sourcePsi as PsiFile).accept(object : PsiElementVisitor() {
      override fun visitElement(element: PsiElement) {
        val uIdentifier = element.toUElementOfType<UIdentifier>()
        if (uIdentifier != null) {
          builder.append("    ".repeat(level))
          builder.append(uIdentifier.sourcePsiElement!!.text)
          builder.append(" -> ")
          builder.append(uIdentifier.uastParent?.asLogString())
          builder.appendln()
        }
        if (element is PsiCodeBlock) level++
        element.acceptChildren(this)
        if (element is PsiCodeBlock) level--
      }
    })
    return builder.toString()
  }

  fun check(testName: String, file: UFile) {
    val valuesFile = getIdentifiersFile(testName)

    assertEqualsToFile("Identifiers", valuesFile, file.asIdentifiers())
  }

}
