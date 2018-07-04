// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.uast.UastVisitorAdapter
import junit.framework.TestCase
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import org.junit.Test

class UastVisitorAdapterTest : AbstractJavaUastTest() {

  override fun check(testName: String, file: UFile) {
    val psiFile = file.sourcePsi as PsiFile
    val toList = SyntaxTraverser.psiTraverser(psiFile).toList()

    val listSources = mutableSetOf<PsiElement>()
    val visitor = UastVisitorAdapter(object : AbstractUastNonRecursiveVisitor() {
      override fun visitElement(node: UElement): Boolean {
        node.sourcePsi?.let {
          TestCase.assertTrue("source elements should be unique ($it)", listSources.add(it))
        }
        return super.visitElement(node)
      }
    }, true)
    toList.forEach { it.accept(visitor) }
  }

  @Test
  fun testCallExpression() = doTest("Simple/CallExpression.java")

}

