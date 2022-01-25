// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging.setupPy;

import com.intellij.execution.ExecutionException;
import com.intellij.ide.actions.GotoActionBase;
import com.intellij.ide.util.gotoByName.ChooseByNamePopup;
import com.intellij.ide.util.gotoByName.ChooseByNamePopupComponent;
import com.intellij.ide.util.gotoByName.ListChooseByNameModel;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.packaging.PyPackageUtil;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.run.PythonTask;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;


public class SetupTaskChooserAction extends AnAction {
  public SetupTaskChooserAction() {
    super(PyBundle.messagePointer("python.packaging.run.setup.py.task"));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Module module = e.getData(PlatformCoreDataKeys.MODULE);
    if (module == null) return;
    final Project project = module.getProject();
    final ListChooseByNameModel<SetupTask> model = new ListChooseByNameModel<>(project,
                                                                               PyBundle.message("python.packaging.enter.setup.py.task"),
                                                                               PyBundle.message("python.packaging.no.tasks.found"),
                                                                               SetupTaskIntrospector.getTaskList(module));
    final ChooseByNamePopup popup = ChooseByNamePopup.createPopup(project, model, GotoActionBase.getPsiContext(e));
    popup.setShowListForEmptyPattern(true);

    popup.invoke(new ChooseByNamePopupComponent.Callback() {

      @Override
      public void elementChosen(Object element) {
        if (element != null) {
          final SetupTask task = (SetupTask) element;
          ApplicationManager.getApplication().invokeLater(() -> runSetupTask(task.getName(), module), ModalityState.NON_MODAL);
        }
      }
    }, ModalityState.current(), false);

  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Module module = e.getData(PlatformCoreDataKeys.MODULE);
    e.getPresentation().setEnabled(module != null && PyPackageUtil.hasSetupPy(module) && PythonSdkUtil.findPythonSdk(module) != null);
  }

  public static void runSetupTask(@NlsSafe String taskName, Module module) {
    final List<SetupTask.Option> options = SetupTaskIntrospector.getSetupTaskOptions(module, taskName);
    List<String> parameters = new ArrayList<>();
    parameters.add(taskName);
    if (options != null) {
      SetupTaskDialog dialog = new SetupTaskDialog(module.getProject(), taskName, options);
      if (!dialog.showAndGet()) {
        return;
      }
      parameters.addAll(dialog.getCommandLine());
    }
    runSetupTask(taskName, module, parameters);
  }

  public static void runSetupTask(@NlsSafe String taskName, Module module, List<String> parameters) {
    try {
      final PyFile setupPy = PyPackageUtil.findSetupPy(module);
      if (setupPy == null) return;
      final PythonTask task = new PythonTask(module, taskName);
      final VirtualFile virtualFile = setupPy.getVirtualFile();
      task.setRunnerScript(virtualFile.getPath());
      task.setWorkingDirectory(virtualFile.getParent().getPath());
      task.setParameters(parameters);
      task.setAfterCompletion(() -> LocalFileSystem.getInstance().refresh(true));
      task.run(null, null);
    }
    catch (ExecutionException ee) {
      Messages.showErrorDialog(module.getProject(), PyBundle.message("python.packaging.failed.to.run.task", ee.getMessage()), taskName);
    }
  }
}