// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java

import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.uast.*
import org.jetbrains.uast.test.common.visitUFileAndGetResult
import com.intellij.testFramework.assertEqualsToFile
import org.jetbrains.uast.util.IndentedPrintingVisitor
import java.io.File

abstract class AbstractJavaExpressionMultiResolveTest : AbstractJavaUastTest() {
  private fun UFile.resolvableWithTargets() = object : IndentedPrintingVisitor(PsiCodeBlock::class, PsiModifierListOwner::class) {
    override fun render(element: PsiElement): CharSequence? =
      element
        .takeIf { it !is PsiIdentifier } // no sense to handle PsiIdentifier, see IDEA-207979
        ?.toUElementOfType<UExpression>()?.let { ref ->
          StringBuilder().apply {
            val parent = ref.uastParent
            append(parent?.asLogString())
            append(" -> ")
            append(ref.asLogString())
            append(" -> ")
            append(multiResolve(ref))
            append(" ~= ")
            append(resolve(ref))
          }
        }
  }.visitUFileAndGetResult(this)

  private fun multiResolve(ref: UExpression): String {
    if (ref is UMultiResolvable) {
      return "[M] " + ref.multiResolve().joinToString(",") { it.element?.javaClass?.simpleName ?: "[]" }
    }
    if (ref is UResolvable) {
      return "[S] " + (ref.resolve()?.toString() ?: "[]")
    }
    return "[]"
  }

  private fun resolve(ref: UExpression): String {
    if (ref is UResolvable) {
      return (ref.resolve()?.toString() ?: "[]")
    }
    return "[]"
  }

  override fun check(testName: String, file: UFile) {
    val expected = File(testDataPath, testName.substringBeforeLast('.') + ".resolved.txt")
    assertEqualsToFile("resolved", expected, file.resolvableWithTargets())
  }
}
