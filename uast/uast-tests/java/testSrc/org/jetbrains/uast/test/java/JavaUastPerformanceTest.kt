// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.StressTestApplication
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import com.intellij.util.ThrowableRunnable
import kotlinx.coroutines.Dispatchers
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.toUElement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Objects

@PerformanceUnitTest
@StressTestApplication
class JavaUastPerformanceTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val uast by javaUastFixture(projectFixture)

  @Test
  fun testVeryLongQualifiedReferenceExpression() {
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
    val clazz = uast.fixture.addClass("""
      class MyClass {
          String foo(){
             return new java.lang.StringBuilder()
             ${(1..1000).joinToString("\n") { ".append(\"$it\")" }}
             .toString();
          }
      }
    """.trimIndent())
    Benchmark.newBenchmark("convert each element to uast first time") {
      ReadAction.runBlocking<RuntimeException> {
        val walker = EachPsiToUastWalker()
        clazz.accept(walker)
        assertEquals(4019, walker.totalCount)
      }
    }.attempts(1).start()
  }

  @Test
  fun testConvertAllElementsWithNaiveToUElement() {
    val myFixture = uast.fixture
    myFixture.configureByFile("Performance/Thinlet.java")
    Benchmark.newBenchmark(myFixture.getTestName(false), object : ThrowableRunnable<Throwable?> {
      var hash = 0
      override fun run() {
        ReadAction.runBlocking<RuntimeException> {
          repeat(100) {
            myFixture.file.accept(object : PsiRecursiveElementWalkingVisitor() {
              override fun visitElement(element: PsiElement) {
                hash += Objects.hashCode(element.toUElement())
                super.visitElement(element)
              }
            })
          }
        }
        assertTrue(hash != 0)
      }
    })
      .setup {
        timeoutRunBlocking(context = Dispatchers.EDT) {
          PsiManager.getInstance(myFixture.project).dropPsiCaches()
        }
      }
      .warmupIterations(1)
      .start()
  }
}