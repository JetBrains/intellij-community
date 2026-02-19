// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.postfix

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.impl.MacroCallNode
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.codeInsight.template.impl.VariableNode
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.psi.PsiElement
import com.jetbrains.python.codeInsight.liveTemplates.CollectionElementNameMacro

class PyForEnumeratePostfixTemplate(name: String, provider: PostfixTemplateProvider) :
  PyEditablePostfixTemplate(
    name,
    name,
    "for \$INDEX$, \$VAR$ in enumerate(\$EXPR$):\n    \$END$",
    "for i, e in enumerate(expr)",
    setOf(PyPostfixTemplateExpressionCondition.PyIterable()),
    false,
    provider,
    true
  ) {

  override fun addTemplateVariables(element: PsiElement, template: Template) {
    super.addTemplateVariables(element, template)
    val name = MacroCallNode(CollectionElementNameMacro())
    name.addParameter(VariableNode("EXPR", null))
    template.addVariable("VAR", name, TextExpression("e"), true)
    template.addVariable("INDEX", null, TextExpression("i"), true)
  }
}
