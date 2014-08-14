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
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.edu.StudyResourceManger;
import com.jetbrains.python.edu.StudyTaskManager;
import com.jetbrains.python.edu.course.Task;
import com.jetbrains.python.edu.course.TaskFile;
import com.jetbrains.python.edu.editor.StudyEditor;

import java.io.File;

public class StudyRunAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(StudyRunAction.class.getName());
  public static final String ACTION_ID = "StudyRunAction";

  public void run(Project project) {
    Editor selectedEditor = StudyEditor.getSelectedEditor(project);
    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    assert selectedEditor != null;
    VirtualFile openedFile = fileDocumentManager.getFile(selectedEditor.getDocument());
    StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
    if (openedFile != null && openedFile.getCanonicalPath() != null) {
      String filePath = openedFile.getCanonicalPath();
      GeneralCommandLine cmd = new GeneralCommandLine();
      cmd.setWorkDirectory(openedFile.getParent().getCanonicalPath());
      Sdk sdk = PythonSdkType.findPythonSdk(ModuleManager.getInstance(project).getModules()[0]);
      if (sdk != null) {
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
            Process p;
            try {
              p = cmd.createProcess();
            }
            catch (ExecutionException e) {
              LOG.error(e);
              return;
            }
            ProcessHandler handler = new OSProcessHandler(p);

            RunContentExecutor executor = new RunContentExecutor(project, handler);
            Disposer.register(project, executor);
            executor.run();
            return;
          }
          try {
            cmd.addParameter(filePath);
            Process p = cmd.createProcess();
            ProcessHandler handler = new OSProcessHandler(p);

            RunContentExecutor executor = new RunContentExecutor(project, handler);
            Disposer.register(project, executor);
            executor.run();
          }

          catch (ExecutionException e) {
            LOG.error(e);
          }
        }
      }
    }
  }

  public void actionPerformed(AnActionEvent e) {
    run(e.getProject());
  }
}
