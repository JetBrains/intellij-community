// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.postfix

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.jetbrains.python.codeInsight.imports.AddImportHelper

class PyCastPostfixTemplate(provider: PostfixTemplateProvider) :
  StringBasedPostfixTemplate("cast", "cast(, expr)", PyPostfixUtils.selectorAllExpressionsWithCurrentOffset(), provider), DumbAware {

  override fun getTemplateString(element: PsiElement): String = "cast(\$END\$, \$expr\$)"

  override fun getElementToRemove(expr: PsiElement): PsiElement = expr

  override fun shouldReformat(): Boolean = false

  override fun expandForChooseExpression(expr: PsiElement, editor: Editor) {
    val project = expr.project
    val file = expr.containingFile
    val document = editor.document
    val exprText = expr.text
    val marker = document.createRangeMarker(getElementToRemove(expr).textRange)

    // Add the import while the expression is still present, then perform the textual expansion below it so that the
    // running live template is never disturbed by a document change happening above its segments.
    AddImportHelper.addOrUpdateFromImportStatement(file, "typing", "cast", null, AddImportHelper.ImportPriority.BUILTIN, null)
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document)

    document.deleteString(marker.startOffset, marker.endOffset)
    editor.caretModel.moveToOffset(marker.startOffset)
    marker.dispose()

    val manager = TemplateManager.getInstance(project)
    val template = createTemplate(manager, getTemplateString(expr))
    template.addVariable(StringBasedPostfixTemplate.EXPR, TextExpression(exprText), false)
    manager.startTemplate(editor, template)
  }
}
