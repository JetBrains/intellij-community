// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener;
import com.intellij.platform.backend.workspace.WorkspaceModelTopics;
import com.intellij.platform.workspace.jps.entities.ModuleEntity;
import com.intellij.platform.workspace.storage.EntityChange;
import com.intellij.platform.workspace.storage.VersionedStorageChange;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleEntityUtils;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;


public final class PythonModulePathCache extends PythonPathCache implements Disposable {
  public static PythonPathCache getInstance(Module module) {
    return module.getService(PythonPathCache.class);
  }

  @SuppressWarnings({"UnusedDeclaration"})
  public PythonModulePathCache(Module module) {
    MessageBusConnection connection = module.getProject().getMessageBus().connect(this);
    connection.subscribe(ModuleRootListener.TOPIC, new ModuleRootListener() {
      @Override
      public void rootsChanged(@NotNull ModuleRootEvent event) {
        if (event.isCausedByWorkspaceModelChangesOnly()) return;
        updateCacheForSdk(module);
        clearCache();
      }
    });
    connection.subscribe(WorkspaceModelTopics.CHANGED, new WorkspaceListener(module));
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

  private class WorkspaceListener implements WorkspaceModelChangeListener {
    private final Module myModule;

    WorkspaceListener(Module module) {
      myModule = module;
    }

    @Override
    public void changed(@NotNull VersionedStorageChange event) {
      if (!myModule.isDisposed()) {
        updateCacheForSdk(myModule);
      }
      List<EntityChange<ModuleEntity>> changes = event.getChanges(ModuleEntity.class);
      for (EntityChange<ModuleEntity> change : changes) {
        ModuleEntity entity = null;
        if (change instanceof EntityChange.Replaced) {
          entity = ((EntityChange.Replaced<ModuleEntity>)change).getOldEntity();
        }
        else if (change instanceof EntityChange.Removed) {
          entity = ((EntityChange.Removed<ModuleEntity>)change).getEntity();
        }
        if (entity != null) {
          var module = ModuleEntityUtils.findModule(entity, event.getStorageBefore());
          if (module == myModule) {
            clearCache();
            return;
          }
        }
      }
    }
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
