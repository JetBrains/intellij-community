// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.packaging.pipenv.PyPipEnvPackageManagementService;
import com.jetbrains.python.packaging.pipenv.PyPipEnvPackageManager;
import com.jetbrains.python.packaging.ui.PyCondaManagementService;
import com.jetbrains.python.packaging.ui.PyPackageManagementService;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.PythonSdkUtil;
import com.jetbrains.python.sdk.pipenv.PipenvKt;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author yole
 */
public class PyPackageManagersImpl extends PyPackageManagers {
  // TODO: Introduce a Python SDK provider EP that is capable of providing a custom package manager and a package management service

  private final Map<String, PyPackageManager> myInstances = new HashMap<>();

  @Override
  @NotNull
  public synchronized PyPackageManager forSdk(@NotNull final Sdk sdk) {
    final String key = PythonSdkType.getSdkKey(sdk);
    PyPackageManager manager = myInstances.get(key);
    if (manager == null) {
      final VirtualFile homeDirectory = sdk.getHomeDirectory();
      PyPackageManager customPackageManager = PyCustomPackageManagers.tryCreateCustomPackageManager(sdk);
      if (customPackageManager != null) {
        manager = customPackageManager;
      }
      else if (PythonSdkUtil.isRemote(sdk)) {
        manager = new PyUnsupportedPackageManager(sdk);
      }
      else if (PipenvKt.isPipEnv(sdk)) {
        manager = new PyPipEnvPackageManager(sdk);
      }
      else if (PythonSdkUtil.isConda(sdk) &&
               homeDirectory != null &&
               PyCondaPackageService.getCondaExecutable(sdk.getHomePath()) != null) {
        manager = new PyCondaPackageManagerImpl(sdk);
      }
      else {
        manager = new PyPackageManagerImpl(sdk);
      }
      myInstances.put(key, manager);
    }
    return manager;
  }

  @Override
  public PyPackageManagementService getManagementService(Project project, Sdk sdk) {
    if (PythonSdkUtil.isConda(sdk)) {
      return new PyCondaManagementService(project, sdk);
    }
    else if (PipenvKt.isPipEnv(sdk)) {
      return new PyPipEnvPackageManagementService(project, sdk);
    }
    return new PyPackageManagementService(project, sdk);
  }

  @Override
  public void clearCache(@NotNull Sdk sdk) {
    final String key = PythonSdkType.getSdkKey(sdk);
    myInstances.remove(key);
  }
}
