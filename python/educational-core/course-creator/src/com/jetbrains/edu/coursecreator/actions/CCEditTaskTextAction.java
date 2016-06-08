package com.jetbrains.edu.coursecreator.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.learning.StudyState;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.editor.StudyEditor;
import com.jetbrains.edu.learning.ui.StudyToolWindow;
import org.jetbrains.annotations.NotNull;

public class CCEditTaskTextAction extends ToggleAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance(CCEditTaskTextAction.class);

  public CCEditTaskTextAction() {
    super("Editing Mode", "Editing Mode", AllIcons.Modules.Edit);
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return false;
    }
    return StudyTaskManager.getInstance(project).getToolWindowMode() == StudyToolWindow.StudyToolWindowMode.EDITING;
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    StudyToolWindow window = StudyUtils.getStudyToolWindow(project);
    if (window == null) {
      return;
    }

    final StudyEditor selectedEditor = StudyUtils.getSelectedStudyEditor(project);
    if (selectedEditor == null) {
      StudyTaskManager.getInstance(project).setTurnEditingMode(true);
      return;
    }
    final StudyState studyState = new StudyState(selectedEditor);
    VirtualFile taskTextFile = StudyUtils.findTaskDescriptionVirtualFile(studyState.getTaskDir());
    if (taskTextFile == null) {
      LOG.info("Failed to find task.html");
      return;
    }
    Document document = FileDocumentManager.getInstance().getDocument(taskTextFile);
    if (!state) {
      if (document != null) {
        FileDocumentManager.getInstance().saveDocument(document);
      }
      window.leaveEditingMode(project);
      return;
    }
    window.enterEditingMode(taskTextFile, project);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }

    if (!CCUtils.isCourseCreator(project)) {
      e.getPresentation().setEnabledAndVisible(false);
    }
  }
}
