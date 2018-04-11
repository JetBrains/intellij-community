// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.patterns.uast

import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PsiJavaPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.uast.UElement
import org.jetbrains.uast.toUElement
import org.junit.Test

class UastPatternsTest : LightCodeInsightFixtureTestCase() {

  @Test
  fun testCallParameters() {

    val psiClass = myFixture.addClass("""
      class MyClass {

          MyClass(String a, String b, String c){}

          MyClass foo(){
             String a = "justString";
             String b = new String("string-ctor-param");
             java.lang.System.out.println("method-param");
             return new MyClass("1ctor-prm", "2ctor-param", a);
          }
      }
    """.trimIndent())

    assertAccepted(psiClass, literalExpression(),
                   "\"method-param\"", "\"string-ctor-param\"", "\"justString\"", "\"1ctor-prm\"", "\"2ctor-param\"")
    assertAccepted(psiClass, literalExpression().inCall(callExpression()),
                   "\"string-ctor-param\"", "\"method-param\"", "\"1ctor-prm\"", "\"2ctor-param\"")
    assertAccepted(psiClass, literalExpression().inCall(
      callExpression().constructor(PsiJavaPatterns.psiClass())), "\"string-ctor-param\"", "\"1ctor-prm\"", "\"2ctor-param\"")
    assertAccepted(psiClass, literalExpression().callParameter(0, callExpression()),
                   "\"string-ctor-param\"", "\"1ctor-prm\"", "\"method-param\"")
    assertAccepted(psiClass, literalExpression().constructorParameter(0, "MyClass"), "\"1ctor-prm\"")

  }

  private fun assertAccepted(psiClass: PsiElement,
                             pattern: ULiteralExpressionPattern,
                             vararg expected: String) {
    assertSameElements(getAcceptedElements(psiClass, pattern).map { it.sourcePsi?.text ?: "null" },
                       *expected)
  }

  private fun getAcceptedElements(psiClass: PsiElement,
                                  pattern: ElementPattern<out UElement>): List<UElement> =
    PsiTreeUtil.collectElements(psiClass, { true }).mapNotNull { it.toUElement() }.filter { pattern.accepts(it) }

}