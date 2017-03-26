package com.jetbrains.edu.learning.checker;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.content.Content;
import com.jetbrains.edu.learning.StudyState;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduDocumentListener;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.Task;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.editor.StudyEditor;
import com.jetbrains.edu.learning.navigation.StudyNavigator;
import com.jetbrains.edu.learning.ui.StudyTestResultsToolWindowFactory;
import com.jetbrains.edu.learning.ui.StudyTestResultsToolWindowFactoryKt;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.List;
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


  public static void showTestResultPopUp(final String text, Color color, @NotNull final Project project) {
    BalloonBuilder balloonBuilder =
      JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(text, null, color, null);
    final Balloon balloon = balloonBuilder.createBalloon();
    StudyUtils.showCheckPopUp(project, balloon);
  }


  public static void runSmartTestProcess(@NotNull final VirtualFile taskDir,
                                         @NotNull final StudyTestRunner testRunner,
                                         @NotNull final String taskFileName,
                                         @NotNull final TaskFile taskFile,
                                         @NotNull final Project project) {
    final VirtualFile virtualFile = taskDir.findFileByRelativePath(taskFileName);
    if (virtualFile == null) {
      return;
    }
    Pair<VirtualFile, TaskFile> pair = getCopyWithAnswers(taskDir, virtualFile, taskFile);
    if (pair == null) {
      return;
    }
    VirtualFile answerFile = pair.getFirst();
    TaskFile answerTaskFile = pair.getSecond();
    try {
      for (final AnswerPlaceholder answerPlaceholder : answerTaskFile.getActivePlaceholders()) {
        final Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
        if (document == null) {
          continue;
        }
        StudySmartChecker.smartCheck(answerPlaceholder, project, answerFile, answerTaskFile, taskFile, testRunner,
                                     virtualFile, document);
      }
    }
    finally {
      StudyUtils.deleteFile(answerFile);
    }
  }


  private static Pair<VirtualFile, TaskFile> getCopyWithAnswers(@NotNull final VirtualFile taskDir,
                                                @NotNull final VirtualFile file,
                                                @NotNull final TaskFile source) {
    try {
      VirtualFile answerFile = file.copy(taskDir, taskDir, file.getNameWithoutExtension() + EduNames.ANSWERS_POSTFIX + "." + file.getExtension());
      final FileDocumentManager documentManager = FileDocumentManager.getInstance();
      final Document document = documentManager.getDocument(answerFile);
      if (document != null) {
        TaskFile answerTaskFile = source.getTask().copy().getTaskFile(StudyUtils.pathRelativeToTask(file));
        if (answerTaskFile == null) {
          return null;
        }
        EduDocumentListener listener = new EduDocumentListener(answerTaskFile);
        document.addDocumentListener(listener);
        for (AnswerPlaceholder answerPlaceholder : answerTaskFile.getActivePlaceholders()) {
          final int start = answerPlaceholder.getOffset();
          final int end = start + answerPlaceholder.getRealLength();
          final String text = answerPlaceholder.getPossibleAnswer();
          document.replaceString(start, end, text);
        }
        ApplicationManager.getApplication().runWriteAction(() -> documentManager.saveDocument(document));
        return Pair.create(answerFile, answerTaskFile);
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return null;
  }


  public static boolean hasBackgroundProcesses(@NotNull Project project) {
    final IdeFrame frame = ((WindowManagerEx)WindowManager.getInstance()).findFrameFor(project);
    final StatusBarEx statusBar = frame == null ? null : (StatusBarEx)frame.getStatusBar();
    if (statusBar != null) {
      final List<Pair<TaskInfo, ProgressIndicator>> processes = statusBar.getBackgroundProcesses();
      if (!processes.isEmpty()) return true;
    }
    return false;
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

  public static void showTestResultsToolWindow(@NotNull final Project project, @NotNull final String message, boolean solved) {
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
        if (!solved) {
          ((ConsoleViewImpl)component).print(message, ConsoleViewContentType.ERROR_OUTPUT);
        }
        else {
          ((ConsoleViewImpl)component).print(message, ConsoleViewContentType.NORMAL_OUTPUT);
        }
        window.setAvailable(true, () -> {});
        window.show(() -> {});
        return;
      }
    }
  }
}
