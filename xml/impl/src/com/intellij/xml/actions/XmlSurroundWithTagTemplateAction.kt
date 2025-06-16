// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.actions

import com.intellij.codeInsight.actions.SimpleCodeInsightAction
import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.impl.InvokeTemplateAction
import com.intellij.codeInsight.template.impl.TemplateImpl
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilBase

class XmlSurroundWithTagTemplateAction : SimpleCodeInsightAction() {

  init {
    setInjectedContext(true)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)

    val project = e.project ?: return
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return
    val file = PsiUtilBase.getPsiFileInEditor(editor, project) ?: return

    if (!e.place.contains(ActionPlaces.EDITOR_FLOATING_TOOLBAR) && !e.isFromContextMenu) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    if (!editor.getSelectionModel().hasSelection()) {
      e.presentation.isEnabled = false
      return
    }
    e.presentation.isEnabled = createAction(editor, file) != null
  }

  override fun invoke(project: Project, editor: Editor, psiFile: PsiFile) {
    if (!FileDocumentManager.getInstance().requestWriting(editor.document, project)) return
    createAction(editor, psiFile)?.perform()
  }

  private fun createAction(editor: Editor, file: PsiFile): InvokeTemplateAction? {
    val templateActionContext = TemplateActionContext.surrounding(file, editor)
    val template = createTemplate()?.takeIf { TemplateManagerImpl.isApplicable(it, templateActionContext) } ?: return null
    return InvokeTemplateAction(template, editor, file.getProject(), mutableSetOf())
  }

  private fun createTemplate(): TemplateImpl? {
    return TemplateSettings.getInstance().getTemplate("T", "HTML/XML")
  }
}