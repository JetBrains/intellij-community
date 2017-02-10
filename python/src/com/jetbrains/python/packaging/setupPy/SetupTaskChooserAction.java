/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.packaging.setupPy;

import com.intellij.execution.ExecutionException;
import com.intellij.ide.actions.GotoActionBase;
import com.intellij.ide.util.gotoByName.ChooseByNamePopup;
import com.intellij.ide.util.gotoByName.ChooseByNamePopupComponent;
import com.intellij.ide.util.gotoByName.ListChooseByNameModel;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.packaging.PyPackageUtil;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.run.PythonTask;
import com.jetbrains.python.sdk.PythonSdkType;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class SetupTaskChooserAction extends AnAction {
  public SetupTaskChooserAction() {
    super("Run setup.py Task...");
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Module module = e.getData(LangDataKeys.MODULE);
    if (module == null) return;
    final Project project = module.getProject();
    final ListChooseByNameModel<SetupTask> model = new ListChooseByNameModel<>(project, "Enter setup.py task name",
                                                                               "No tasks found",
                                                                               SetupTaskIntrospector.getTaskList(module));
    final ChooseByNamePopup popup = ChooseByNamePopup.createPopup(project, model, GotoActionBase.getPsiContext(e));
    popup.setShowListForEmptyPattern(true);

    popup.invoke(new ChooseByNamePopupComponent.Callback() {
      public void onClose() {
      }

      public void elementChosen(Object element) {
        if (element != null) {
          final SetupTask task = (SetupTask) element;
          ApplicationManager.getApplication().invokeLater(() -> runSetupTask(task.getName(), module), ModalityState.NON_MODAL);
        }
      }
    }, ModalityState.current(), false);

  }

  @Override
  public void update(AnActionEvent e) {
    final Module module = e.getData(LangDataKeys.MODULE);
    e.getPresentation().setEnabled(module != null && PyPackageUtil.hasSetupPy(module) && PythonSdkType.findPythonSdk(module) != null);
  }

  public static void runSetupTask(String taskName, Module module) {
    final PyFile setupPy = PyPackageUtil.findSetupPy(module);
    try {
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
      final PythonTask task = new PythonTask(module, taskName);
      final VirtualFile virtualFile = setupPy.getVirtualFile();
      task.setRunnerScript(virtualFile.getPath());
      task.setWorkingDirectory(virtualFile.getParent().getPath());
      task.setParameters(parameters);
      task.setAfterCompletion(() -> LocalFileSystem.getInstance().refresh(true));
      task.run(null, null);
    }
    catch (ExecutionException ee) {
      Messages.showErrorDialog(module.getProject(), "Failed to run task: " + ee.getMessage(), taskName);
    }
  }
}
