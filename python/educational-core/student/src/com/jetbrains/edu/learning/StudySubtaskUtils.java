package com.jetbrains.edu.learning;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.ui.EditorNotifications;
import com.jetbrains.edu.learning.checker.StudyCheckUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.*;
import com.jetbrains.edu.learning.ui.StudyToolWindow;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;

public class StudySubtaskUtils {
  private static final Logger LOG = Logger.getInstance(StudySubtaskUtils.class);

  private StudySubtaskUtils() {
  }

  /***
   * @param toSubtaskIndex from 0 to subtaskNum - 1
   */
  public static void switchStep(@NotNull Project project, @NotNull Task task, int toSubtaskIndex) {
    if (toSubtaskIndex == task.getActiveSubtaskIndex()) {
      return;
    }
    VirtualFile taskDir = task.getTaskDir(project);
    if (taskDir == null) {
      return;
    }
    VirtualFile srcDir = taskDir.findChild(EduNames.SRC);
    if (srcDir != null) {
      taskDir = srcDir;
    }
    int fromSubtaskIndex = task.getActiveSubtaskIndex();
    for (Map.Entry<String, TaskFile> entry : task.getTaskFiles().entrySet()) {
      String name = entry.getKey();
      VirtualFile virtualFile = taskDir.findChild(name);
      if (virtualFile == null) {
        continue;
      }
      TaskFile taskFile = entry.getValue();
      Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
      if (document == null) {
        continue;
      }
      updatePlaceholderTexts(project, document, taskFile, fromSubtaskIndex, toSubtaskIndex);
      EditorNotifications.getInstance(project).updateNotifications(virtualFile);
      if (StudyUtils.isStudentProject(project)) {
        WolfTheProblemSolver.getInstance(project).clearProblems(virtualFile);
        taskFile.setHighlightErrors(false);
      }
    }
    transformTestFile(project, toSubtaskIndex, taskDir);
    task.setActiveSubtaskIndex(toSubtaskIndex);
    updateUI(project, task, taskDir);
  }

  private static void transformTestFile(@NotNull Project project, int toSubtaskIndex, VirtualFile taskDir) {
    Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) {
      return;
    }
    StudyLanguageManager manager = StudyUtils.getLanguageManager(course);
    if (manager == null) {
      return;
    }
    String defaultTestFileName = manager.getTestFileName();
    String nameWithoutExtension = FileUtil.getNameWithoutExtension(defaultTestFileName);
    String extension = FileUtilRt.getExtension(defaultTestFileName);
    String subtaskTestFileName = nameWithoutExtension + EduNames.SUBTASK_MARKER + toSubtaskIndex;
    VirtualFile subtaskTestFile = taskDir.findChild(subtaskTestFileName + ".txt");
    if (subtaskTestFile != null) {
      ApplicationManager.getApplication().runWriteAction(() -> {
        try {
          subtaskTestFile.rename(project, subtaskTestFileName + "." + extension);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      });
    }
  }

  private static void updateUI(@NotNull Project project, @NotNull Task task, VirtualFile taskDir) {
    StudyCheckUtils.drawAllPlaceholders(project, task, taskDir);
    ProjectView.getInstance(project).refresh();
    StudyToolWindow toolWindow = StudyUtils.getStudyToolWindow(project);
    if (toolWindow != null) {
      String text = StudyUtils.getTaskTextFromTask(taskDir, task);
      if (text == null) {
        toolWindow.setEmptyText(project);
        return;
      }
      toolWindow.setTaskText(text, taskDir, project);
    }
  }

  private static void updatePlaceholderTexts(@NotNull Project project,
                                             @NotNull Document document,
                                             @NotNull TaskFile taskFile,
                                             int fromSubtaskIndex,
                                             int toSubtaskIndex) {
    taskFile.setTrackLengths(false);
    for (AnswerPlaceholder placeholder : taskFile.getAnswerPlaceholders()) {
      placeholder.switchSubtask(project, document, fromSubtaskIndex, toSubtaskIndex);
    }
    taskFile.setTrackLengths(true);
  }

  public static void refreshPlaceholder(@NotNull Project project, @NotNull Editor editor, @NotNull AnswerPlaceholder placeholder) {
    int prevSubtaskIndex = placeholder.getActiveSubtaskIndex() - 1;
    AnswerPlaceholderSubtaskInfo info = placeholder.getSubtaskInfos().get(prevSubtaskIndex);
    String replacementText = info != null ? info.getAnswer() : placeholder.getTaskText();
    EduUtils.replaceAnswerPlaceholder(project, editor.getDocument(), placeholder, placeholder.getRealLength(), replacementText);
  }
}