package com.jetbrains.edu.learning.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunContentExecutor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.Task;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.courseFormat.UserTest;
import com.jetbrains.edu.learning.editor.StudyEditor;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;

public class StudyRunAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(StudyRunAction.class.getName());
  public static final String ACTION_ID = "StudyRunAction";
  private ProcessHandler myHandler;
  private List<ProcessListener> myProcessListeners = new LinkedList<>();

  public StudyRunAction() {
    super("Run File With Tests", "Run your code with tests", AllIcons.General.Run);
  }

  public void run(@NotNull final Project project) {
    if (myHandler != null && !myHandler.isProcessTerminated()) return;
    Editor selectedEditor = StudyUtils.getSelectedEditor(project);
    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    assert selectedEditor != null;
    VirtualFile openedFile = fileDocumentManager.getFile(selectedEditor.getDocument());

    if (openedFile != null && openedFile.getCanonicalPath() != null) {
      String filePath = openedFile.getCanonicalPath();
      executeFile(project, openedFile, filePath);
    }
  }

  private void executeFile(@NotNull final Project project,
                           @NotNull final VirtualFile openedFile, @NotNull final String filePath) {
    GeneralCommandLine cmd = new GeneralCommandLine();
    cmd.withWorkDirectory(openedFile.getParent().getCanonicalPath());

    TaskFile selectedTaskFile = StudyUtils.getTaskFile(project, openedFile);
    assert selectedTaskFile != null;
    final Task currentTask = selectedTaskFile.getTask();
    final Sdk sdk = StudyUtils.findSdk(currentTask, project);
    if (sdk == null) {
      StudyUtils.showNoSdkNotification(currentTask, project);
      return;
    }
    String sdkHomePath = sdk.getHomePath();
    if (sdkHomePath != null) {
      cmd.setExePath(sdkHomePath);
      StudyUtils.setCommandLineParameters(cmd, project, filePath, sdkHomePath, currentTask);

      try {
        myHandler = new OSProcessHandler(cmd);
      }
      catch (ExecutionException e) {
        LOG.error(e);
        return;
      }

      for (ProcessListener processListener : myProcessListeners) {
        myHandler.addProcessListener(processListener);
      }

      final RunContentExecutor executor = StudyUtils.getExecutor(project, currentTask, myHandler);
      if (executor != null) {
        Disposer.register(project, executor);
        executor.run();
      }
      EduUtils.synchronize();
    }
  }

  public void addProcessListener(@NotNull final ProcessListener processListener) {
    myProcessListeners.add(processListener);
  }

  public void removeProcessListener(@NotNull final ProcessListener processListener) {
    myProcessListeners.remove(processListener);
  }

  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project != null) {
      run(project);
    }
  }

  @Override
  public void update(AnActionEvent e) {
    EduUtils.enableAction(e, false);

    final Project project = e.getProject();
    if (project != null) {
      StudyEditor studyEditor = StudyUtils.getSelectedStudyEditor(project);
      if (studyEditor != null) {
        final List<UserTest> userTests = StudyTaskManager.getInstance(project).getUserTests(studyEditor.getTaskFile().getTask());
        if (!userTests.isEmpty()) {
          EduUtils.enableAction(e, true);
        }
      }
    }
  }
}
