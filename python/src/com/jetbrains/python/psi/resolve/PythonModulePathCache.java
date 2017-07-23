/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.psi.resolve;

import com.intellij.ProjectTopics;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.messages.MessageBusConnection;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.sdk.PythonSdkType;

/**
 * @author yole
 */
public class PythonModulePathCache extends PythonPathCache implements Disposable {
  public static PythonPathCache getInstance(Module module) {
    return ModuleServiceManager.getService(module, PythonPathCache.class);
  }

  @SuppressWarnings({"UnusedDeclaration"})
  public PythonModulePathCache(final Module module) {
    final MessageBusConnection connection = module.getMessageBus().connect();
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      public void rootsChanged(ModuleRootEvent event) {
        updateCacheForSdk(module);
        clearCache();
      }
    });
    connection.subscribe(PyPackageManager.PACKAGE_MANAGER_TOPIC, sdk -> {
      final Sdk moduleSdk = PythonSdkType.findPythonSdk(module);
      if (sdk == moduleSdk) {
        updateCacheForSdk(module);
        clearCache();
      }
    });
    VirtualFileManager.getInstance().addVirtualFileListener(new MyVirtualFileListener(), this);
    updateCacheForSdk(module);
  }

  private static void updateCacheForSdk(Module module) {
    final Sdk sdk = PythonSdkType.findPythonSdk(module);
    if (sdk != null) {
      // initialize cache for SDK
      PythonSdkPathCache.getInstance(module.getProject(), sdk);
    }
  }

  @Override
  public void dispose() {
 }
}
