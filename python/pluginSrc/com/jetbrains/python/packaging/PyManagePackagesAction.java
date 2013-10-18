package com.jetbrains.python.packaging;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.python.sdk.PythonSdkType;

/**
 * @author yole
 */
public class PyManagePackagesAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Module module = e.getData(LangDataKeys.MODULE);
    final Sdk sdk = PythonSdkType.findPythonSdk(module);
    if (module != null && sdk != null) {
      new PyManagePackagesDialog(module.getProject(), sdk).show();
    }
  }

  @Override
  public void update(AnActionEvent e) {
    Module module = e.getData(LangDataKeys.MODULE);
    e.getPresentation().setEnabled(module != null && PythonSdkType.findPythonSdk(module) != null);
  }
}
