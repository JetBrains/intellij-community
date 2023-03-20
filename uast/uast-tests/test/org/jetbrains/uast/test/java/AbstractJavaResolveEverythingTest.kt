// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java

import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.testFramework.assertEqualsToFile
import org.jetbrains.uast.*
import org.jetbrains.uast.test.common.visitUFileAndGetResult
import org.jetbrains.uast.util.IndentedPrintingVisitor
import java.io.File

abstract class AbstractJavaResolveEverythingTest : AbstractJavaUastTest() {
  private fun UFile.resolvableWithTargets() = object : IndentedPrintingVisitor(PsiCodeBlock::class, PsiModifierListOwner::class) {
    override fun render(element: PsiElement): CharSequence? =
      element
        .takeIf { it !is PsiIdentifier } // no sense to handle PsiIdentifier, see IDEA-207979
        ?.toUElementOfType<UExpression>()?.let { ref ->
          if(ref !is UResolvable) return@let null
          val refExpr = ref as? UReferenceExpression
          StringBuilder().apply {
            val parent = ref.uastParent
            append(parent?.asLogString())
            append(" -> ")
            append(ref.asLogString())
            append(" -> ")
            append(ref.resolve())
            append(": ")
            append(refExpr?.resolvedName)
          }
        }
  }.visitUFileAndGetResult(this)

  override fun check(testName: String, file: UFile) {
    val expected = File(testDataPath, testName.substringBeforeLast('.') + ".resolved.txt")
    assertEqualsToFile("resolved", expected, file.resolvableWithTargets())
  }
}
