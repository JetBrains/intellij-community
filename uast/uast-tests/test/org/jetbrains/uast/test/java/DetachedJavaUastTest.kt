// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java

import com.intellij.psi.PsiDeclarationStatement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiExpressionListStatement
import com.intellij.psi.util.PsiTreeUtil
import junit.framework.TestCase
import org.jetbrains.uast.UFile
import org.jetbrains.uast.toUElement
import org.junit.Test

class DetachedJavaUastTest : AbstractJavaUastTest() {

  private val detachers = mapOf(
    PsiDeclarationStatement::class.java through PsiElementFactory::createStatementFromText,
    PsiExpressionListStatement::class.java through PsiElementFactory::createStatementFromText
  )

  override fun check(testName: String, file: UFile) {
    val detachablePsiElements = PsiTreeUtil.collectElementsOfType(file.psi, *detachers.keys.toTypedArray())
      .map { psiElement -> psiElement to detachers.entries.first { it.key.isAssignableFrom(psiElement.javaClass) }.value }

    for ((element, detacher) in detachablePsiElements) {
      val attachedUElement = element.toUElement() ?: throw AssertionError(
        "UElement should be defined for attached element $element with text '${element.text}' in $testName")
      val detachedPsiElement = detacher.invoke(elementFactory, element.text, element)
      val detachedUElement = try {
        detachedPsiElement.toUElement()
      }
      catch (e: Exception) {
        throw AssertionError("failed to convert '${detachedPsiElement.text}' to Uast in $testName", e)
      }
      TestCase.assertNotNull(
        "UElement(${attachedUElement.javaClass}) should be defined for detached element with text '${detachedPsiElement.text}' in $testName\"",
        detachedUElement)
    }

  }

  @Test
  fun testDataClass() = doTest("DataClass/DataClass.java")

  @Test
  fun testEnumSwitch() = doTest("Simple/EnumSwitch.java")

  @Test
  fun testLocalClass() = doTest("Simple/LocalClass.java")

  @Test
  fun testReturnX() = doTest("Simple/ReturnX.java")

  @Test
  fun testFor() = doTest("Simple/For.java")

  @Test
  fun testVariableAnnotation() = doTest("Simple/VariableAnnotation.java")

}

private infix fun Class<out PsiElement>.through(detacher: (PsiElementFactory, String, PsiElement) -> PsiElement)
  : Pair<Class<PsiElement>, (PsiElementFactory, String, PsiElement) -> PsiElement> = (this as Class<PsiElement>) to detacher
