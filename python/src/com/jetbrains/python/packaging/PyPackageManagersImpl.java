// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
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
public class PyPackageManagersImpl extends PyPackageManagers implements Disposable {
  private final Map<String, PyPackageManager> myStandardManagers = new HashMap<>();
  private final Map<String, PyPackageManager> myProvidedManagers = new HashMap<>();

  public PyPackageManagersImpl() {
    PyPackageManagerProvider.EP_NAME.addExtensionPointListener(new ExtensionPointListener<PyPackageManagerProvider>() {
      @Override
      public void extensionRemoved(@NotNull PyPackageManagerProvider extension, @NotNull PluginDescriptor pluginDescriptor) {
        clearProvidedManagersCache();
      }
    }, this);
  }

  @Override
  @NotNull
  public synchronized PyPackageManager forSdk(@NotNull final Sdk sdk) {
    final String key = PythonSdkType.getSdkKey(sdk);
    PyPackageManager manager = myStandardManagers.get(key);
    if (manager == null) {
      manager = myProvidedManagers.get(key);
    }
    if (manager == null) {
      final VirtualFile homeDirectory = sdk.getHomeDirectory();
      final Map<String, PyPackageManager> cache;
      PyPackageManager customPackageManager = PyCustomPackageManagers.tryCreateCustomPackageManager(sdk);
      if (customPackageManager != null) {
        cache = myProvidedManagers;
        manager = customPackageManager;
      }
      else {
        cache = myStandardManagers;
        if (PythonSdkUtil.isRemote(sdk)) {
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
      }
      cache.put(key, manager);
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
  public synchronized void clearCache(@NotNull Sdk sdk) {
    final String key = PythonSdkType.getSdkKey(sdk);
    myStandardManagers.remove(key);
    myProvidedManagers.remove(key);
  }

  private synchronized void clearProvidedManagersCache() {
    myProvidedManagers.clear();
  }

  @Override
  public void dispose() {
    // Needed to dispose PyPackageManagerProvider EP listener
  }
}
