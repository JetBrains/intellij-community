// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.jetbrains.python.packaging;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Key;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public abstract class PyPackageManager {
  public static final Key<Boolean> RUNNING_PACKAGING_TASKS = Key.create("PyPackageRequirementsInspection.RunningPackagingTasks");

  public static final String USE_USER_SITE = "--user";
  public static final Topic<Listener> PACKAGE_MANAGER_TOPIC = Topic.create("Python package manager", Listener.class);

  @NotNull
  public static PyPackageManager getInstance(@NotNull Sdk sdk) {
    return PyPackageManagers.getInstance().forSdk(sdk);
  }

  public abstract void installManagement() throws ExecutionException;

  public abstract boolean hasManagement() throws ExecutionException;

  public abstract void install(@NotNull String requirementString) throws ExecutionException;

  public abstract void install(@NotNull List<PyRequirement> requirements, @NotNull List<String> extraArgs) throws ExecutionException;

  public abstract void uninstall(@NotNull List<PyPackage> packages) throws ExecutionException;

  public abstract void refresh();

  @NotNull
  public abstract String createVirtualEnv(@NotNull String destinationDir, boolean useGlobalSite) throws ExecutionException;

  @Nullable
  public abstract List<PyPackage> getPackages();

  @NotNull
  public abstract List<PyPackage> refreshAndGetPackages(boolean alwaysRefresh) throws ExecutionException;

  @Nullable
  public abstract List<PyRequirement> getRequirements(@NotNull Module module);

  @NotNull
  public abstract List<PyRequirement> parseRequirements(@NotNull String text);

  @NotNull
  public abstract Set<PyPackage> getDependents(@NotNull PyPackage pkg) throws ExecutionException;

  public interface Listener {
    void packagesRefreshed(@NotNull Sdk sdk);
  }
}
