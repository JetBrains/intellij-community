package com.jetbrains.edu.learning;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.ui.EditorNotifications;
import com.jetbrains.edu.learning.checker.StudyCheckUtils;
import com.jetbrains.edu.learning.core.EduDocumentListener;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.*;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.courseFormat.tasks.TaskWithSubtasks;
import com.jetbrains.edu.learning.navigation.StudyNavigator;
import com.jetbrains.edu.learning.ui.StudyToolWindow;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;

public class StudySubtaskUtils {
  private static final Logger LOG = Logger.getInstance(StudySubtaskUtils.class);

  private StudySubtaskUtils() {
  }

  public static void switchStep(@NotNull Project project, @NotNull TaskWithSubtasks task, int toSubtaskIndex) {
    switchStep(project, task, toSubtaskIndex, true);
  }

  /***
   * @param toSubtaskIndex from 0 to subtaskNum - 1
   */
  public static void switchStep(@NotNull Project project, @NotNull TaskWithSubtasks task, int toSubtaskIndex, boolean navigateToTask) {
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
      VirtualFile virtualFile = taskDir.findFileByRelativePath(name);
      if (virtualFile == null) {
        continue;
      }
      TaskFile taskFile = entry.getValue();
      Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
      if (document == null) {
        continue;
      }
      EduDocumentListener listener = null;
      if (!FileEditorManager.getInstance(project).isFileOpen(virtualFile)) {
        listener = new EduDocumentListener(taskFile, true);
        document.addDocumentListener(listener);
      }
      updatePlaceholderTexts(document, taskFile, fromSubtaskIndex, toSubtaskIndex);
      if (listener != null) {
        document.removeDocumentListener(listener);
      }
      UndoManager.getInstance(project).nonundoableActionPerformed(DocumentReferenceManager.getInstance().create(document), false);
      EditorNotifications.getInstance(project).updateNotifications(virtualFile);
      if (StudyUtils.isStudentProject(project)) {
        WolfTheProblemSolver.getInstance(project).clearProblems(virtualFile);
        taskFile.setHighlightErrors(false);
      }
    }
    transformTestFile(project, toSubtaskIndex, taskDir);
    task.setActiveSubtaskIndex(toSubtaskIndex);
    updateUI(project, task, taskDir, navigateToTask);

    for (StudySubtaskChangeListener listener : Extensions.getExtensions(StudySubtaskChangeListener.EP_NAME)) {
      listener.subtaskChanged(project, task, fromSubtaskIndex, toSubtaskIndex);
    }
  }

  private static void transformTestFile(@NotNull Project project, int toSubtaskIndex, VirtualFile taskDir) {
    Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) {
      return;
    }
    EduPluginConfigurator configurator = EduPluginConfigurator.INSTANCE.forLanguage(course.getLanguageById());
    if (configurator == null) {
      return;
    }
    String defaultTestFileName = configurator.getTestFileName();
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

  public static void updateUI(@NotNull Project project, @NotNull Task task, VirtualFile taskDir, boolean navigateToTask) {
    StudyCheckUtils.drawAllPlaceholders(project, task);
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
    if (navigateToTask) {
      StudyNavigator.navigateToTask(project, task);
    }
  }

  private static void updatePlaceholderTexts(@NotNull Document document,
                                             @NotNull TaskFile taskFile,
                                             int fromSubtaskIndex,
                                             int toSubtaskIndex) {
    taskFile.setTrackLengths(false);
    for (AnswerPlaceholder placeholder : taskFile.getAnswerPlaceholders()) {
      placeholder.switchSubtask(document, fromSubtaskIndex, toSubtaskIndex);
    }
    taskFile.setTrackLengths(true);
  }

  public static void refreshPlaceholder(@NotNull Editor editor, @NotNull AnswerPlaceholder placeholder) {
    int prevSubtaskIndex = placeholder.getActiveSubtaskIndex() - 1;
    AnswerPlaceholderSubtaskInfo info = placeholder.getSubtaskInfos().get(prevSubtaskIndex);
    String replacementText = info != null ? info.getAnswer() : placeholder.getTaskText();
    EduUtils.replaceAnswerPlaceholder(editor.getDocument(), placeholder, placeholder.getRealLength(), replacementText);
  }
}