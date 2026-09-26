// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.postfix

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement

/**
 * Turns a context-manager expression into a `with` block: `open(p).with` -> `with open(p) as f:`.
 */
class PyWithPostfixTemplate(provider: PostfixTemplateProvider) :
  StringBasedPostfixTemplate("with", "with expr as name:", PyPostfixUtils.selectorTopmost(), provider), DumbAware {

  override fun getTemplateString(element: PsiElement): String = "with \$expr\$ as \$name\$:\n    \$END\$"

  override fun setVariables(template: Template, element: PsiElement) {
    template.addVariable("name", TextExpression(""), true)
  }

  override fun shouldReformat(): Boolean = false
}
