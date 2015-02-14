package com.jetbrains.edu.coursecreator.actions;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.jetbrains.edu.EduAnswerPlaceholderPainter;
import com.jetbrains.edu.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.courseFormat.TaskFile;
import com.jetbrains.edu.coursecreator.CCProjectService;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CCDeleteAnswerPlaceholder extends CCAnswerPlaceholderAction {

  public CCDeleteAnswerPlaceholder() {
    super("Delete Answer Placeholder","Delete answer placeholder", null);
  }

  @Override
  protected void performAnswerPlaceholderAction(@NotNull CCState state) {
    Project project = state.getProject();
    PsiFile psiFile = state.getFile();
    final Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
    if (document == null) return;
    TaskFile taskFile = state.getTaskFile();
    AnswerPlaceholder answerPlaceholder = state.getAnswerPlaceholder();
    final List<AnswerPlaceholder> answerPlaceholders = taskFile.getAnswerPlaceholders();
    if (answerPlaceholders.contains(answerPlaceholder)) {
      answerPlaceholders.remove(answerPlaceholder);
      final Editor editor = state.getEditor();
      editor.getMarkupModel().removeAllHighlighters();
      CCProjectService.getInstance(project).drawAnswerPlaceholders(psiFile.getVirtualFile(), editor);
      EduAnswerPlaceholderPainter.createGuardedBlocks(editor, taskFile, false);
    }
  }
}