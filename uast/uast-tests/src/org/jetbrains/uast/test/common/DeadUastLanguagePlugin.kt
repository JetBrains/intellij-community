// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.common

import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.util.ClassSet
import org.jetbrains.uast.util.emptyClassSet
import org.junit.Assert

class DeadUastLanguagePlugin(override val language: Language) : UastLanguagePlugin {

  override fun isFileSupported(fileName: String): Boolean {
    Assert.fail("Uast should not be called in this test")
    return false
  }

  override val priority: Int get() = 0

  override fun convertElement(element: PsiElement, parent: UElement?, requiredType: Class<out UElement>?): UElement? {
    Assert.fail("Uast should not be called in this test")
    return null
  }

  override fun convertElementWithParent(element: PsiElement, requiredType: Class<out UElement>?): UElement? {
    Assert.fail("Uast should not be called in this test")
    return null
  }

  override fun getMethodCallExpression(element: PsiElement,
                                       containingClassFqName: String?,
                                       methodName: String): UastLanguagePlugin.ResolvedMethod? {
    Assert.fail("Uast should not be called in this test")
    return null
  }

  override fun getConstructorCallExpression(element: PsiElement, fqName: String): UastLanguagePlugin.ResolvedConstructor? {
    Assert.fail("Uast should not be called in this test")
    return null
  }

  override fun isExpressionValueUsed(element: UExpression): Boolean {
    Assert.fail("Uast should not be called in this test")
    return false
  }

  override fun getPossiblePsiSourceTypes(vararg uastTypes: Class<out UElement>): ClassSet<PsiElement> = emptyClassSet()
}