package com.jetbrains.edu.learning.actions;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.ui.tree.TreeUtil;
import com.jetbrains.edu.EduNames;
import com.jetbrains.edu.courseFormat.Task;
import com.jetbrains.edu.courseFormat.TaskFile;
import com.jetbrains.edu.learning.StudyState;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.editor.StudyEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.util.Map;


abstract public class StudyTaskNavigationAction extends DumbAwareAction {
  public StudyTaskNavigationAction(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  public void navigateTask(@NotNull final Project project) {
    StudyEditor studyEditor = StudyUtils.getSelectedStudyEditor(project);
    StudyState studyState = new StudyState(studyEditor);
    if (!studyState.isValid()) {
      return;
    }
    Task nextTask = getTargetTask(studyState.getTask());
    if (nextTask == null) {
      BalloonBuilder balloonBuilder =
        JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(getNavigationFinishedMessage(), MessageType.INFO, null);
      Balloon balloon = balloonBuilder.createBalloon();
      assert studyEditor != null;
      balloon.show(StudyUtils.computeLocation(studyEditor.getEditor()), Balloon.Position.above);
      return;
    }
    for (VirtualFile file : FileEditorManager.getInstance(project).getOpenFiles()) {
      FileEditorManager.getInstance(project).closeFile(file);
    }
    int nextTaskIndex = nextTask.getIndex();
    int lessonIndex = nextTask.getLesson().getIndex();
    Map<String, TaskFile> nextTaskFiles = nextTask.getTaskFiles();
    if (nextTaskFiles.isEmpty()) {
      return;
    }
    VirtualFile projectDir = project.getBaseDir();
    String lessonDirName = EduNames.LESSON + String.valueOf(lessonIndex);
    if (projectDir == null) {
      return;
    }
    VirtualFile lessonDir = projectDir.findChild(lessonDirName);
    if (lessonDir == null) {
      return;
    }
    String taskDirName = EduNames.TASK + String.valueOf(nextTaskIndex);
    VirtualFile taskDir = lessonDir.findChild(taskDirName);
    if (taskDir == null) {
      return;
    }

    VirtualFile shouldBeActive = getFileToActivate(project, nextTaskFiles, taskDir);
    JTree tree = ProjectView.getInstance(project).getCurrentProjectViewPane().getTree();
    TreePath path = TreeUtil.getFirstNodePath(tree);
    tree.collapsePath(path);
    if (shouldBeActive != null) {
      ProjectView.getInstance(project).select(shouldBeActive, shouldBeActive, false);
      FileEditorManager.getInstance(project).openFile(shouldBeActive, true);
    }
    ToolWindow runToolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.RUN);
    if (runToolWindow != null) {
      runToolWindow.hide(null);
    }
  }

  @Nullable
  protected VirtualFile getFileToActivate(@NotNull Project project, Map<String, TaskFile> nextTaskFiles, VirtualFile taskDir) {
    VirtualFile shouldBeActive = null;
    for (Map.Entry<String, TaskFile> entry : nextTaskFiles.entrySet()) {
      String name = entry.getKey();
      TaskFile taskFile = entry.getValue();
      VirtualFile srcDir = taskDir.findChild("src");
      VirtualFile vf = srcDir == null ? taskDir.findChild(name) : srcDir.findChild(name);
      if (vf != null) {
        FileEditorManager.getInstance(project).openFile(vf, true);
        if (!taskFile.getAnswerPlaceholders().isEmpty()) {
          shouldBeActive = vf;
        }
      }
    }
    return shouldBeActive != null ? shouldBeActive : getFirstTaskFile(taskDir, project);
  }

  @Nullable
  private static VirtualFile getFirstTaskFile(@NotNull final VirtualFile taskDir, @NotNull final Project project) {
    for (VirtualFile virtualFile : taskDir.getChildren()) {
      if (StudyUtils.getTaskFile(project, virtualFile) != null) {
        return virtualFile;
      }
    }
    return null;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    navigateTask(project);
  }

  protected abstract String getNavigationFinishedMessage();

  protected abstract Task getTargetTask(@NotNull final Task sourceTask);

  @Override
  public void update(AnActionEvent e) {
    StudyUtils.updateAction(e);
  }
}
