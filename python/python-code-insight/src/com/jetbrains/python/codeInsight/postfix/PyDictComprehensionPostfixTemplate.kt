// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.postfix

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.impl.MacroCallNode
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.codeInsight.template.impl.VariableNode
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.psi.PsiElement
import com.jetbrains.python.codeInsight.liveTemplates.CollectionElementNameMacro

/**
 * Builds a dict comprehension over an iterable expression, e.g. `nums.compd` -> `{n: n for n in nums}`.
 */
class PyDictComprehensionPostfixTemplate(provider: PostfixTemplateProvider) :
  PyEditablePostfixTemplate(
    "compd",
    "compd",
    "{\$KEY_EXPR$: \$VAL_EXPR$ for \$VAR$ in \$EXPR$}\$END$",
    "{k: v for k in expr}",
    setOf(PyPostfixTemplateExpressionCondition.PyIterable()),
    false,
    provider,
    true
  ) {

  override fun addTemplateVariables(element: PsiElement, template: Template) {
    super.addTemplateVariables(element, template)
    val varName = MacroCallNode(CollectionElementNameMacro())
    varName.addParameter(VariableNode("EXPR", null))
    template.addVariable("VAR", varName, TextExpression("e"), true)
    template.addVariable("KEY_EXPR", VariableNode("VAR", null), VariableNode("VAR", null), true)
    template.addVariable("VAL_EXPR", VariableNode("VAR", null), VariableNode("VAR", null), true)
  }
}
