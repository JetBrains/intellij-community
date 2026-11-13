// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java

import com.intellij.platform.uast.testFramework.common.visitUFileAndGetResult
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.testFramework.assertEqualsToFile
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.util.IndentedPrintingVisitor
import org.junit.jupiter.api.Test
import java.io.File

@TestApplication
class JavaResolveEverythingTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val uast by javaUastFixture(projectFixture) { testName, file -> check(testName, file) }

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

  private fun check(testName: String, file: UFile) {
    val expected = File(uast.fixture.testDataPath, testName.substringBeforeLast('.') + ".resolved.txt")
    assertEqualsToFile("resolved", expected, file.resolvableWithTargets())
  }

  @Test
  fun testAnnotation(): Unit = uast.doTest("Simple/Annotation.java")

  @Test
  fun testAnonymous(): Unit = uast.doTest("Simple/Anonymous.java")

  @Test
  fun testAnonymousClassWithParameters(): Unit = uast.doTest("Simple/AnonymousClassWithParameters.java")

  @Test
  fun testComments(): Unit = uast.doTest("Simple/Comments.java")

  @Test
  fun testExternal(): Unit = uast.doTest("Simple/External.java")

  @Test
  fun testTryWithResources(): Unit = uast.doTest("Simple/TryWithResources.java")

  @Test
  fun testInnerClass(): Unit = uast.doTest("Simple/InnerClass.java")

  @Test
  fun testComplexCalls(): Unit = uast.doTest("Simple/ComplexCalls.java")

}