package com.jetbrains.python;

import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class PythonTemplateRunner {

  public static void runTemplate(@NotNull PsiFile file, @NotNull TemplateBuilder builder) {
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
    final FileEditor editor = PythonUiService.getInstance().getSelectedEditor(project, anchor.getContainingFile().getVirtualFile());
    if (editor instanceof TextEditor) {
      builder.run(((TextEditor)editor).getEditor(), false);
    }
    else {
      builder.runNonInteractively(false);
    }
  }
}
