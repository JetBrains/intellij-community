package com.jetbrains.edu.learning.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunContentExecutor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.course.Task;
import com.jetbrains.edu.learning.course.TaskFile;
import com.jetbrains.edu.learning.editor.StudyEditor;
import org.jetbrains.annotations.NotNull;

public class StudyRunAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(StudyRunAction.class.getName());
  public static final String ACTION_ID = "StudyRunAction";
  private ProcessHandler myHandler;

  public void run(@NotNull final Project project, @NotNull final Sdk sdk) {
    if (myHandler != null && !myHandler.isProcessTerminated()) return;
    Editor selectedEditor = StudyEditor.getSelectedEditor(project);
    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    assert selectedEditor != null;
    VirtualFile openedFile = fileDocumentManager.getFile(selectedEditor.getDocument());

    if (openedFile != null && openedFile.getCanonicalPath() != null) {
      String filePath = openedFile.getCanonicalPath();
      executeFile(project, sdk, openedFile, filePath);
    }
  }

  private void executeFile(@NotNull final Project project, @NotNull final Sdk sdk,
                           @NotNull final VirtualFile openedFile, @NotNull final String filePath) {
    StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
    GeneralCommandLine cmd = new GeneralCommandLine();
    cmd.withWorkDirectory(openedFile.getParent().getCanonicalPath());
    String sdkHomePath = sdk.getHomePath();
    if (sdkHomePath != null) {
      cmd.setExePath(sdkHomePath);
      TaskFile selectedTaskFile = taskManager.getTaskFile(openedFile);
      assert selectedTaskFile != null;
      Task currentTask = selectedTaskFile.getTask();
      Process process;
      StudyUtils.setCommandLineParameters(cmd, project, filePath, sdkHomePath, currentTask);

      try {
        process = cmd.createProcess();
      }
      catch (ExecutionException e) {
        LOG.error(e);
        return;
      }
      myHandler = new OSProcessHandler(process);
      RunContentExecutor executor = StudyUtils.getExecutor(project, myHandler);
      Disposer.register(project, executor);
      executor.run();

    }
  }

  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    Sdk sdk = StudyUtils.findSdk(project);
    if (sdk == null) {
      return;
    }
    run(project, sdk);
  }
}
