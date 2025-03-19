// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import com.intellij.util.ThrowableRunnable
import junit.framework.TestCase
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.toUElement
import org.junit.Test
import java.util.*

class JavaUastPerformanceTest : AbstractJavaUastTest() {
  override fun check(testName: String, file: UFile) {
  }

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
    val clazz = myFixture.addClass("""
      class MyClass {
          String foo(){
             return new java.lang.StringBuilder()
             ${(1..1000).joinToString("\n") { ".append(\"$it\")" }}
             .toString();
          }
      }
    """.trimIndent())
    Benchmark.newBenchmark("convert each element to uast first time") {
      val walker = EachPsiToUastWalker()
      clazz.accept(walker)
      TestCase.assertEquals(4019, walker.totalCount)
    }.attempts(1).start()
  }

  @Test
  fun testConvertAllElementsWithNaiveToUElement() {
    myFixture.configureByFile("Performance/Thinlet.java")
    Benchmark.newBenchmark(getTestName(false), object : ThrowableRunnable<Throwable?> {
      var hash = 0
      override fun run() {
        for (i in 0..99) {
          file.accept(object : PsiRecursiveElementWalkingVisitor() {
            override fun visitElement(element: PsiElement) {
              hash += Objects.hashCode(element.toUElement())
              super.visitElement(element)
            }
          })
        }
        assertTrue(hash != 0)
      }
    })
      .setup { PsiManager.getInstance(project).dropPsiCaches() }
      .warmupIterations(1)
      .start()
  }
}