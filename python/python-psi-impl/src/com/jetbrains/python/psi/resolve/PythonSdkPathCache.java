// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.python.sdk.backend.PythonInterpreter;
import com.intellij.util.messages.MessageBusConnection;
import com.jetbrains.python.PythonPluginDisposable;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The resolve cache of the roots of one interpreter in one project, and its {@link PyBuiltinCache}. The project keeps one
 * per interpreter, see {@link #getInstance(Project, PythonInterpreter)}.
 */
public class PythonSdkPathCache extends PythonPathCache implements Disposable {
  private static final Key<Map<PythonInterpreter, PythonSdkPathCache>> KEY = Key.create("PythonPathCache");

  public static PythonSdkPathCache getInstance(@NotNull Project project, @NotNull PythonInterpreter interpreter) {
    synchronized (KEY) {
      Map<PythonInterpreter, PythonSdkPathCache> cacheMap = project.getUserData(KEY);
      if (cacheMap == null) {
        cacheMap = new HashMap<>();
        project.putUserData(KEY, cacheMap);
      }
      PythonSdkPathCache cache = cacheMap.get(interpreter);
      if (cache == null) {
        cache = new PythonSdkPathCache(project, interpreter);
        cacheMap.put(interpreter, cache);
      }
      return cache;
    }
  }

  private final Project myProject;
  private final PythonInterpreter myInterpreter;
  private final AtomicReference<PyBuiltinCache> myBuiltins = new AtomicReference<>();

  private PythonSdkPathCache(final @NotNull Project project, final @NotNull PythonInterpreter interpreter) {
    myProject = project;
    myInterpreter = interpreter;
    if (project.isDisposed()) {
      return;
    }
    Disposer.register(PythonPluginDisposable.getInstance(project), this);
    // The updater still writes the roots and the packages of an interpreter into its SDK.
    final Sdk sdk = MainPythonSdkKt.resolveSdk(interpreter);
    final MessageBusConnection connection = project.getMessageBus().connect(this);
    connection.subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, new ProjectJdkTable.Listener() {
      @Override
      public void jdkRemoved(@NotNull Sdk jdk) {
        if (jdk == sdk) {
          Disposer.dispose(PythonSdkPathCache.this);
        }
      }
    });
    connection.subscribe(PyPackageManager.PACKAGE_MANAGER_TOPIC, eventSdk -> {
      if (eventSdk == sdk) {
        clearCache();
      }
    });
    sdk.getRootProvider().addRootSetChangedListener(wrapper -> onRootsChanged(), this);
    VirtualFileManager.getInstance().addVirtualFileListener(new MyVirtualFileListener(), this);
  }

  /**
   * Drops what was resolved against the old roots of the interpreters of {@code project}, when
   * {@link com.intellij.python.sdk.backend.PythonInterpreterDetails} reports a change of them.
   */
  static void onInterpreterDetailsChanged(@NotNull Project project) {
    final List<PythonSdkPathCache> caches;
    synchronized (KEY) {
      final Map<PythonInterpreter, PythonSdkPathCache> cacheMap = project.getUserData(KEY);
      caches = cacheMap != null ? new ArrayList<>(cacheMap.values()) : List.of();
    }
    for (PythonSdkPathCache cache : caches) cache.onRootsChanged();
  }

  /** Drops what was resolved against the old roots of the interpreter. */
  private void onRootsChanged() {
    clearCache();
    if (!myProject.isDisposed()) {
      final Module[] modules = ModuleManager.getInstance(myProject).getModules();
      for (Module module : modules) {
        PythonModulePathCache.getInstance(module).clearCache();
      }
    }
    myBuiltins.set(null);
  }

  @Override
  public void dispose() {
    synchronized (KEY) {
      final Map<PythonInterpreter, PythonSdkPathCache> cacheMap = myProject.getUserData(KEY);
      if (cacheMap != null) {
        cacheMap.remove(myInterpreter, this);
      }
    }
  }

  public @NotNull PyBuiltinCache getBuiltins() {
    while (true) {
      PyBuiltinCache pyBuiltinCache = myBuiltins.get();
      if (pyBuiltinCache == null || !pyBuiltinCache.isValid()) {
        PyBuiltinCache newCache = new PyBuiltinCache(myProject, myInterpreter);
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
