package com.jetbrains.python.edu.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.jetbrains.python.edu.StudyDocumentListener;
import com.jetbrains.python.edu.StudyTaskManager;
import com.jetbrains.python.edu.StudyUtils;
import com.jetbrains.python.edu.course.*;
import com.jetbrains.python.edu.editor.StudyEditor;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;

public class StudyCheckAction extends DumbAwareAction {

  private static final Logger LOG = Logger.getInstance(StudyCheckAction.class.getName());
  public static final String PYTHONPATH = "PYTHONPATH";

  static class StudyTestRunner {
    public static final String TEST_OK = "#study_plugin test OK";
    private static final String TEST_FAILED = "#study_plugin FAILED + ";
    private final Task myTask;
    private final VirtualFile myTaskDir;

    StudyTestRunner(Task task, VirtualFile taskDir) {
      myTask = task;
      myTaskDir = taskDir;
    }

    Process launchTests(Project project, String executablePath) throws ExecutionException {
      Sdk sdk = PythonSdkType.findPythonSdk(ModuleManager.getInstance(project).getModules()[0]);
      File testRunner = new File(myTaskDir.getPath(), myTask.getTestFile());
      GeneralCommandLine commandLine = new GeneralCommandLine();
      commandLine.setWorkDirectory(myTaskDir.getPath());
      final Map<String, String> env = commandLine.getEnvironment();
      final VirtualFile courseDir = project.getBaseDir();
      if (courseDir != null)
        env.put(PYTHONPATH, courseDir.getPath());
      if (sdk != null) {
        String pythonPath = sdk.getHomePath();
        if (pythonPath != null) {
          commandLine.setExePath(pythonPath);
          commandLine.addParameter(testRunner.getPath());
          final Course course = StudyTaskManager.getInstance(project).getCourse();
          assert course != null;
          commandLine.addParameter(new File(course.getResourcePath()).getParent());
          commandLine.addParameter(FileUtil.toSystemDependentName(executablePath));
          return commandLine.createProcess();
        }
      }
      return null;
    }


