// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging;

import com.intellij.execution.ExecutionException;
import com.intellij.model.SideEffectGuard;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.python.packaging.utils.PyPackagesManagerUIBridge;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class PyPackageManagerUI {
  private final @Nullable Listener myListener;
  private final @NotNull Project myProject;
  private final @NotNull Sdk mySdk;

  public interface Listener {
    void started();
    void finished(List<ExecutionException> exceptions);
  }

  public PyPackageManagerUI(@NotNull Project project, @NotNull Sdk sdk, @Nullable Listener listener) {
    myProject = project;
    mySdk = sdk;
    myListener = listener;
  }

  public void install(final @Nullable List<PyRequirement> requirements, final @NotNull List<String> extraArgs) {
    SideEffectGuard.checkSideEffectAllowed(SideEffectGuard.EffectType.EXEC);
    PyPackagesManagerUIBridge.runInstallInBackground(myProject, mySdk, requirements, extraArgs, myListener);
  }

  public void uninstall(final @NotNull List<PyPackage> packages) {
    SideEffectGuard.checkSideEffectAllowed(SideEffectGuard.EffectType.EXEC);
    PyPackagesManagerUIBridge.runUninstallInBackground(myProject, mySdk, packages, myListener);
  }
}