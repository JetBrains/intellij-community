package org.intellij.plugins.markdown.editor.tables.inspections

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project

/**
 * Same as default [IntentionWrapper] but passes actual descriptor element instead of file.
 */
internal open class IntentionOnPsiElementWrapper(intention: PsiElementBaseIntentionAction): IntentionWrapper(intention) {
  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement ?: return
    val file = element.containingFile ?: return
    val fileEditor = FileEditorManager.getInstance(project).getSelectedEditor(file.virtualFile)
    val editor = (fileEditor as? TextEditor)?.editor
    val action = action as PsiElementBaseIntentionAction
    action.invoke(project, editor, element)
  }
}
