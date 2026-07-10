// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement

/**
 * Wraps an expression with a call to a built-in such as `str`, `list`, `set`, `dict` or `tuple`:
 * `expr.list` -> `list(expr)`.
 */
class PyCallWrapPostfixTemplate(
  private val function: String,
  provider: PostfixTemplateProvider,
) : StringBasedPostfixTemplate(function, "$function(expr)", PyPostfixUtils.selectorAllExpressionsWithCurrentOffset(), provider), DumbAware {

  override fun getTemplateString(element: PsiElement): String = "$function(\$expr\$)\$END\$"

  override fun getElementToRemove(expr: PsiElement): PsiElement = expr

  override fun shouldReformat(): Boolean = false
}
