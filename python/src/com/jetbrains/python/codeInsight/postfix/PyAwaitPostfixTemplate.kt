// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Condition
import com.intellij.psi.PsiElement
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.validation.PyImplicitAsyncContextProvider

class PyAwaitPostfixTemplate(provider: PostfixTemplateProvider) :
  StringBasedPostfixTemplate("await", "await expr", PyPostfixUtils.selectorAllExpressionsWithCurrentOffset(IN_ASYNC_CONTEXT), provider), DumbAware {

  override fun getTemplateString(element: PsiElement): String = "await \$expr\$\$END\$"

  override fun getElementToRemove(expr: PsiElement): PsiElement = expr

  override fun shouldReformat(): Boolean = false

  companion object {
    // "await" is only valid inside an async function (or an implicit async context such as the Python console).
    private val IN_ASYNC_CONTEXT: Condition<PsiElement> =
      Condition { element -> PyImplicitAsyncContextProvider.isAsyncAllowed(ScopeUtil.getScopeOwner(element)) }
  }
}
