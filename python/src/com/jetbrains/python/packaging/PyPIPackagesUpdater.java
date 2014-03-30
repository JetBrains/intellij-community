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
package com.jetbrains.python.packaging;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.util.text.DateFormatUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * PyPI cache updater
 * User : catherine
 */
public class PyPIPackagesUpdater implements StartupActivity {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.packaging.PyPIPackagesUpdater");

  public static PyPIPackagesUpdater getInstance() {
    final StartupActivity[] extensions = Extensions.getExtensions(StartupActivity.POST_STARTUP_ACTIVITY);
    for (StartupActivity extension : extensions) {
      if (extension instanceof PyPIPackagesUpdater) {
        return (PyPIPackagesUpdater) extension;
      }
    }
    throw new UnsupportedOperationException("could not find self");
  }

  @Override
  public void runActivity(@NotNull final Project project) {
    final Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode()) {
      return;
    }
    final PyPackageService service = PyPackageService.getInstance();
    if (checkNeeded(project, service)) {
      application.executeOnPooledThread(new Runnable() {
        @Override
        public void run() {
          try {
            PyPIPackageUtil.INSTANCE.updatePyPICache(service);
            service.LAST_TIME_CHECKED = System.currentTimeMillis();
          }
          catch (IOException e) {
            LOG.warn(e.getMessage());
          }
        }
      });
    }
  }


  public static boolean checkNeeded(Project project, PyPackageService service) {
    boolean hasPython = false;
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      final Sdk sdk = PythonSdkType.findPythonSdk(module);
      if (sdk != null && sdk.getSdkType() instanceof PythonSdkType) {
        hasPython = true;
        break;
      }
    }
    if (!hasPython) return false;
    final long timeDelta = System.currentTimeMillis() - service.LAST_TIME_CHECKED;
    if (Math.abs(timeDelta) < DateFormatUtil.DAY) return false;
    return true;
  }
}
