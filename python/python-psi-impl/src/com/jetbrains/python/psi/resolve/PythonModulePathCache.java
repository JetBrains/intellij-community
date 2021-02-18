// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.resolve;

import com.intellij.ProjectTopics;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.messages.MessageBusConnection;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public final class PythonModulePathCache extends PythonPathCache implements Disposable {
  public static PythonPathCache getInstance(Module module) {
    return module.getService(PythonPathCache.class);
  }

  @SuppressWarnings({"UnusedDeclaration"})
  public PythonModulePathCache(Module module) {
    MessageBusConnection connection = module.getProject().getMessageBus().connect(this);
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(@NotNull ModuleRootEvent event) {
        updateCacheForSdk(module);
        clearCache();
      }
    });
    connection.subscribe(PyPackageManager.PACKAGE_MANAGER_TOPIC, sdk -> {
      final Sdk moduleSdk = PythonSdkUtil.findPythonSdk(module);
      if (sdk == moduleSdk) {
        updateCacheForSdk(module);
        clearCache();
      }
    });
    VirtualFileManager.getInstance().addVirtualFileListener(new MyVirtualFileListener(), this);
    updateCacheForSdk(module);
  }

  private static void updateCacheForSdk(Module module) {
    final Sdk sdk = PythonSdkUtil.findPythonSdk(module);
    if (sdk != null) {
      // initialize cache for SDK
      PythonSdkPathCache.getInstance(module.getProject(), sdk);
    }
  }

  @Override
  public void dispose() {
 }
}
