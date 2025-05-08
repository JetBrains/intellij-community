// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.packaging.management.PythonPackageManagerService;
import com.jetbrains.python.packaging.ui.PyPackageManagementService;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
@ApiStatus.Internal

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
  public synchronized @NotNull PyPackageManager forSdk(final @NotNull Sdk sdk) {
    if (sdk instanceof Disposable) {
      LOG.assertTrue(!Disposer.isDisposed((Disposable)sdk),
                     "Requesting a package manager for an already disposed SDK " + sdk + " (" + sdk.getClass() + ")");
    }
    final String key = PythonSdkType.getSdkKey(sdk);
    PyPackageManager manager = myStandardManagers.get(key);
    if (manager == null) {
      manager = myProvidedManagers.get(key);
    }
    if (manager == null) {
      final Map<String, PyPackageManager> cache;
      PyPackageManager customPackageManager = PyCustomPackageManagers.tryCreateCustomPackageManager(sdk);
      if (customPackageManager != null) {
        cache = myProvidedManagers;
        manager = customPackageManager;
      }
      else {
        cache = myStandardManagers;
        // TODO:
        // * There should be no difference between local and "Remote" package manager
        // * But python flavor makes the difference.
        // So one must check flavor and execute appropriate command on SDK target
        // (be it localRequest or target request)

        // This is a temporary solution to support local conda
        if (PythonSdkUtil.isConda(sdk) &&
            sdk.getHomePath() != null &&
            PyCondaPackageService.getCondaExecutable(sdk.getHomePath()) != null) {
          manager = new PyCondaPackageManagerImpl(sdk);
        }
        else {
          manager = new PyTargetEnvironmentPackageManager(sdk);
        }
      }
      cache.put(key, manager);
      if (sdk instanceof Disposable) {
        Disposer.register((Disposable)sdk, () -> clearCache(sdk));
      }
      var parentDisposable = (sdk instanceof Disposable ? (Disposable)sdk : this);
      Disposer.register(parentDisposable, manager);

      if (PyPackageManager.shouldSubscribeToLocalChanges(manager)) {
        PyPackageUtil.runOnChangeUnderInterpreterPaths(sdk, manager, () -> PythonSdkType.getInstance().setupSdkPaths(sdk));
      }
    }
    return manager;
  }

  @Override
  public PyPackageManagementService getManagementService(Project project, Sdk sdk) {
    if (sdk instanceof Disposable) {
      LOG.assertTrue(!Disposer.isDisposed((Disposable)sdk),
                     "Requesting a package service for an already disposed SDK " + sdk + " (" + sdk.getClass() + ")");
    }
    return project.getService(PythonPackageManagerService.class).bridgeForSdk(project, sdk);
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
