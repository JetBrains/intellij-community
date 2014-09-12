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
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  public static final String USE_USER_SITE = "--user";

  public static PyPackageManager getInstance(Sdk sdk) {
    return PyPackageManagers.getInstance().forSdk(sdk);
  }

  public abstract void installManagement() throws PyExternalProcessException;
  public abstract boolean hasManagement(boolean cachedOnly);
  public abstract void install(@NotNull String requirementString) throws PyExternalProcessException;
  public abstract void install(@NotNull List<PyRequirement> requirements, @NotNull List<String> extraArgs) throws PyExternalProcessException;
  public abstract void uninstall(@NotNull List<PyPackage> packages) throws PyExternalProcessException;
  public abstract void refresh();
  @NotNull
  public abstract String createVirtualEnv(@NotNull String destinationDir, boolean useGlobalSite) throws PyExternalProcessException;
  @Nullable
  public abstract List<PyPackage> getPackages(boolean cachedOnly) throws PyExternalProcessException;
  @Nullable
  public abstract PyPackage findPackage(@NotNull String name, boolean cachedOnly) throws PyExternalProcessException;
  @Nullable
  public abstract List<PyRequirement> getRequirements(@NotNull Module module);
  @Nullable
  public abstract Set<PyPackage> getDependents(@NotNull PyPackage pkg) throws PyExternalProcessException;
}
