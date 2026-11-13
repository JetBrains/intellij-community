// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDeclarationStatement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiExpressionListStatement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.uast.UFile
import org.jetbrains.uast.toUElement
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

@TestApplication
class DetachedJavaUastTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val uast by javaUastFixture(projectFixture) { testName, file -> check(testName, file) }

  private val detachers = mapOf(
    PsiDeclarationStatement::class.java through PsiElementFactory::createStatementFromText,
    PsiExpressionListStatement::class.java through PsiElementFactory::createStatementFromText
  )

  private fun check(testName: String, file: UFile) {
    val detachablePsiElements = PsiTreeUtil.collectElementsOfType(file.sourcePsi, *detachers.keys.toTypedArray())
      .map { psiElement -> psiElement to detachers.entries.first { it.key.isAssignableFrom(psiElement.javaClass) }.value }

    for ((element, detacher) in detachablePsiElements) {
      val attachedUElement = element.toUElement() ?: throw AssertionError(
        "UElement should be defined for attached element $element with text '${element.text}' in $testName")
      val detachedPsiElement = detacher.invoke(JavaPsiFacade.getElementFactory(uast.fixture.project), element.text, element)
      val detachedUElement = try {
        detachedPsiElement.toUElement()
      }
      catch (e: Exception) {
        throw AssertionError("failed to convert '${detachedPsiElement.text}' to Uast in $testName", e)
      }
      assertNotNull(
        detachedUElement,
        "UElement(${attachedUElement.javaClass}) should be defined for detached element with text '${detachedPsiElement.text}' in $testName\"")
    }

  }

  @Test
  fun testDataClass(): Unit = uast.doTest("DataClass/DataClass.java")

  @Test
  fun testEnumSwitch(): Unit = uast.doTest("Simple/EnumSwitch.java")

  @Test
  fun testLocalClass(): Unit = uast.doTest("Simple/LocalClass.java")

  @Test
  fun testReturnX(): Unit = uast.doTest("Simple/ReturnX.java")

  @Test
  fun testFor(): Unit = uast.doTest("Simple/For.java")

  @Test
  fun testVariableAnnotation(): Unit = uast.doTest("Simple/VariableAnnotation.java")

}

private infix fun Class<out PsiElement>.through(detacher: (PsiElementFactory, String, PsiElement) -> PsiElement)
  : Pair<Class<PsiElement>, (PsiElementFactory, String, PsiElement) -> PsiElement> =
  @Suppress("UNCHECKED_CAST") (this as Class<PsiElement>) to detacher
