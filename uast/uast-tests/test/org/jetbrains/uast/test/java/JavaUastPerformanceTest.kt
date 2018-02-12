// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import junit.framework.TestCase
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.toUElement
import org.junit.Test

class JavaUastPerformanceTest : LightCodeInsightFixtureTestCase() {

  class EachPsiToUastWalker : PsiElementVisitor() {
    var totalCount = 0
    var identityChecksum = 0
    override fun visitElement(element: PsiElement) {
      val uElement = element.toUElement()
      if (uElement != null) {
        totalCount++
        identityChecksum = 31 * identityChecksum + System.identityHashCode(uElement)
      }

      when (uElement) {
        is UQualifiedReferenceExpression -> uElement.receiver // force lazy evaluation
      }

      element.acceptChildren(this)
    }
  }

  @Test
  fun testVeryLongQualifiedReferenceExpression() {
    val expectedUElementsCount = 4019
    val clazz = myFixture.addClass("""
      class MyClass {
          String foo(){
             return new java.lang.StringBuilder()
             ${(1..1000).joinToString("\n") { ".append(\"$it\")" }}
             .toString();
          }
      }
    """.trimIndent())
    val nonCachedWalk = EachPsiToUastWalker().apply {
      PlatformTestUtil.startPerformanceTest("convert each element to uast first time", 2000) {
        clazz.accept(this)
        TestCase.assertEquals(expectedUElementsCount, totalCount)
      }.attempts(1).assertTiming()
    }

  }


}