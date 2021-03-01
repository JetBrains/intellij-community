// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.packaging.ui.PyCondaManagementService;
import com.jetbrains.python.packaging.ui.PyPackageManagementService;
import com.jetbrains.python.sdk.PySdkProvider;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author yole
 */
public class PyPackageManagersImpl extends PyPackageManagers {
  private static final Logger LOG = Logger.getInstance(PyPackageManagersImpl.class);

  private final Map<String, PyPackageManager> myStandardManagers = new HashMap<>();
  private final Map<String, PyPackageManager> myProvidedManagers = new HashMap<>();

  public PyPackageManagersImpl() {
    PyPackageManagerProvider.EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionRemoved(@NotNull PyPackageManagerProvider extension, @NotNull PluginDescriptor pluginDescriptor) {
        clearProvidedManagersCache();
      }
    }, this);
  }

  @Override
  @NotNull
  public synchronized PyPackageManager forSdk(@NotNull final Sdk sdk) {
    if (sdk instanceof Disposable) {
      LOG.assertTrue(!Disposer.isDisposed((Disposable)sdk), "Requesting a package manager for an already disposed SDK " + sdk);
    }
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
      if (sdk instanceof Disposable) {
        Disposer.register((Disposable)sdk, () -> clearCache(sdk));
      }
    }
    return manager;
  }

  @Override
  public PyPackageManagementService getManagementService(Project project, Sdk sdk) {
    Optional<PyPackageManagementService> provided = PySdkProvider.EP_NAME.extensions()
      .map(ext -> ext.tryCreatePackageManagementServiceForSdk(project, sdk))
      .filter(service -> service != null)
      .findFirst();

    if (provided.isPresent()) {
      return provided.get();
    }
    else if (PythonSdkUtil.isConda(sdk)) {
      return new PyCondaManagementService(project, sdk);
    }
    return new PyPackageManagementService(project, sdk);
  }

  @Override
  public synchronized void clearCache(@NotNull Sdk sdk) {
    String sdkKey = PythonSdkType.getSdkKey(sdk);
    removeCachedManager(myStandardManagers, sdkKey);
    removeCachedManager(myProvidedManagers, sdkKey);
  }

  private synchronized void clearProvidedManagersCache() {
    for (String key : ArrayUtil.toStringArray(myProvidedManagers.keySet())) {
      removeCachedManager(myProvidedManagers, key);
    }
  }

  private static void removeCachedManager(@NotNull Map<String, PyPackageManager> cache, @NotNull String key) {
    PyPackageManager removed = cache.remove(key);
    if (removed != null) {
      Disposer.dispose(removed);
    }
  }
}
