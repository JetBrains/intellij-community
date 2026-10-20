// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement

/**
 * Prefixes a whole expression statement with a keyword such as `raise` or `yield`:
 * `expr.raise` -> `raise expr`.
 */
class PyStatementKeywordPostfixTemplate(
  private val keyword: String,
  provider: PostfixTemplateProvider,
) : StringBasedPostfixTemplate(keyword, "$keyword expr", PyPostfixUtils.selectorTopmost(), provider), DumbAware {

  override fun getTemplateString(element: PsiElement): String = "$keyword \$expr\$\$END\$"

  override fun shouldReformat(): Boolean = false
}
