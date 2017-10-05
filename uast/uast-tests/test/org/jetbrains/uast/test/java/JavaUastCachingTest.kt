// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java

import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import junit.framework.TestCase
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.toUElement
import org.junit.Test

class JavaUastCachingTest : LightCodeInsightFixtureTestCase() {

  class EachPsiToUastWalker : PsiElementVisitor() {
    var totalCount = 0
    var identityChecksum = 0
    override fun visitElement(element: PsiElement) {
      val uElement = element.toUElement()
      when (uElement) {
        is UQualifiedReferenceExpression -> uElement.receiver // force lazy receiver evaluation
      }
      if (uElement != null) {
        totalCount++
        identityChecksum = 31 * identityChecksum + System.identityHashCode(uElement)
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
      PlatformTestUtil.startPerformanceTest("NO CACHE: convert each element to uast first time", 2000) {
        clazz.accept(this)
        TestCase.assertEquals(expectedUElementsCount, totalCount)
      }.attempts(1).assertTiming()
    }

    val cachedWalk = EachPsiToUastWalker().apply {
      PlatformTestUtil.startPerformanceTest("CACHED: convert each element to uast using cache", 800) {
        clazz.accept(this)
        TestCase.assertEquals(expectedUElementsCount, totalCount)
      }.attempts(1).assertTiming()
    }

    TestCase.assertEquals("uElements walked twice should be the same", nonCachedWalk.identityChecksum, cachedWalk.identityChecksum)

  }

  @Test
  fun testUastCacheInvalidation() {
    myFixture.configureByText("MyClass.java", """
      class MyClass {
          String foo(){
             return new java.lang.StringBuilder("<caret>")
             .toString();
          }
      }
    """.trimIndent())

    fun myClass() = myFixture.findClass("MyClass")

    fun myFooMethod() = myClass().findMethodsByName("foo", false).first()

    val walkBeforeChange = EachPsiToUastWalker().apply { myClass().accept(this) }.also {
      val runAgain = EachPsiToUastWalker().apply { myClass().accept(this) }
      TestCase.assertEquals("uElements walked twice should be the same", it.identityChecksum, runAgain.identityChecksum)
    }

    val myUFooMethodBeforeChange = myFooMethod().toUElement().also {
      val runAgain = myFooMethod().toUElement()
      TestCase.assertSame("uElements got twice should be the same because caching should be used", it, runAgain)
    }

    myFixture.type("abc")
    PsiDocumentManager.getInstance(project).commitAllDocuments()

    val myUFooMethodAfterChange = myFooMethod().toUElement()
    TestCase.assertFalse("foo methods uasts should not be equal",myUFooMethodBeforeChange === myUFooMethodAfterChange)
    val eachPsi2 = EachPsiToUastWalker().apply { myClass().accept(this) }
    TestCase.assertFalse("uElements after change should not be the same", walkBeforeChange.identityChecksum == eachPsi2.identityChecksum)

  }


}