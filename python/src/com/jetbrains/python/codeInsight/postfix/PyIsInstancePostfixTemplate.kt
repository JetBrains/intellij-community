// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement

class PyIsInstancePostfixTemplate(provider: PostfixTemplateProvider) :
  StringBasedPostfixTemplate("isinstance", "isinstance(expr, )", PyPostfixUtils.selectorAllExpressionsWithCurrentOffset(), provider), DumbAware {

  override fun getTemplateString(element: PsiElement): String = "isinstance(\$expr\$, \$END\$)"

  override fun getElementToRemove(expr: PsiElement): PsiElement = expr

  override fun shouldReformat(): Boolean = false
}
