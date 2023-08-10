// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import com.intellij.platform.uast.testFramework.common.RenderLogTestBase
import org.jetbrains.uast.visitor.UastVisitor

abstract class AbstractJavaRenderLogTest : AbstractJavaUastTest(), RenderLogTestBase {
  override fun check(testName: String, file: UFile, checkParentConsistency: Boolean) {
    super.check(testName, file, checkParentConsistency)

    file.accept(object : UastVisitor {
      override fun visitElement(node: UElement): Boolean {
        if (node is PsiElement && node.isPhysical) {
          UsefulTestCase.assertInstanceOf(node.containingFile, PsiJavaFile::class.java)
        }
        return false
      }
    })
  }
}