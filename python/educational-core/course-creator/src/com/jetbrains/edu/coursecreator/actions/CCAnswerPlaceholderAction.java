package com.jetbrains.edu.coursecreator.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

abstract public class CCAnswerPlaceholderAction extends DumbAwareAction {

  protected CCAnswerPlaceholderAction(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  @Nullable
  protected static CCState getState(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null || !CCUtils.isCourseCreator(project)) {
      return null;
    }
    final PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(e.getDataContext());
    if (psiFile == null) {
      return null;
    }
    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) {
      return null;
    }
    final Editor editor = CommonDataKeys.EDITOR.getData(e.getDataContext());
    if (editor == null) {
      return null;
    }
    TaskFile taskFile = StudyUtils.getTaskFile(project, virtualFile);
    if (taskFile == null) {
      return null;
    }
    AnswerPlaceholder answerPlaceholder = taskFile.getAnswerPlaceholder(editor.getDocument(),
                                                                        editor.getCaretModel().getLogicalPosition(),
                                                                        true);
    return new CCState(taskFile, answerPlaceholder, psiFile, editor, project);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    CCState state = getState(e);
    if (state == null) {
      return;
    }
    performAnswerPlaceholderAction(state);
  }

  protected abstract void performAnswerPlaceholderAction(@NotNull final CCState state);

  protected static class CCState {
    private TaskFile myTaskFile;
    private AnswerPlaceholder myAnswerPlaceholder;
    private PsiFile myFile;
    private Editor myEditor;
    private Project myProject;

    public CCState(@NotNull final TaskFile taskFile,
                   @Nullable final AnswerPlaceholder answerPlaceholder,
                   @NotNull final PsiFile file,
                   @NotNull final Editor editor,
                   @NotNull final Project project) {
      myTaskFile = taskFile;
      myAnswerPlaceholder = answerPlaceholder;
      myFile = file;
      myEditor = editor;
      myProject = project;
    }

    @NotNull
    public TaskFile getTaskFile() {
      return myTaskFile;
    }

    @Nullable
    public AnswerPlaceholder getAnswerPlaceholder() {
      return myAnswerPlaceholder;
    }

    @NotNull
    public PsiFile getFile() {
      return myFile;
    }

    @NotNull
    public Editor getEditor() {
      return myEditor;
    }

    @NotNull
    public Project getProject() {
      return myProject;
    }
  }
}
