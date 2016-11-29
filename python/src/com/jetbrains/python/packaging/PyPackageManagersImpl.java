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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.packaging.ui.PyCondaManagementService;
import com.jetbrains.python.packaging.ui.PyPackageManagementService;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author yole
 */
public class PyPackageManagersImpl extends PyPackageManagers {
  private final Map<String, PyPackageManagerImpl> myInstances = new HashMap<>();

  @NotNull
  public synchronized PyPackageManager forSdk(@NotNull final Sdk sdk) {
    final String key = PythonSdkType.getSdkKey(sdk);
    PyPackageManagerImpl manager = myInstances.get(key);
    if (manager == null) {
      if (PythonSdkType.isRemote(sdk)) {
        manager = new PyRemotePackageManagerImpl(sdk);
      }
      else if (PyCondaPackageManagerImpl.isCondaVEnv(sdk) && PyCondaPackageService.getCondaExecutable(sdk.getHomeDirectory()) != null) {
        manager = new PyCondaPackageManagerImpl(sdk);
      }
      else {
        manager = new PyPackageManagerImpl(sdk);
      }
      if (sdkIsSetUp(sdk))
        myInstances.put(key, manager);
    }
    return manager;
  }

  private static boolean sdkIsSetUp(@NotNull final Sdk sdk) {
    final VirtualFile[] roots = sdk.getRootProvider().getFiles(OrderRootType.CLASSES);
    return roots.length != 0;
  }

  public PyPackageManagementService getManagementService(Project project, Sdk sdk) {
    if (PyCondaPackageManagerImpl.isCondaVEnv(sdk)) {
      return new PyCondaManagementService(project, sdk);
    }
    return new PyPackageManagementService(project, sdk);
  }

  @Override
  public void clearCache(@NotNull Sdk sdk) {
    final String key = PythonSdkType.getSdkKey(sdk);
    if (myInstances.containsKey(key)) {
      myInstances.remove(key);
    }
  }
}
