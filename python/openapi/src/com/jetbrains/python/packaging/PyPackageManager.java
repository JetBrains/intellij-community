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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * @author yole
 */
public abstract class PyPackageManager {
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
  @Nullable
  public abstract PyPackage findInstalledPackage(String name) throws PyExternalProcessException;
  @Nullable
  public abstract PyPackage findInstalledPackage(String name, boolean cachedOnly) throws PyExternalProcessException;
}
