package com.jetbrains.edu.learning.checker;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.jetbrains.edu.learning.StudyState;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.editor.StudyEditor;
import com.jetbrains.edu.learning.navigation.StudyNavigator;
import com.jetbrains.edu.learning.ui.StudyTestResultsToolWindowFactory;
import com.jetbrains.edu.learning.ui.StudyTestResultsToolWindowFactoryKt;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class StudyCheckUtils {
  private static final Logger LOG = Logger.getInstance(StudyCheckUtils.class);

  private StudyCheckUtils() {
  }

  public static void drawAllPlaceholders(@NotNull final Project project, @NotNull final Task task) {
    VirtualFile taskDir = task.getTaskDir(project);
    if (taskDir == null) {
      return;
    }
    for (Map.Entry<String, TaskFile> entry : task.getTaskFiles().entrySet()) {
      String name = entry.getKey();
      TaskFile taskFile = entry.getValue();
      VirtualFile virtualFile = taskDir.findFileByRelativePath(name);
      if (virtualFile == null) {
        continue;
      }
      FileEditor fileEditor = FileEditorManager.getInstance(project).getSelectedEditor(virtualFile);
      if (fileEditor instanceof StudyEditor) {
        StudyEditor studyEditor = (StudyEditor)fileEditor;
        StudyUtils.drawAllAnswerPlaceholders(studyEditor.getEditor(), taskFile);
      }
    }
  }

  public static void navigateToFailedPlaceholder(@NotNull final StudyState studyState,
                                                 @NotNull final Task task,
                                                 @NotNull final VirtualFile taskDir,
                                                 @NotNull final Project project) {
    TaskFile selectedTaskFile = studyState.getTaskFile();
    Editor editor = studyState.getEditor();
    TaskFile taskFileToNavigate = selectedTaskFile;
    VirtualFile fileToNavigate = studyState.getVirtualFile();
    final StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
    if (!taskManager.hasFailedAnswerPlaceholders(selectedTaskFile)) {
      for (Map.Entry<String, TaskFile> entry : task.getTaskFiles().entrySet()) {
        String name = entry.getKey();
        TaskFile taskFile = entry.getValue();
        if (taskManager.hasFailedAnswerPlaceholders(taskFile)) {
          taskFileToNavigate = taskFile;
          VirtualFile virtualFile = taskDir.findFileByRelativePath(name);
          if (virtualFile == null) {
            continue;
          }
          FileEditor fileEditor = FileEditorManager.getInstance(project).getSelectedEditor(virtualFile);
          if (fileEditor instanceof StudyEditor) {
            StudyEditor studyEditor = (StudyEditor)fileEditor;
            editor = studyEditor.getEditor();
          }
          fileToNavigate = virtualFile;
          break;
        }
      }
    }
    if (fileToNavigate != null) {
      FileEditorManager.getInstance(project).openFile(fileToNavigate, true);
    }
    final Editor editorToNavigate = editor;
    ApplicationManager.getApplication().invokeLater(
      () -> IdeFocusManager.getInstance(project).requestFocus(editorToNavigate.getContentComponent(), true));

    StudyNavigator.navigateToFirstFailedAnswerPlaceholder(editor, taskFileToNavigate);
  }


  public static void showTestResultPopUp(@NotNull final String text, Color color, @NotNull final Project project) {
    BalloonBuilder balloonBuilder =
      JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(text, null, color, null);
    final Balloon balloon = balloonBuilder.createBalloon();
    StudyUtils.showCheckPopUp(project, balloon);
  }


  public static void flushWindows(@NotNull final Task task, @NotNull final VirtualFile taskDir) {
    for (Map.Entry<String, TaskFile> entry : task.getTaskFiles().entrySet()) {
      String name = entry.getKey();
      TaskFile taskFile = entry.getValue();
      VirtualFile virtualFile = taskDir.findFileByRelativePath(name);
      if (virtualFile == null) {
        continue;
      }
      EduUtils.flushWindows(taskFile, virtualFile);
    }
  }

  public static void showTestResultsToolWindow(@NotNull final Project project, @NotNull final String message) {
    ApplicationManager.getApplication().invokeLater(() -> {
      final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
      ToolWindow window = toolWindowManager.getToolWindow(StudyTestResultsToolWindowFactoryKt.ID);
      if (window == null) {
        toolWindowManager.registerToolWindow(StudyTestResultsToolWindowFactoryKt.ID, true, ToolWindowAnchor.BOTTOM);
        window = toolWindowManager.getToolWindow(StudyTestResultsToolWindowFactoryKt.ID);
        new StudyTestResultsToolWindowFactory().createToolWindowContent(project, window);
      }

      final Content[] contents = window.getContentManager().getContents();
      for (Content content : contents) {
        final JComponent component = content.getComponent();
        if (component instanceof ConsoleViewImpl) {
          ((ConsoleViewImpl)component).clear();
          ((ConsoleViewImpl)component).print(message, ConsoleViewContentType.ERROR_OUTPUT);
          window.setAvailable(true,null);
          window.show(null);
        }
      }
    });
  }

  public static StudyTestsOutputParser.TestsOutput getTestOutput(@NotNull Process testProcess,
                                                                 @NotNull String commandLine,
                                                                 boolean isAdaptive) {
    final CapturingProcessHandler handler = new CapturingProcessHandler(testProcess, null, commandLine);
    final ProcessOutput output = ProgressManager.getInstance().hasProgressIndicator() ? handler
      .runProcessWithProgressIndicator(ProgressManager.getInstance().getProgressIndicator()) :
                                 handler.runProcess();
    final StudyTestsOutputParser.TestsOutput testsOutput = StudyTestsOutputParser.getTestsOutput(output, isAdaptive);
    String stderr = output.getStderr();
    if (!stderr.isEmpty() && output.getStdout().isEmpty()) {
      LOG.info("#educational " + stderr);
      return new StudyTestsOutputParser.TestsOutput(false, stderr);
    }
    return testsOutput;
  }

  public static void hideTestResultsToolWindow(@NotNull Project project) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(StudyTestResultsToolWindowFactoryKt.ID);
    if (toolWindow != null) {
      toolWindow.hide(() -> {});
    }
  }
}
