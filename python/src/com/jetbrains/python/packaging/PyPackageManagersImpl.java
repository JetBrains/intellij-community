/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.packaging.ui.PyCondaManagementService;
import com.jetbrains.python.packaging.ui.PyPackageManagementService;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class PyPackageManagersImpl extends PyPackageManagers {
  private final Map<String, PyPackageManagerImpl> myInstances = new HashMap<>();

  @NotNull
  public synchronized PyPackageManager forSdk(@NotNull final Sdk sdk) {
    final String homePath = sdk.getHomePath();
    if (homePath == null) {
      return new DummyPackageManager(sdk);
    }
    PyPackageManagerImpl manager = myInstances.get(homePath);
    if (manager == null) {
      if (PythonSdkType.isRemote(sdk)) {
        manager = new PyRemotePackageManagerImpl(sdk);
      }
      else if (PyCondaPackageManagerImpl.isCondaVEnv(sdk) && PyCondaPackageService.getCondaExecutable(sdk.getHomeDirectory()) != null) {
        manager = new PyCondaPackageManagerImpl(sdk);
      }
      else {
        manager = new PyPackageManagerImpl(sdk);
      }
      if (sdkIsSetUp(sdk))
        myInstances.put(homePath, manager);
    }
    return manager;
  }

  private static boolean sdkIsSetUp(@NotNull final Sdk sdk) {
    final VirtualFile[] roots = sdk.getRootProvider().getFiles(OrderRootType.CLASSES);
    return roots.length != 0;
  }

  public PyPackageManagementService getManagementService(Project project, Sdk sdk) {
    if (PyCondaPackageManagerImpl.isCondaVEnv(sdk)) {
      return new PyCondaManagementService(project, sdk);
    }
    return new PyPackageManagementService(project, sdk);
  }

  @Override
  public void clearCache(@NotNull Sdk sdk) {
    if (myInstances.containsKey(sdk.getHomePath())) {
      myInstances.remove(sdk.getHomePath());
    }
  }

  static class DummyPackageManager extends PyPackageManager {
    private final String myName;
    private final LanguageLevel myLanguageLevel;
    private final PythonSdkFlavor myFlavor;

    public DummyPackageManager(@NotNull final Sdk sdk) {
      myName = sdk.getName();
      myLanguageLevel = PythonSdkType.getLanguageLevelForSdk(sdk);
      myFlavor = PythonSdkFlavor.getFlavor(sdk);
    }

    @Override
    public void installManagement() throws ExecutionException {
      throw new ExecutionException(getErrorMessage());
    }

    @Override
    public boolean hasManagement() throws ExecutionException {
      return false;
    }

    @NotNull
    private String getErrorMessage() {
      return "Invalid interpreter \"" + myName + "\" version: " + myLanguageLevel.toString() + " type: " + myFlavor.getName();
    }

    @Override
    public void install(@NotNull String requirementString) throws ExecutionException {
      throw new ExecutionException(getErrorMessage());
    }

    @Override
    public void install(@NotNull List<PyRequirement> requirements, @NotNull List<String> extraArgs) throws ExecutionException {
      throw new ExecutionException(getErrorMessage());
    }

    @Override
    public void uninstall(@NotNull List<PyPackage> packages) throws ExecutionException {
      throw new ExecutionException(getErrorMessage());
    }

    @Override
    public void refresh() {
    }

    @NotNull
    @Override
    public String createVirtualEnv(@NotNull String destinationDir, boolean useGlobalSite) throws ExecutionException {
      throw new ExecutionException(getErrorMessage());
    }

    @Nullable
    @Override
    public List<PyPackage> getPackages() {
      return null;
    }

    @NotNull
    @Override
    public List<PyPackage> refreshAndGetPackages(boolean alwaysRefresh) throws ExecutionException {
      throw new ExecutionException(getErrorMessage());
    }

    @Nullable
    @Override
    public List<PyRequirement> getRequirements(@NotNull Module module) {
      return null;
    }

    @NotNull
    @Override
    public Set<PyPackage> getDependents(@NotNull PyPackage pkg) throws ExecutionException {
      throw new ExecutionException(getErrorMessage());
    }
  }

}
