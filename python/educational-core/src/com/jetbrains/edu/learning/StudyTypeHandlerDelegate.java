package com.jetbrains.edu.learning;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.courseFormat.tasks.TaskWithSubtasks;
import org.jetbrains.annotations.NotNull;

public class StudyTypeHandlerDelegate extends TypedHandlerDelegate {

  @Override
  public Result checkAutoPopup(char charTyped, Project project, Editor editor, PsiFile file) {
    return handleTyping(project, editor, file);
  }

  @Override
  public Result beforeCharTyped(char c,
                                Project project,
                                Editor editor,
                                PsiFile file,
                                FileType fileType) {
    return handleTyping(project, editor, file);
  }

  @NotNull
  private static Result handleTyping(Project project, Editor editor, PsiFile file) {
    if (!StudyUtils.isStudentProject(project)) {
      return Result.CONTINUE;
    }
    TaskFile taskFile = StudyUtils.getTaskFile(project, file.getVirtualFile());
    if (taskFile == null || !(taskFile.getTask() instanceof TaskWithSubtasks)) {
      return Result.CONTINUE;
    }
    if (taskFile.getAnswerPlaceholders().isEmpty()) {
      return Result.CONTINUE;
    }
    int offset = editor.getCaretModel().getOffset();
    boolean insidePlaceholder = taskFile.getAnswerPlaceholder(offset) != null;
    if (!insidePlaceholder) {
      HintManager.getInstance().showInformationHint(editor, "Text outside of placeholders is not editable in this task");
    }
    return insidePlaceholder ? Result.CONTINUE : Result.STOP;
  }
}
