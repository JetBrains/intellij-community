package com.jetbrains.edu.learning.navigation;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.ui.tree.TreeUtil;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.*;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.statistics.EduUsagesCollector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StudyNavigator {
  private StudyNavigator() {

  }

  public static Task nextTask(@NotNull final Task task) {
    Lesson currentLesson = task.getLesson();
    List<Task> taskList = currentLesson.getTaskList();
    if (task.getIndex() < taskList.size()) {
      return taskList.get(task.getIndex());
    }
    Lesson nextLesson = nextLesson(currentLesson);
    if (nextLesson == null) {
      return null;
    }
    List<Task> nextLessonTaskList = nextLesson.getTaskList();
    while (nextLessonTaskList.isEmpty()) {
      nextLesson = nextLesson(nextLesson);
      if (nextLesson == null) {
        return null;
      }
      nextLessonTaskList = nextLesson.getTaskList();
    }
    return StudyUtils.getFirst(nextLessonTaskList);
  }

  public static Task previousTask(@NotNull final Task task) {
    Lesson currentLesson = task.getLesson();
    int prevTaskIndex = task.getIndex() - 2;
    if (prevTaskIndex >= 0) {
      return currentLesson.getTaskList().get(prevTaskIndex);
    }
    Lesson prevLesson = previousLesson(currentLesson);
    if (prevLesson == null) {
      return null;
    }
    //getting last task in previous lesson
    List<Task> prevLessonTaskList = prevLesson.getTaskList();
    while (prevLessonTaskList.isEmpty()) {
      prevLesson = previousLesson(prevLesson);
      if (prevLesson == null) {
        return null;
      }
      prevLessonTaskList = prevLesson.getTaskList();
    }
    return prevLessonTaskList.get(prevLessonTaskList.size() - 1);
  }

  public static Lesson nextLesson(@NotNull final Lesson lesson) {
    List<Lesson> lessons = lesson.getCourse().getLessons();
    int nextLessonIndex = lesson.getIndex();
    if (nextLessonIndex >= lessons.size()) {
      return null;
    }
    return lessons.get(nextLessonIndex);
  }

  public static Lesson previousLesson(@NotNull final Lesson lesson) {
    int prevLessonIndex = lesson.getIndex() - 2;
    if (prevLessonIndex < 0) {
      return null;
    }
    return lesson.getCourse().getLessons().get(prevLessonIndex);
  }

  public static void navigateToFirstFailedAnswerPlaceholder(@NotNull final Editor editor, @NotNull final TaskFile taskFile) {
    final Project project = editor.getProject();
    if (project == null) return;
    for (AnswerPlaceholder answerPlaceholder : taskFile.getActivePlaceholders()) {
      if (answerPlaceholder.getStatus() != StudyStatus.Failed) {
        continue;
      }
      navigateToAnswerPlaceholder(editor, answerPlaceholder);
      break;
    }
  }

  public static void navigateToAnswerPlaceholder(@NotNull final Editor editor, @NotNull final AnswerPlaceholder answerPlaceholder) {
    if (editor.isDisposed()) {
      return;
    }
    Pair<Integer, Integer> offsets = StudyUtils.getPlaceholderOffsets(answerPlaceholder, editor.getDocument());
    editor.getCaretModel().moveToOffset(offsets.first);
    editor.getSelectionModel().setSelection(offsets.first, offsets.second);
  }


  public static void navigateToFirstAnswerPlaceholder(@NotNull final Editor editor, @NotNull final TaskFile taskFile) {
    if (!taskFile.getActivePlaceholders().isEmpty()) {
      AnswerPlaceholder firstAnswerPlaceholder = StudyUtils.getFirst(taskFile.getActivePlaceholders());
      if (firstAnswerPlaceholder == null) return;
      navigateToAnswerPlaceholder(editor, firstAnswerPlaceholder);
    }
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

  public static void navigateToTask(@NotNull final Project project, @NotNull final String lessonName, @NotNull final String taskName) {
    final Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) {
      return;
    }
    final Lesson lesson = course.getLesson(lessonName);
    if (lesson == null) {
      return;
    }
    final Task task = lesson.getTask(taskName);
    if (task == null) {
      return;
    }
    ApplicationManager.getApplication().invokeLater(() -> navigateToTask(project, task));
  }

  public static void navigateToTask(@NotNull Project project, @NotNull Task task) {
    boolean tutorial = task.getLesson().getCourse().isTutorial();
    if (!tutorial) {
      for (VirtualFile file : FileEditorManager.getInstance(project).getOpenFiles()) {
        FileEditorManager.getInstance(project).closeFile(file);
      }
    }
    Map<String, TaskFile> taskFiles = task.getTaskFiles();
    VirtualFile taskDir = task.getTaskDir(project);
    if (taskDir == null) {
      return;
    }
    VirtualFile srcDir = taskDir.findChild(EduNames.SRC);
    if (srcDir != null) {
      taskDir = srcDir;
    }
    if (taskFiles.isEmpty()) {
      ProjectView.getInstance(project).select(taskDir, taskDir, false);
      return;
    }
    VirtualFile fileToActivate = tutorial ? null : getFirstTaskFile(taskDir, project);
    for (Map.Entry<String, TaskFile> entry : taskFiles.entrySet()) {
      final TaskFile taskFile = entry.getValue();
      if (taskFile.getActivePlaceholders().isEmpty()) {
        continue;
      }
      VirtualFile virtualFile = taskDir.findFileByRelativePath(entry.getKey());
      if (virtualFile == null) {
        continue;
      }
      FileEditorManager.getInstance(project).openFile(virtualFile, true);
      fileToActivate = virtualFile;
    }
    EduUsagesCollector.taskNavigation();
    if (fileToActivate != null) {
      updateProjectView(project, fileToActivate);
    }

    StudyUtils.selectFirstAnswerPlaceholder(StudyUtils.getSelectedStudyEditor(project), project);
    ToolWindow runToolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.RUN);
    if (runToolWindow != null) {
      runToolWindow.hide(null);
    }
  }

  private static void updateProjectView(@NotNull Project project, @NotNull VirtualFile fileToActivate) {
    AbstractProjectViewPane viewPane = ProjectView.getInstance(project).getCurrentProjectViewPane();
    if (viewPane == null) {
      return;
    }
    JTree tree = viewPane.getTree();
    ProjectView.getInstance(project).selectCB(fileToActivate, fileToActivate, false).doWhenDone(() -> {
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
          }
          else {
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
    FileEditorManager.getInstance(project).openFile(fileToActivate, true);
  }
}
