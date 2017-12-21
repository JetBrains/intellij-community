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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
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
public class PyPackagesUpdater implements StartupActivity {
  private static final Logger LOG = Logger.getInstance(PyPackagesUpdater.class);
  private static final long EXPIRATION_TIMEOUT = DateFormatUtil.DAY;

  @Override
  public void runActivity(@NotNull final Project project) {
    final Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode()) {
      return;
    }
    if (checkNeeded(project)) {
      application.executeOnPooledThread(() -> {
        try {
          PyPIPackageUtil.INSTANCE.updatePyPICache();
        }
        catch (IOException e) {
          LOG.warn(e.getMessage());
        }
      });
    }
    if (checkCondaUpdateNeeded(project)) {
      application.executeOnPooledThread(() -> PyCondaPackageService.getInstance().updatePackagesCache());
    }
  }

  private static boolean checkCondaUpdateNeeded(Project project) {
    if (!hasPython(project)) return false;
    final long timeDelta = System.currentTimeMillis() - PyCondaPackageService.getInstance().LAST_TIME_CHECKED;
    if (Math.abs(timeDelta) < EXPIRATION_TIMEOUT) return false;
    LOG.debug("Updating outdated Conda package cache");
    return true;
  }

  private static boolean hasPython(Project project) {
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      final Sdk sdk = PythonSdkType.findPythonSdk(module);
      if (sdk != null && sdk.getSdkType() instanceof PythonSdkType) {
        return true;
      }
    }
    return false;
  }

  private static boolean checkNeeded(Project project) {
    if (!hasPython(project)) return false;
    final PyPackageService service = PyPackageService.getInstance();
    if (service.PYPI_REMOVED) return false;
    final long timeDelta = System.currentTimeMillis() - service.LAST_TIME_CHECKED;
    if (Math.abs(timeDelta) < EXPIRATION_TIMEOUT) return false;
    LOG.debug("Updating outdated PyPI package cache");
    return true;
  }
}
