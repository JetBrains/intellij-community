/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.packaging.ui;

import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.util.CatchingConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.webcore.packaging.InstalledPackage;
import com.intellij.webcore.packaging.RepoPackage;
import com.jetbrains.python.packaging.PyCondaPackageCache;
import com.jetbrains.python.packaging.PyCondaPackageManagerImpl;
import com.jetbrains.python.packaging.PyCondaPackageService;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.sdk.flavors.PyCondaRunKt;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

public class PyCondaManagementService extends PyPackageManagementService {
  private static final Logger LOG = Logger.getInstance(PyCondaManagementService.class);
  public PyCondaManagementService(@NotNull final Project project, @NotNull final Sdk sdk) {
    super(project, sdk);
  }

  private boolean useConda() {
    return PyPackageManager.getInstance(mySdk) instanceof PyCondaPackageManagerImpl &&
           ((PyCondaPackageManagerImpl)PyPackageManager.getInstance(mySdk)).useConda();
  }

  @Override
  @NotNull
  public List<RepoPackage> getAllPackagesCached() {
    if (useConda()) {
      return getCachedCondaPackages();
    }
    return super.getAllPackagesCached();
  }

  @Override
  @NotNull
  public List<RepoPackage> getAllPackages() throws IOException {
    if (useConda()) {
      PyCondaPackageService.getInstance().loadAndGetPackages(false);
      return getAllPackagesCached();
    }
    return super.getAllPackages();
  }

  @Override
  @NotNull
  public List<RepoPackage> reloadAllPackages() throws IOException {
    if (useConda()) {
      PyCondaPackageService.getInstance().loadAndGetPackages(true);
      return getAllPackagesCached();
    }
    return super.reloadAllPackages();
  }

  @Override
  public List<String> getAllRepositories() {
    return useConda() ? Lists.newArrayList(PyCondaPackageService.getInstance().loadAndGetChannels()) : super.getAllRepositories();
  }

  @Override
  public void addRepository(String repositoryUrl) {
    if (useConda()) {
      try {
        PyCondaRunKt.runConda(mySdk, Lists.newArrayList("config", "--add", "channels", repositoryUrl, "--force"));
      }
      catch (ExecutionException e) {
        LOG.warn("Failed to add repository. " + e);
      }
    }
    else {
      super.addRepository(repositoryUrl);
    }
  }

  @Override
  public void removeRepository(String repositoryUrl) {
    if (useConda()) {
      try {
        PyCondaRunKt.runConda(mySdk, Lists.newArrayList("config", "--remove", "channels", repositoryUrl, "--force"));
      }
      catch (ExecutionException e) {
        LOG.warn("Failed to remove repository. " + e);
      }
    }
    else {
      super.removeRepository(repositoryUrl);
    }
  }

  @Override
  public boolean canInstallToUser() {
    return !useConda() && super.canInstallToUser();
  }

  @Override
  public void fetchPackageVersions(String packageName, CatchingConsumer<List<String>, Exception> consumer) {
    if (useConda()) {
      consumer.consume(PyCondaPackageService.getInstance().getPackageVersions(packageName));
    }
    else {
      super.fetchPackageVersions(packageName, consumer);
    }
  }

  @Override
  public void fetchLatestVersion(@NotNull InstalledPackage pkg, @NotNull CatchingConsumer<String, Exception> consumer) {
    final String packageName = pkg.getName();
    if (useConda()) {
      final String latestVersion = ContainerUtil.getFirstItem(PyCondaPackageCache.getInstance().getVersions(packageName));
      consumer.consume(latestVersion);
    }
    else {
      super.fetchLatestVersion(pkg, consumer);
    }
  }

  @NotNull
  private static List<RepoPackage> getCachedCondaPackages() {
    final PyCondaPackageCache instance = PyCondaPackageCache.getInstance();
    return ContainerUtil.map(instance.getPackageNames(), name -> {
      final String latestVersion = ContainerUtil.getFirstItem(instance.getVersions(name));
      return new RepoPackage(name, null, latestVersion);
    });
  }
}
