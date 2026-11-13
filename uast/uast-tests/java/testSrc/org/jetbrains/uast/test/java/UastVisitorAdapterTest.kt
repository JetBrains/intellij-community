// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java

import com.intellij.psi.PsiElement
import com.intellij.psi.SyntaxTraverser
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.uast.UastVisitorAdapter
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class UastVisitorAdapterTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val uast by javaUastFixture(projectFixture) { _, file -> check(file) }

  private fun check(file: UFile) {
    val psiFile = file.sourcePsi
    val toList = SyntaxTraverser.psiTraverser(psiFile).toList()

    val listSources = mutableSetOf<PsiElement>()
    val visitor = UastVisitorAdapter(object : AbstractUastNonRecursiveVisitor() {
      override fun visitElement(node: UElement): Boolean {
        node.sourcePsi?.let {
          assertTrue(listSources.add(it), "source elements should be unique ($it)")
        }
        return super.visitElement(node)
      }
    }, true)
    toList.forEach { it.accept(visitor) }
  }

  @Test
  fun testCallExpression(): Unit = uast.doTest("Simple/CallExpression.java")

}

