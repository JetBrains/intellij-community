package org.intellij.plugins.markdown.editor.tables.inspections

import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction
import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project

/**
 * Same as default [IntentionWrapper] but passes actual descriptor element instead of file.
 */
internal open class IntentionOnElementAtCaretWrapper(intention: BaseElementAtCaretIntentionAction): IntentionWrapper(intention) {
  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement ?: return
    val file = element.containingFile ?: return
    val fileEditor = FileEditorManager.getInstance(project).getSelectedEditor(file.virtualFile)
    val editor = (fileEditor as? TextEditor)?.editor ?: return
    val action = action as BaseElementAtCaretIntentionAction
    action.invoke(project, editor, element)
  }
}
