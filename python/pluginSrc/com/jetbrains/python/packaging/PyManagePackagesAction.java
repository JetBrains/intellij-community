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
