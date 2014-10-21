package org.jetbrains.plugins.coursecreator.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.coursecreator.CCProjectService;
import org.jetbrains.plugins.coursecreator.format.TaskFile;
import org.jetbrains.plugins.coursecreator.format.TaskWindow;

import javax.swing.*;

abstract public class CCTaskWindowAction extends DumbAwareAction {

  public CCTaskWindowAction(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  @Nullable
  private static CCState getState(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
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
    TaskFile taskFile = CCProjectService.getInstance(project).getTaskFile(virtualFile);
    if (taskFile == null) {
      return null;
    }
    TaskWindow taskWindow = taskFile.getTaskWindow(editor.getDocument(), editor.getCaretModel().getLogicalPosition());
    if (taskWindow == null) {
      return null;
    }
    return new CCState(taskFile, taskWindow, psiFile, editor, project);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    boolean isAvailable = getState(e) != null;
    presentation.setEnabled(isAvailable);
    presentation.setVisible(isAvailable);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    CCState state = getState(e);
    if (state == null) {
      return;
    }
    performTaskWindowAction(state);
  }

  protected abstract void performTaskWindowAction(@NotNull final CCState state);

  protected static class CCState {
    private TaskFile myTaskFile;
    private TaskWindow myTaskWindow;
    private PsiFile myFile;
    private Editor myEditor;
    private Project myProject;

    public CCState(@NotNull final TaskFile taskFile,
                   @NotNull final TaskWindow taskWindow,
                   @NotNull final PsiFile file,
                   @NotNull final Editor editor,
                   @NotNull final Project project) {
      myTaskFile = taskFile;
      myTaskWindow = taskWindow;
      myFile = file;
      myEditor = editor;
      myProject = project;
    }

    @NotNull
    public TaskFile getTaskFile() {
      return myTaskFile;
    }

    @NotNull
    public TaskWindow getTaskWindow() {
      return myTaskWindow;
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
