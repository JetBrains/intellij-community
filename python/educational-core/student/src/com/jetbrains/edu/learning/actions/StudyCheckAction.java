package com.jetbrains.edu.learning.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.jetbrains.edu.EduDocumentListener;
import com.jetbrains.edu.EduUtils;
import com.jetbrains.edu.courseFormat.*;
import com.jetbrains.edu.learning.StudyState;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.editor.StudyEditor;
import com.jetbrains.edu.learning.navigation.StudyNavigator;
import com.jetbrains.edu.learning.run.StudySmartChecker;
import com.jetbrains.edu.learning.run.StudyTestRunner;
import com.jetbrains.edu.stepic.EduStepicConnector;
import com.jetbrains.edu.stepic.StudySettings;
import icons.InteractiveLearningIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class StudyCheckAction extends DumbAwareAction {

  private static final Logger LOG = Logger.getInstance(StudyCheckAction.class.getName());
  private static final String ANSWERS_POSTFIX = "_answers";
  public static final String ACTION_ID = "CheckAction";
  public static final String SHORTCUT = "ctrl alt pressed ENTER";

  boolean checkInProgress = false;

  protected StudyCheckAction() {
    super("Check Task (" + KeymapUtil.getShortcutText(new KeyboardShortcut(KeyStroke.getKeyStroke(SHORTCUT), null)) + ")", "Check current task", InteractiveLearningIcons.Resolve);
  }


  public static StudyCheckAction createCheckAction(final Course course) {
    StudyCheckAction checkAction = StudyUtils.getCheckAction(course);
    return checkAction != null ? checkAction : new StudyCheckAction();
  }

  protected static void flushWindows(@NotNull final Task task, @NotNull final VirtualFile taskDir) {
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

  private static void drawAllPlaceholders(@NotNull final Project project, @NotNull final Task task, @NotNull final VirtualFile taskDir) {
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


  protected void check(@NotNull final Project project) {
    if (DumbService.isDumb(project)) {
      DumbService.getInstance(project).showDumbModeNotification("Check Action is not available while indexing is in progress");
      return;
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
          @Override
          public void run() {
            final StudyEditor selectedEditor = StudyUtils.getSelectedStudyEditor(project);
            if (selectedEditor == null) return;
            final StudyState studyState = new StudyState(selectedEditor);
            if (!studyState.isValid()) {
              LOG.error("StudyCheckAction was invoked outside study editor");
              return;
            }
            final IdeFrame frame = ((WindowManagerEx)WindowManager.getInstance()).findFrameFor(project);
            final StatusBarEx statusBar = frame == null ? null : (StatusBarEx)frame.getStatusBar();
            if (statusBar != null) {
              final List<Pair<TaskInfo, ProgressIndicator>> processes = statusBar.getBackgroundProcesses();
              if (!processes.isEmpty()) return;
            }

            final Task task = studyState.getTask();
            final VirtualFile taskDir = studyState.getTaskDir();
            flushWindows(task, taskDir);
            final StudyRunAction runAction = (StudyRunAction)ActionManager.getInstance().getAction(StudyRunAction.ACTION_ID);
            if (runAction == null) {
              return;
            }
            runAction.run(project);
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                IdeFocusManager.getInstance(project).requestFocus(studyState.getEditor().getComponent(), true);
              }
            });

            final StudyTestRunner testRunner = StudyUtils.getTestRunner(task, taskDir);
            Process testProcess = null;
            String commandLine = "";
            try {
              final VirtualFile executablePath = getTaskVirtualFile(studyState, task, taskDir);
              if (executablePath != null) {
                commandLine = executablePath.getPath();
                testProcess = testRunner.createCheckProcess(project, commandLine);
              }
            }
            catch (ExecutionException e) {
              LOG.error(e);
            }
            if (testProcess == null) {
              return;
            }
            checkInProgress = true;
            ProgressManager.getInstance().run(getCheckTask(studyState, testRunner, testProcess, commandLine, project, selectedEditor));
          }
        });
      }

      @Nullable
      private VirtualFile getTaskVirtualFile(@NotNull final StudyState studyState,
                                             @NotNull final Task task,
                                             @NotNull final VirtualFile taskDir) {
        VirtualFile taskVirtualFile = studyState.getVirtualFile();
        for (Map.Entry<String, TaskFile> entry : task.getTaskFiles().entrySet()) {
          String name = entry.getKey();
          TaskFile taskFile = entry.getValue();
          VirtualFile virtualFile = taskDir.findChild(name);
          if (virtualFile != null) {
            if (!taskFile.getAnswerPlaceholders().isEmpty()) {
              taskVirtualFile = virtualFile;
            }
          }
        }
        return taskVirtualFile;
      }
    });
  }

  @NotNull
  protected com.intellij.openapi.progress.Task.Backgroundable getCheckTask(final StudyState studyState,
                                                                         final StudyTestRunner testRunner,
                                                                         final Process testProcess,
                                                                         @NotNull final String commandLine, @NotNull final Project project,
                                                                         final StudyEditor selectedEditor) {
    final Task task = studyState.getTask();
    final VirtualFile taskDir = studyState.getTaskDir();

    final StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
    final StudyStatus statusBeforeCheck = taskManager.getStatus(task);
    return new com.intellij.openapi.progress.Task.Backgroundable(project, "Checking Task", true) {
      @Override
      public void onSuccess() {
        StudyUtils.updateToolWindows(project);
        drawAllPlaceholders(project, task, taskDir);
        ProjectView.getInstance(project).refresh();
        EduUtils.deleteWindowDescriptions(task, taskDir);
        checkInProgress = false;
      }

      @Override
      public void onCancel() {
        taskManager.setStatus(task, statusBeforeCheck);
        EduUtils.deleteWindowDescriptions(task, taskDir);
        checkInProgress = false;
      }

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        final Map<String, TaskFile> taskFiles = task.getTaskFiles();
        final CapturingProcessHandler handler = new CapturingProcessHandler(testProcess, null, commandLine);
        final ProcessOutput output = handler.runProcessWithProgressIndicator(indicator);
        if (indicator.isCanceled()) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              showTestResultPopUp("Tests check cancelled.", MessageType.WARNING.getPopupBackground(), project);
            }
          });
          return;
        }
        final StudyTestRunner.TestsOutput testsOutput = testRunner.getTestsOutput(output);
        String stderr = output.getStderr();
        if (!stderr.isEmpty()) {
          LOG.info("#educational " + stderr);
        }
        final StudySettings studySettings = StudySettings.getInstance();

        final String login = studySettings.getLogin();
        final String password = StringUtil.isEmptyOrSpaces(login) ? "" : studySettings.getPassword();
        if (testsOutput.isSuccess()) {
          taskManager.setStatus(task, StudyStatus.Solved);
          EduStepicConnector.postAttempt(task, true, login, password);
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              showTestResultPopUp(testsOutput.getMessage(), MessageType.INFO.getPopupBackground(), project);
            }
          });
        }
        else {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              if (taskDir == null) return;
              EduStepicConnector.postAttempt(task, false, login, password);
              taskManager.setStatus(task, StudyStatus.Failed);
              for (Map.Entry<String, TaskFile> entry : taskFiles.entrySet()) {
                final String name = entry.getKey();
                final TaskFile taskFile = entry.getValue();
                if (taskFile.getAnswerPlaceholders().size() < 2) {
                  taskManager.setStatus(taskFile, StudyStatus.Failed);
                  continue;
                }
                CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
                  @Override
                  public void run() {
                    ApplicationManager.getApplication().runWriteAction(new Runnable() {
                      @Override
                      public void run() {
                        runSmartTestProcess(taskDir, testRunner, name, taskFile, project);
                      }
                    });
                  }
                });
              }
              showTestResultPopUp(testsOutput.getMessage(), MessageType.ERROR.getPopupBackground(), project);
              navigateToFailedPlaceholder(studyState, task, taskDir, project);
            }
          });
        }
      }
    };
  }

  private static void navigateToFailedPlaceholder(@NotNull final StudyState studyState,
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
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        IdeFocusManager.getInstance(project).requestFocus(editorToNavigate.getContentComponent(), true);
      }
    });

    StudyNavigator.navigateToFirstFailedAnswerPlaceholder(editor, taskFileToNavigate);
  }

  protected void runSmartTestProcess(@NotNull final VirtualFile taskDir,
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

  private VirtualFile getCopyWithAnswers(@NotNull final VirtualFile taskDir,
                                         @NotNull final VirtualFile file,
                                         @NotNull final TaskFile source,
                                         @NotNull final TaskFile target) {
    VirtualFile copy = null;
    try {

      copy = file.copy(this, taskDir, file.getNameWithoutExtension() + ANSWERS_POSTFIX + "." + file.getExtension());
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
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            documentManager.saveDocument(document);
          }
        });
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return copy;
  }

  protected static void showTestResultPopUp(final String text, Color color, @NotNull final Project project) {
    BalloonBuilder balloonBuilder =
      JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(text, null, color, null);
    final Balloon balloon = balloonBuilder.createBalloon();
    StudyUtils.showCheckPopUp(project, balloon);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      check(project);
    }
  }

  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    StudyUtils.updateAction(e);
    if (presentation.isEnabled()) {
      presentation.setEnabled(!checkInProgress);
    }
  }
}
