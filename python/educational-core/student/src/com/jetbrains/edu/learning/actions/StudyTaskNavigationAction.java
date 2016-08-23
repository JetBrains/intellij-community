package com.jetbrains.edu.learning.actions;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.ui.tree.TreeUtil;
import com.jetbrains.edu.learning.StudyState;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Task;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.editor.StudyEditor;
import com.jetbrains.edu.learning.navigation.StudyNavigator;
import com.jetbrains.edu.learning.statistics.EduUsagesCollector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


abstract public class StudyTaskNavigationAction extends StudyActionWithShortcut {
  public StudyTaskNavigationAction(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  public void navigateTask(@NotNull final Project project) {
    StudyEditor studyEditor = StudyUtils.getSelectedStudyEditor(project);
    StudyState studyState = new StudyState(studyEditor);
    if (!studyState.isValid()) {
      return;
    }
    Task targetTask = getTargetTask(studyState.getTask());
    if (targetTask == null) {
      return;
    }
    for (VirtualFile file : FileEditorManager.getInstance(project).getOpenFiles()) {
      FileEditorManager.getInstance(project).closeFile(file);
    }
    int nextTaskIndex = targetTask.getIndex();
    int lessonIndex = targetTask.getLesson().getIndex();
    Map<String, TaskFile> nextTaskFiles = targetTask.getTaskFiles();
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
    if (nextTaskFiles.isEmpty()) {
      ProjectView.getInstance(project).select(taskDir, taskDir, false);
      return;
    }
    EduUsagesCollector.taskNavigation();
    VirtualFile shouldBeActive = getFileToActivate(project, nextTaskFiles, taskDir);

    updateProjectView(project, shouldBeActive);

    StudyUtils.selectFirstAnswerPlaceholder(StudyUtils.getSelectedStudyEditor(project), project);
    ToolWindow runToolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.RUN);
    if (runToolWindow != null) {
      runToolWindow.hide(null);
    }
  }

  public static void updateProjectView(@NotNull Project project, VirtualFile shouldBeActive) {
    JTree tree = ProjectView.getInstance(project).getCurrentProjectViewPane().getTree();
    if (shouldBeActive != null) {
      ProjectView.getInstance(project).selectCB(shouldBeActive, shouldBeActive, false).doWhenDone(() -> {
        List<TreePath> paths = TreeUtil.collectExpandedPaths(tree);
        List<TreePath> toCollapse = new ArrayList<>();
        TreePath selectedPath = tree.getSelectionPath();
        for (TreePath treePath : paths) {
          if (treePath.isDescendant(selectedPath)) {
            continue;
          }
          if (toCollapse.isEmpty()) {
            toCollapse.add(treePath);
            continue;
          }
          for (int i = 0; i < toCollapse.size(); i++) {
            TreePath path = toCollapse.get(i);
            if (treePath.isDescendant(path)) {
              toCollapse.set(i, treePath);
            }  else {
              if (!path.isDescendant(treePath)) {
                toCollapse.add(treePath);
              }
            }
          }
        }
        for (TreePath path : toCollapse) {
          tree.collapsePath(path);
          tree.fireTreeCollapsed(path);
        }
      });
      FileEditorManager.getInstance(project).openFile(shouldBeActive, true);
    }
  }

  @Nullable
  protected VirtualFile getFileToActivate(@NotNull Project project, Map<String, TaskFile> nextTaskFiles, VirtualFile taskDir) {
    return StudyNavigator.getFileToActivate(project, nextTaskFiles, taskDir);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    navigateTask(project);
  }

  protected abstract Task getTargetTask(@NotNull final Task sourceTask);

  @Override
  public void update(AnActionEvent e) {
    StudyUtils.updateAction(e);
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    StudyEditor studyEditor = StudyUtils.getSelectedStudyEditor(project);
    StudyState studyState = new StudyState(studyEditor);
    if (!studyState.isValid()) {
      return;
    }
    if (getTargetTask(studyState.getTask()) == null) {
      e.getPresentation().setEnabled(false);
    }
  }
}