    String getPassedTests(Process p) {
      InputStream testOutput = p.getInputStream();
      BufferedReader testOutputReader = new BufferedReader(new InputStreamReader(testOutput));
      String line;
      try {
        while ((line = testOutputReader.readLine()) != null) {
          if (line.contains(TEST_FAILED)) {
             return line.substring(TEST_FAILED.length(), line.length());
          }
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
      finally {
        StudyUtils.closeSilently(testOutputReader);
      }
      return TEST_OK;
    }
  }

  public void check(@NotNull final Project project) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
          @Override
          public void run() {
        final Editor selectedEditor = StudyEditor.getSelectedEditor(project);
        if (selectedEditor != null) {
          final FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
          final VirtualFile openedFile = fileDocumentManager.getFile(selectedEditor.getDocument());
          if (openedFile != null) {
            StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
            final TaskFile selectedTaskFile = taskManager.getTaskFile(openedFile);
            List<VirtualFile> filesToDelete = new ArrayList<VirtualFile>();
            if (selectedTaskFile != null) {
              final VirtualFile taskDir = openedFile.getParent();
              Task currentTask = selectedTaskFile.getTask();
              StudyStatus oldStatus = currentTask.getStatus();
              Map<String, TaskFile> taskFiles = selectedTaskFile.getTask().getTaskFiles();
              for (Map.Entry<String, TaskFile> entry : taskFiles.entrySet()) {
                String name = entry.getKey();
                TaskFile taskFile = entry.getValue();
                VirtualFile virtualFile = taskDir.findChild(name);
                if (virtualFile == null) {
                  continue;
                }
                VirtualFile windowFile = StudyUtils.flushWindows(FileDocumentManager.getInstance().getDocument(virtualFile), taskFile, virtualFile);
                filesToDelete.add(windowFile);
                FileDocumentManager.getInstance().saveAllDocuments();
              }

              StudyRunAction runAction = (StudyRunAction)ActionManager.getInstance().getAction(StudyRunAction.ACTION_ID);
              if (runAction != null && currentTask.getTaskFiles().size() == 1) {
                runAction.run(project);
              }
              final StudyTestRunner testRunner = new StudyTestRunner(currentTask, taskDir);
              Process testProcess = null;
              try {
                testProcess = testRunner.launchTests(project, openedFile.getPath());
              }
              catch (ExecutionException e) {
                LOG.error(e);
              }
              if (testProcess != null) {
                String failedMessage = testRunner.getPassedTests(testProcess);
                if (failedMessage.equals(StudyTestRunner.TEST_OK)) {
                  currentTask.setStatus(StudyStatus.Solved, oldStatus);
                  StudyUtils.updateStudyToolWindow(project);
                  selectedTaskFile.drawAllWindows(selectedEditor);
                  ProjectView.getInstance(project).refresh();
                  for (VirtualFile file:filesToDelete) {
                    try {
                      file.delete(this);
                    }
                    catch (IOException e) {
                      LOG.error(e);
                    }
                  }
                  createTestResultPopUp("Congratulations!", JBColor.GREEN, project);
                  return;
                }
                for (Map.Entry<String, TaskFile> entry : taskFiles.entrySet()) {
                  String name = entry.getKey();
                  TaskFile taskFile = entry.getValue();
                  TaskFile answerTaskFile = new TaskFile();
                  VirtualFile virtualFile = taskDir.findChild(name);
                  if (virtualFile == null) {
                    continue;
                  }
                  VirtualFile answerFile = getCopyWithAnswers(taskDir, virtualFile, taskFile, answerTaskFile);
                  for (TaskWindow taskWindow : answerTaskFile.getTaskWindows()) {
                    Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
                    if (document == null) {
                      continue;
                    }
                    if (!taskWindow.isValid(document)) {
                      continue;
                    }
                    check(project, taskWindow, answerFile, answerTaskFile, taskFile, document, testRunner, virtualFile);
                  }
                  FileEditor fileEditor = FileEditorManager.getInstance(project).getSelectedEditor(virtualFile);
                  Editor editor = null;
                  if (fileEditor instanceof StudyEditor) {
                    StudyEditor studyEditor = (StudyEditor) fileEditor;
                    editor = studyEditor.getEditor();
                  }

                  if (editor != null) {
                    taskFile.drawAllWindows(editor);
                    StudyUtils.synchronize();
                  }
                  try {
                    answerFile.delete(this);
                  }
                  catch (IOException e) {
                    LOG.error(e);
                  }
                }
                for (VirtualFile file:filesToDelete) {
                  try {
                    file.delete(this);
                  }
                  catch (IOException e) {
                    LOG.error(e);
                  }
                }
                currentTask.setStatus(StudyStatus.Failed, oldStatus);
                StudyUtils.updateStudyToolWindow(project);
                createTestResultPopUp(failedMessage, JBColor.RED, project);
              }
            }
          }
        }

         }
      });
      }
    });
  }

  private void check(Project project,
                     TaskWindow taskWindow,
                     VirtualFile answerFile,
                     TaskFile answerTaskFile,
                     TaskFile usersTaskFile,
                     Document usersDocument,
                     StudyTestRunner testRunner,
                     VirtualFile openedFile) {

    try {
       VirtualFile windowCopy = answerFile.copy(this, answerFile.getParent(), answerFile.getNameWithoutExtension() + "_window" + taskWindow.getIndex() + ".py");
      final FileDocumentManager documentManager = FileDocumentManager.getInstance();
      final Document windowDocument = documentManager.getDocument(windowCopy);
      if (windowDocument != null) {
        StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
        Course course = taskManager.getCourse();
        Task task = usersTaskFile.getTask();
        int taskNum = task.getIndex() + 1;
        int lessonNum = task.getLesson().getIndex() + 1;
        assert course != null;
        String pathToResource = FileUtil.join(new File(course.getResourcePath()).getParent(), Lesson.LESSON_DIR + lessonNum,  Task.TASK_DIR + taskNum);
        File resourceFile = new File(pathToResource, windowCopy.getName());
        FileUtil.copy(new File(pathToResource, openedFile.getName()), resourceFile);
        TaskFile windowTaskFile = new TaskFile();
        TaskFile.copy(answerTaskFile, windowTaskFile);
        StudyDocumentListener listener = new StudyDocumentListener(windowTaskFile);
        windowDocument.addDocumentListener(listener);
        int start = taskWindow.getRealStartOffset(windowDocument);
        int end = start + taskWindow.getLength();
        TaskWindow userTaskWindow = usersTaskFile.getTaskWindows().get(taskWindow.getIndex());
        int userStart = userTaskWindow.getRealStartOffset(usersDocument);
        int userEnd = userStart + userTaskWindow.getLength();
        String text = usersDocument.getText(new TextRange(userStart, userEnd));
        windowDocument.replaceString(start, end, text);
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            documentManager.saveDocument(windowDocument);
          }
        });
        VirtualFile fileWindows = StudyUtils.flushWindows(windowDocument, windowTaskFile, windowCopy);
        Process smartTestProcess = testRunner.launchTests(project, windowCopy.getPath());
        boolean res = testRunner.getPassedTests(smartTestProcess).equals(StudyTestRunner.TEST_OK);
        userTaskWindow.setStatus(res ? StudyStatus.Solved : StudyStatus.Failed, StudyStatus.Unchecked);
        windowCopy.delete(this);
        fileWindows.delete(this);
        if (!resourceFile.delete()) {
          LOG.error("failed to delete", resourceFile.getPath());
        }
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
    catch (ExecutionException e) {
      LOG.error(e);
    }
  }


  private VirtualFile getCopyWithAnswers(final VirtualFile taskDir,
                                         final VirtualFile file,
                                         final TaskFile source,
                                         TaskFile target) {
    VirtualFile copy = null;
    try {

      copy = file.copy(this, taskDir, file.getNameWithoutExtension() +"_answers.py");
      final FileDocumentManager documentManager = FileDocumentManager.getInstance();
      final Document document = documentManager.getDocument(copy);
      if (document != null) {
        TaskFile.copy(source, target);
        StudyDocumentListener listener = new StudyDocumentListener(target);
        document.addDocumentListener(listener);
        for (TaskWindow taskWindow : target.getTaskWindows()) {
          if (!taskWindow.isValid(document)) {
            continue;
          }
          final int start = taskWindow.getRealStartOffset(document);
          final int end = start + taskWindow.getLength();
          final String text = taskWindow.getPossibleAnswer();
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

  private static void createTestResultPopUp(final String text, Color color, @NotNull final Project project) {
    BalloonBuilder balloonBuilder =
      JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(text, null, color, null);
    Balloon balloon = balloonBuilder.createBalloon();
    StudyEditor studyEditor = StudyEditor.getSelectedStudyEditor(project);
    assert studyEditor != null;
    JButton checkButton = studyEditor.getCheckButton();
    balloon.showInCenterOf(checkButton);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      check(project);
    }
  }
}
