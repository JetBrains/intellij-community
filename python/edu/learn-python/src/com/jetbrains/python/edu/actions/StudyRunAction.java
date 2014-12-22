package com.jetbrains.python.edu.actions;

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
import com.jetbrains.python.edu.StudyResourceManger;
import com.jetbrains.python.edu.StudyTaskManager;
import com.jetbrains.python.edu.StudyUtils;
import com.jetbrains.python.edu.course.Task;
import com.jetbrains.python.edu.course.TaskFile;
import com.jetbrains.python.edu.editor.StudyEditor;
import com.jetbrains.python.run.PythonTracebackFilter;
import org.jetbrains.annotations.NotNull;

import java.io.File;

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
    StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
    if (openedFile != null && openedFile.getCanonicalPath() != null) {
      String filePath = openedFile.getCanonicalPath();
      GeneralCommandLine cmd = new GeneralCommandLine();
      cmd.withWorkDirectory(openedFile.getParent().getCanonicalPath());
        String pythonPath = sdk.getHomePath();
        if (pythonPath != null) {
          cmd.setExePath(pythonPath);
          TaskFile selectedTaskFile = taskManager.getTaskFile(openedFile);
          assert selectedTaskFile != null;
          Task currentTask = selectedTaskFile.getTask();
          if (!currentTask.getUserTests().isEmpty()) {
            cmd.addParameter(new File(project.getBaseDir().getPath(), StudyResourceManger.USER_TESTER).getPath());
            cmd.addParameter(pythonPath);
            cmd.addParameter(filePath);
            Process process;
            try {
              process = cmd.createProcess();
            }
            catch (ExecutionException e) {
              LOG.error(e);
              return;
            }
            myHandler = new OSProcessHandler(process);
            RunContentExecutor executor = new RunContentExecutor(project, myHandler).withFilter(new PythonTracebackFilter(project));
            Disposer.register(project, executor);
            executor.run();
            return;
          }
          try {
            cmd.addParameter(filePath);
            Process p = cmd.createProcess();
            myHandler = new OSProcessHandler(p);

            RunContentExecutor executor = new RunContentExecutor(project, myHandler).withFilter(new PythonTracebackFilter(project));
            Disposer.register(project, executor);
            executor.run();
          }

          catch (ExecutionException e) {
            LOG.error(e);
          }
        }
    }
  }

  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    Sdk sdk = StudyUtils.findPythonSdk(project);
    if (sdk == null) {
      return;
    }
    run(project, sdk);
  }
}
