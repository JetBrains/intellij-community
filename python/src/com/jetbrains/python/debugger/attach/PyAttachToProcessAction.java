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
package com.jetbrains.python.debugger.attach;

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.SelectFromListDialog;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.internal.ProcessUtils;
import com.jetbrains.python.internal.PyProcessInfo;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author traff
 */
public class PyAttachToProcessAction extends AnAction {

  public PyAttachToProcessAction() {
    super("Attach debugger to process");
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    Module module = e.getData(LangDataKeys.MODULE);

    e.getPresentation().setEnabled(true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    Module module = e.getData(LangDataKeys.MODULE);

    Sdk sdk = PythonSdkType.findLocalCPython(module);

    final SelectFromListDialog selectDialog =
      new SelectFromListDialog(project, pythonProcessesList(), new SelectFromListDialog.ToStringAspect() {
        public String getToStirng(Object obj) {
          PyProcessInfo info = (PyProcessInfo)obj;

          return info.getPid() + " " + info.getArgs();
        }
      }, "Select Python Process", ListSelectionModel.SINGLE_SELECTION);
    if (selectDialog.showAndGet()) {
      PyProcessInfo process = (PyProcessInfo)selectDialog.getSelection()[0];

      PyAttachToProcessDebugRunner runner =
        new PyAttachToProcessDebugRunner(project, process.getPid(), sdk.getHomePath());

      try {
        runner.launch();
      }
      catch (ExecutionException e1) {
        Messages.showErrorDialog(project, e1.getMessage(), "Error Attaching Debugger");
      }
      ;
    }
  }

  private static PyProcessInfo[] pythonProcessesList() {
    PyProcessInfo[] list = ProcessUtils.getProcessList(PythonHelpersLocator.getHelpersRoot().getAbsolutePath()).getProcessList();
    return FluentIterable.from(Lists.newArrayList(list)).filter(new Predicate<PyProcessInfo>() {
      @Override
      public boolean apply(PyProcessInfo input) {
        return input.getCommand().toLowerCase().contains("python");
      }
    }).toArray(PyProcessInfo.class);
  }
}
