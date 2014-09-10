/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.packaging;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public abstract class PyPackageManager {
  public static final Key<Boolean> RUNNING_PACKAGING_TASKS = Key.create("PyPackageRequirementsInspection.RunningPackagingTasks");

  public static final String PACKAGE_SETUPTOOLS = "setuptools";
  public static final String PACKAGE_PIP = "pip";
  public static final String PACKAGE_DISTRIBUTE = "distribute";

  // Bundled versions of package management tools
  public static final String SETUPTOOLS_VERSION = "1.1.5";
  public static final String PIP_VERSION = "1.4.1";

  public static final String SETUPTOOLS = PACKAGE_SETUPTOOLS + "-" + SETUPTOOLS_VERSION;
  public static final String PIP = PACKAGE_PIP + "-" + PIP_VERSION;

  public static final int OK = 0;
  public static final int ERROR_NO_PIP = 2;
  public static final int ERROR_NO_SETUPTOOLS = 3;
  public static final int ERROR_INVALID_SDK = -1;
  public static final int ERROR_TOOL_NOT_FOUND = -2;
  public static final int ERROR_TIMEOUT = -3;
  public static final int ERROR_INVALID_OUTPUT = -4;
  public static final int ERROR_ACCESS_DENIED = -5;
  public static final int ERROR_EXECUTION = -6;
  public static final String USE_USER_SITE = "--user";

  public static PyPackageManager getInstance(Sdk sdk) {
    return PyPackageManagers.getInstance().forSdk(sdk);
  }
  public abstract boolean hasPip();
  public abstract void installManagement(@NotNull String name) throws PyExternalProcessException;
  public abstract void install(@NotNull String requirementString) throws PyExternalProcessException;
  public abstract void install(@NotNull List<PyRequirement> requirements, @NotNull List<String> extraArgs) throws PyExternalProcessException;
  public abstract void uninstall(@NotNull List<PyPackage> packages) throws PyExternalProcessException;
  public abstract void showInstallationError(Project project, String title, String description);
  public abstract void showInstallationError(Component owner, String title, String description);
  public abstract void refresh();
  @NotNull
  public abstract String createVirtualEnv(@NotNull String destinationDir, boolean useGlobalSite) throws PyExternalProcessException;
  @Nullable
  public abstract List<PyPackage> getPackages(boolean cachedOnly) throws PyExternalProcessException;
  @Nullable
  public abstract PyPackage findInstalledPackage(String name) throws PyExternalProcessException;
  @Nullable
  public abstract PyPackage findInstalledPackage(String name, boolean cachedOnly) throws PyExternalProcessException;
  @Nullable
  public abstract List<PyRequirement> getRequirements(@NotNull Module module);
  public abstract Set<PyPackage> getDependents(@NotNull PyPackage pkg) throws PyExternalProcessException;
  @Deprecated
  public abstract boolean cacheIsNotNull();
}
