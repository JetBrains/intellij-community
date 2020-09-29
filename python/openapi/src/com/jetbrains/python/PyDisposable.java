// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * The service is intended to be used instead of a project as a parent disposable.
 */
@Service
public final class PyDisposable implements Disposable {

  public static @NotNull Disposable getInstance() {
    return ServiceManager.getService(PyDisposable.class);
  }

  public static @NotNull Disposable getInstance(@NotNull Project project) {
    return project.getService(PyDisposable.class);
  }

  public static @NotNull Disposable getInstance(@NotNull Module module) {
    return module.getService(PyDisposable.class);
  }

  @Override
  public void dispose() {
  }
}
