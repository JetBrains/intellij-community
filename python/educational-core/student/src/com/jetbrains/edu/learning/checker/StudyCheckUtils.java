package com.jetbrains.edu.learning.checker;

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
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.jetbrains.edu.EduDocumentListener;
import com.jetbrains.edu.EduUtils;
import com.jetbrains.edu.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.courseFormat.Task;
import com.jetbrains.edu.courseFormat.TaskFile;
import com.jetbrains.edu.learning.StudyState;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.editor.StudyEditor;
import com.jetbrains.edu.learning.navigation.StudyNavigator;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class StudyCheckUtils {
  private static final String ANSWERS_POSTFIX = "_answers";
  private static final Logger LOG = Logger.getInstance(StudyCheckUtils.class);

  private StudyCheckUtils() {
  }

  public static void drawAllPlaceholders(@NotNull final Project project, @NotNull final Task task, @NotNull final VirtualFile taskDir) {
    for (Map.Entry<String, TaskFile> entry : task.getTaskFiles().entrySet()) {
      String name = entry.getKey();
      TaskFile taskFile = entry.getValue();
      VirtualFile virtualFile = taskDir.findChild(name);
      if (virtualFile == null) {
        continue;
      }
      FileEditor fileEditor = FileEditorManager.getInstance(project).getSelectedEditor(virtualFile);
      if (fileEditor instanceof StudyEditor) {
        StudyEditor studyEditor = (StudyEditor)fileEditor;
        StudyUtils.drawAllWindows(studyEditor.getEditor(), taskFile);
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
          VirtualFile virtualFile = taskDir.findChild(name);
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
                                     final String taskFileName,
                                     @NotNull final TaskFile taskFile,
                                     @NotNull final Project project) {
    final TaskFile answerTaskFile = new TaskFile();
    answerTaskFile.name = taskFileName;
    final VirtualFile virtualFile = taskDir.findChild(taskFileName);
    if (virtualFile == null) {
      return;
    }
    final VirtualFile answerFile = getCopyWithAnswers(taskDir, virtualFile, taskFile, answerTaskFile);
    for (final AnswerPlaceholder answerPlaceholder : answerTaskFile.getAnswerPlaceholders()) {
      final Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
      if (document == null) {
        continue;
      }
      if (!answerPlaceholder.isValid(document)) {
        continue;
      }
      StudySmartChecker.smartCheck(answerPlaceholder, project, answerFile, answerTaskFile, taskFile, testRunner,
                                   virtualFile, document);
    }
    StudyUtils.deleteFile(answerFile);
  }



  private static VirtualFile getCopyWithAnswers(@NotNull final VirtualFile taskDir,
                                         @NotNull final VirtualFile file,
                                         @NotNull final TaskFile source,
                                         @NotNull final TaskFile target) {
    VirtualFile copy = null;
    try {

      copy = file.copy(taskDir, taskDir, file.getNameWithoutExtension() + ANSWERS_POSTFIX + "." + file.getExtension());
      final FileDocumentManager documentManager = FileDocumentManager.getInstance();
      final Document document = documentManager.getDocument(copy);
      if (document != null) {
        TaskFile.copy(source, target);
        EduDocumentListener listener = new EduDocumentListener(target);
        document.addDocumentListener(listener);
        for (AnswerPlaceholder answerPlaceholder : target.getAnswerPlaceholders()) {
          if (!answerPlaceholder.isValid(document)) {
            continue;
          }
          final int start = answerPlaceholder.getRealStartOffset(document);
          final int end = start + answerPlaceholder.getLength();
          final String text = answerPlaceholder.getPossibleAnswer();
          document.replaceString(start, end, text);
        }
        ApplicationManager.getApplication().runWriteAction(() -> {
          documentManager.saveDocument(document);
        });
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return copy;
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
      VirtualFile virtualFile = taskDir.findChild(name);
      if (virtualFile == null) {
        continue;
      }
      EduUtils.flushWindows(taskFile, virtualFile, true);
    }
  }
}
