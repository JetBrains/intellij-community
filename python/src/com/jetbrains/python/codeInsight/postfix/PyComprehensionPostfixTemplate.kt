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
 * Builds a list/set/generator comprehension over an iterable expression, e.g. `nums.compl` -> `[n for n in nums]`.
 */
class PyComprehensionPostfixTemplate(name: String, template: String, provider: PostfixTemplateProvider) :
  PyEditablePostfixTemplate(
    name,
    name,
    template,
    "[e for e in expr]",
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
    template.addVariable("VAR_EXPR", VariableNode("VAR", null), VariableNode("VAR", null), true)
  }
}
