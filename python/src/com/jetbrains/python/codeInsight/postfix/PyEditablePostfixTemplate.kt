// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.postfix

import com.intellij.codeInsight.template.impl.TemplateImpl
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.editable.EditablePostfixTemplateWithMultipleExpressions
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.Conditions
import com.intellij.psi.PsiElement

@Suppress("PostfixTemplateDescriptionNotFound")
open class PyEditablePostfixTemplate(templateId: String,
                                     templateName: String,
                                     liveTemplate: TemplateImpl,
                                     example: String,
                                     conditions: Set<PyPostfixTemplateExpressionCondition?>,
                                     topmost: Boolean,
                                     provider: PostfixTemplateProvider,
                                     private val builtin: Boolean) :
  EditablePostfixTemplateWithMultipleExpressions<PyPostfixTemplateExpressionCondition?>(
    templateId, templateName, liveTemplate, example, conditions, topmost, provider
  ) {

  constructor(templateId: String,
              templateName: String,
              templateText: String,
              example: String,
              conditions: Set<PyPostfixTemplateExpressionCondition?>,
              topmost: Boolean,
              provider: PostfixTemplateProvider,
              builtin: Boolean) :
    this(templateId, templateName, createTemplate(templateText), example, conditions, topmost, provider, builtin)

  override fun getExpressions(context: PsiElement, document: Document, offset: Int): List<PsiElement> {
    val selector = if (myUseTopmostExpression) PyPostfixUtils.selectorTopmost() else PyPostfixUtils.selectorAllExpressionsWithCurrentOffset()
    val expressions = selector.getExpressions(context, document, offset)
    val condition = Conditions.and({ e: PsiElement -> e.textRange.endOffset == offset }, expressionCompositeCondition)
    return expressions.filter { condition.value(it) }
  }

  override fun isBuiltin(): Boolean = builtin

  override fun isEditable(): Boolean {
    return expressionConditions.all {
      it != null && (PyPostfixTemplateExpressionCondition.PUBLIC_CONDITIONS.containsKey(it.id) ||
                     PyPostfixTemplateExpressionCondition.PyClassCondition.ID == it.id)
    }
  }

  override fun getTopmostExpression(element: PsiElement): PsiElement {
    val expressionsInRange = PyPostfixUtils.getAllExpressionsAtOffset(element.containingFile, element.textOffset)
    val lastItem = expressionsInRange.lastOrNull()
    return lastItem ?: element
  }
}
