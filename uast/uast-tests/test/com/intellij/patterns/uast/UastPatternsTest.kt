// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.patterns.uast

import com.intellij.openapi.application.readAction
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PsiJavaPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.uast.UElement
import org.jetbrains.uast.toUElement
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@TestApplication
class UastPatternsTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun testCallParameters(): Unit = timeoutRunBlocking(timeout = 2.minutes) {

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

  private suspend fun assertAccepted(psiClass: PsiElement,
                                     pattern: ULiteralExpressionPattern,
                                     vararg expected: String) {
    val texts = readAction { getAcceptedElements(psiClass, pattern).map { it.sourcePsi?.text ?: "null" } }
    assertThat(texts).hasSize(expected.size).containsOnly(*expected)
  }

  private fun getAcceptedElements(psiClass: PsiElement,
                                  pattern: ElementPattern<out UElement>): List<UElement> =
    PsiTreeUtil.collectElements(psiClass, { true }).mapNotNull { it.toUElement() }.filter { pattern.accepts(it) }

}