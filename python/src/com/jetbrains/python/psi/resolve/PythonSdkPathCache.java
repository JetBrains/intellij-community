/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.WeakHashMap;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author yole
 */
public class PythonSdkPathCache extends PythonPathCache implements Disposable {
  private static final Key<Map<Project, PythonSdkPathCache>> KEY = Key.create("PythonPathCache");

  public static PythonSdkPathCache getInstance(@NotNull Project project, @NotNull Sdk sdk) {
    synchronized (KEY) {
      Map<Project, PythonSdkPathCache> cacheMap = sdk.getUserData(KEY);
      if (cacheMap == null) {
        cacheMap = new WeakHashMap<>();
        sdk.putUserData(KEY, cacheMap);
      }
      PythonSdkPathCache cache = cacheMap.get(project);
      if (cache == null) {
        cache = new PythonSdkPathCache(project, sdk);
        cacheMap.put(project, cache);
      }
      return cache;
    }
  }

  private final Project myProject;
  private final Sdk mySdk;
  private final AtomicReference<PyBuiltinCache> myBuiltins = new AtomicReference<>();

  public PythonSdkPathCache(@NotNull final Project project, @NotNull final Sdk sdk) {
    myProject = project;
    mySdk = sdk;
    sdk.getRootProvider().addRootSetChangedListener(new RootProvider.RootSetChangedListener() {
      @Override
      public void rootSetChanged(RootProvider wrapper) {
        clearCache();
        if (!project.isDisposed()) {
          final Module[] modules = ModuleManager.getInstance(project).getModules();
          for (Module module : modules) {
            PythonModulePathCache.getInstance(module).clearCache();
          }
        }
        myBuiltins.set(null);
      }
    }, this);
    VirtualFileManager.getInstance().addVirtualFileListener(new MyVirtualFileAdapter(), this);
    if (!project.isDisposed()) {
      project.getMessageBus().connect(this).subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, new ProjectJdkTable.Adapter() {
        @Override
        public void jdkRemoved(Sdk jdk) {
          if (jdk == sdk) {
            Disposer.dispose(PythonSdkPathCache.this);
          }
        }
      });
      Disposer.register(project, this);
    }
  }

  @Override
  public void dispose() {
    if (mySdk != null) {
      synchronized (KEY) {
        final Map<Project, PythonSdkPathCache> cacheMap = mySdk.getUserData(KEY);
        if (cacheMap != null) {
          cacheMap.remove(myProject);
        }
      }
    }
  }

  @NotNull
  public PyBuiltinCache getBuiltins() {
    while (true) {
      PyBuiltinCache pyBuiltinCache = myBuiltins.get();
      if (pyBuiltinCache == null || !pyBuiltinCache.isValid()) {
        PyBuiltinCache newCache = new PyBuiltinCache(PyBuiltinCache.getBuiltinsForSdk(myProject, mySdk),
                                                     PyBuiltinCache.getSkeletonFile(myProject, mySdk, PyBuiltinCache.EXCEPTIONS_FILE));
        if (myBuiltins.compareAndSet(pyBuiltinCache, newCache)) {
          return newCache;
        }
      }
      else {
        return pyBuiltinCache;
      }
    }
  }

  public void clearBuiltins() {
    myBuiltins.set(null);
  }
}
