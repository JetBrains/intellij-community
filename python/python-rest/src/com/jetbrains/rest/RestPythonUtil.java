/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.SystemInfo;
import com.jetbrains.python.packaging.PyExternalProcessException;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.packaging.PyPackageManagerImpl;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.Nullable;

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
        Sdk sdk = PythonSdkType.findPythonSdk(module);
        if (sdk != null) {
          PyPackageManagerImpl manager = (PyPackageManagerImpl)PyPackageManager.getInstance(sdk);
          try {
            final PyPackage sphinx = manager.findPackage("Sphinx");
            String quickStart = findQuickStart(sdk);
            presentation.setEnabled(sphinx != null && quickStart != null);
          }
          catch (PyExternalProcessException ignored) {
          }
        }
      }
    }
    return presentation;
  }

  @Nullable
  public static String findQuickStart(final Sdk sdkHome) {
    final String runnerName = "sphinx-quickstart" + (SystemInfo.isWindows ? ".exe" : "");
    return PythonSdkType.getExecutablePath(sdkHome, runnerName);
  }
}
