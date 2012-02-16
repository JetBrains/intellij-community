package com.jetbrains.python.packaging.setupPy;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.packaging.PyPackageUtil;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.run.PythonTask;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class RunSetupTaskAction extends AnAction {
  private final String myTaskName;

  public RunSetupTaskAction(String taskName, String description) {
    super(description);
    myTaskName = taskName;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Module module = e.getData(LangDataKeys.MODULE);
    if (module == null) return;
    runSetupTask(myTaskName, module);
  }

  public static void runSetupTask(String taskName, Module module) {
    final PyFile setupPy = PyPackageUtil.findSetupPy(module);
    try {
      final List<SetupTask.Option> options = SetupTaskIntrospector.getSetupTaskOptions(module, taskName);
      List<String> parameters = new ArrayList<String>();
      parameters.add(taskName);
      if (options != null) {
        SetupTaskDialog dialog = new SetupTaskDialog(module.getProject(), taskName, options);
        dialog.show();
        if (!dialog.isOK()) {
          return;
        }
        parameters.addAll(dialog.getCommandLine());
      }
      final PythonTask task = new PythonTask(module, taskName);
      final VirtualFile virtualFile = setupPy.getVirtualFile();
      task.setRunnerScript(virtualFile.getPath());
      task.setWorkingDirectory(virtualFile.getParent().getPath());
      task.setParameters(parameters);
      task.setAfterCompletion(new Runnable() {
        @Override
        public void run() {
          LocalFileSystem.getInstance().refresh(true);
        }
      });
      task.run();
    }
    catch (ExecutionException ee) {
      Messages.showErrorDialog(module.getProject(), "Failed to run task: " + ee.getMessage(), taskName);
    }
  }

  @Override
  public void update(AnActionEvent e) {
    final Module module = e.getData(LangDataKeys.MODULE);
    e.getPresentation().setVisible(module != null && PyPackageUtil.findSetupPy(module) != null);
  }
}
