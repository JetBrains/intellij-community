// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.webcore.packaging.PackageManagementService;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated To get an instance of PythonPackageManager consider using
 * {@link com.jetbrains.python.packaging.management.PythonPackageManager.Companion#forSdk(Project, Sdk)}
 */
@Deprecated(forRemoval = true)
public abstract class PyPackageManagers implements Disposable {
  public static @NotNull PyPackageManagers getInstance() {
    return ApplicationManager.getApplication().getService(PyPackageManagers.class);
  }

  /**
   * @param sdk must not be disposed if {@link Disposable}
   */
  public abstract @NotNull PyPackageManager forSdk(@NotNull Sdk sdk);

  public abstract PackageManagementService getManagementService(Project project, Sdk sdk);

  public abstract void clearCache(final @NotNull Sdk sdk);

  @Override
  public void dispose() {
  }
}
