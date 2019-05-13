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
package com.jetbrains.rest;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.packaging.PyPackageUtil;
import com.jetbrains.python.sdk.PythonSdkType;

import java.util.List;

/**
 * User : catherine
 */
public class RestPythonUtil {
  private RestPythonUtil() {}

  public static Presentation updateSphinxQuickStartRequiredAction(final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();

    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project != null) {
      Module module = e.getData(LangDataKeys.MODULE);
      if (module == null) {
        Module[] modules = ModuleManager.getInstance(project).getModules();
        module = modules.length == 0 ? null : modules [0];
      }
      if (module != null) {
        final Sdk sdk = PythonSdkType.findPythonSdk(module);
        if (sdk != null) {
          final List<PyPackage> packages = PyPackageManager.getInstance(sdk).getPackages();
          final PyPackage sphinx = packages != null ? PyPackageUtil.findPackage(packages, "Sphinx") : null;
          presentation.setEnabled(sphinx != null);
        }
      }
    }
    return presentation;
  }
}
