// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.webcore.packaging.PackageManagementService;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated replaced by {@link com.jetbrains.python.packaging.common.PackageManagerHolder }.
 * To get an instance of PythonPackageManager consider using
 * {@link com.jetbrains.python.packaging.management.PythonPackageManager.Companion#forSdk(Project, Sdk)}
 */
@Deprecated(forRemoval = true)
public abstract class PyPackageManagers implements Disposable {
  @NotNull
  public static PyPackageManagers getInstance() {
    return ApplicationManager.getApplication().getService(PyPackageManagers.class);
  }

  /**
   * @param sdk must not be disposed if {@link Disposable}
   */
  @NotNull
  public abstract PyPackageManager forSdk(@NotNull Sdk sdk);

  public abstract PackageManagementService getManagementService(Project project, Sdk sdk);

  public abstract void clearCache(@NotNull final Sdk sdk);

  @Override
  public void dispose() {
  }
}
