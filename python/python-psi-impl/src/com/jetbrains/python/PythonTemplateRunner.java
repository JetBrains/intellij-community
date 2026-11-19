package com.jetbrains.python;

import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Runs the live template of a quick fix in an editor of its file.
 * <p>
 * During an intention preview, the runner does nothing. The preview runs in a read action, where no editor can open,
 * and a fix can get a problem descriptor in the physical file.
 */
@ApiStatus.Internal
public final class PythonTemplateRunner {

  public static void runTemplate(@NotNull PsiFile file, @NotNull TemplateBuilder builder) {
    if (IntentionPreviewUtils.isIntentionPreviewActive()) return;
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return;
    final Editor editor = PythonUiService.getInstance().openTextEditor(file.getProject(), virtualFile);
    if (editor != null) {
      builder.run(editor, false);
    }
    else {
      builder.runNonInteractively(false);
    }
  }

  public static void runTemplateInSelectedEditor(@NotNull Project project, PsiElement anchor, @NotNull TemplateBuilder builder) {
    if (IntentionPreviewUtils.isIntentionPreviewActive()) return;
    VirtualFile virtualFile = anchor.getContainingFile().getVirtualFile();
    if (virtualFile == null) return;
    final FileEditor editor = PythonUiService.getInstance().getSelectedEditor(project, virtualFile);
    if (editor instanceof TextEditor te) {
      builder.run(te.getEditor(), false);
    }
    else {
      builder.runNonInteractively(false);
    }
  }

  public static void runInlineTemplate(@NotNull Project project,
                                       PsiElement element,
                                       TemplateBuilderImpl builder, int offset) {
    if (IntentionPreviewUtils.isIntentionPreviewActive()) return;
    final VirtualFile virtualFile = element.getContainingFile().getVirtualFile();
    if (virtualFile == null) {
      return;
    }
    Editor editor = PythonUiService.getInstance().openTextEditor(project, virtualFile, offset);
    if (editor != null) {
      Template template = builder.buildInlineTemplate();
      TemplateManager.getInstance(project).startTemplate(editor, template);
    }
    else {
      builder.runNonInteractively(true);
    }
  }
}
