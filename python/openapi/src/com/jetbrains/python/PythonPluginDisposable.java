// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * The service is intended to be used instead of a project/application as a parent disposable.
 */
@Service
public final class PythonPluginDisposable implements Disposable {
  public static @NotNull Disposable getInstance() {
    return ApplicationManager.getApplication().getService(PythonPluginDisposable.class);
  }

  public static @NotNull Disposable getInstance(@NotNull Project project) {
    return project.getService(PythonPluginDisposable.class);
  }

  @Override
  public void dispose() {
  }
}
