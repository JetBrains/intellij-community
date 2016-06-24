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
    Task nextTask = getTargetTask(studyState.getTask());
    if (nextTask == null) {
      return;
    }
    for (VirtualFile file : FileEditorManager.getInstance(project).getOpenFiles()) {
      FileEditorManager.getInstance(project).closeFile(file);
    }
    int nextTaskIndex = nextTask.getIndex();
    int lessonIndex = nextTask.getLesson().getIndex();
    Map<String, TaskFile> nextTaskFiles = nextTask.getTaskFiles();
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

    VirtualFile shouldBeActive = getFileToActivate(project, nextTaskFiles, taskDir);

    updateProjectView(project, shouldBeActive);

    ToolWindow runToolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.RUN);
    if (runToolWindow != null) {
      runToolWindow.hide(null);
    }
  }

  private void updateProjectView(@NotNull Project project, VirtualFile shouldBeActive) {
    JTree tree = ProjectView.getInstance(project).getCurrentProjectViewPane().getTree();
    if (shouldBeActive != null) {
      ProjectView.getInstance(project).selectCB(shouldBeActive, shouldBeActive, false).doWhenDone(() -> {
        List<TreePath> paths = TreeUtil.collectExpandedPaths(tree);
        List<TreePath> toCollapse = new ArrayList<TreePath>();
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
    VirtualFile shouldBeActive = null;
    for (Map.Entry<String, TaskFile> entry : nextTaskFiles.entrySet()) {
      String name = entry.getKey();
      TaskFile taskFile = entry.getValue();
      VirtualFile srcDir = taskDir.findChild(EduNames.SRC);
      VirtualFile vf = srcDir == null ? taskDir.findChild(name) : srcDir.findChild(name);
      if (vf != null) {
        if (shouldBeActive != null) {
          FileEditorManager.getInstance(project).openFile(vf, true);
        }
        if (shouldBeActive == null && !taskFile.getAnswerPlaceholders().isEmpty()) {
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
