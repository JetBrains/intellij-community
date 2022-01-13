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

import com.google.common.collect.Multimap;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.util.CatchingConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.webcore.packaging.InstalledPackage;
import com.intellij.webcore.packaging.RepoPackage;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.packaging.PyCondaPackageManagerImpl;
import com.jetbrains.python.packaging.PyCondaPackageService;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.sdk.flavors.PyCondaRunKt;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
      return Collections.emptyList();
    }
    else {
      return super.getAllPackagesCached();
    }
  }

  @Override
  @NotNull
  public List<RepoPackage> getAllPackages() throws IOException {
    if (useConda()) {
      return reloadAllPackages();
    }
    else {
      return super.getAllPackages();
    }
  }

  @Override
  @NotNull
  public List<RepoPackage> reloadAllPackages() throws IOException {
    if (useConda()) {
      final Multimap<String, String> packages = PyCondaPackageService.listAllPackagesAndVersions();
      if (packages == null) return Collections.emptyList();
      final List<RepoPackage> results = new ArrayList<>();
      for (String pkg : packages.keySet()) {
        results.add(new RepoPackage(pkg, null, ContainerUtil.getFirstItem(packages.get(pkg))));
      }
      return results;
    }
    else {
      return super.reloadAllPackages();
    }
  }


  @Override
  public void fetchAllRepositories(@NotNull CatchingConsumer<? super List<String>, ? super Exception> consumer) {
    if (useConda()) {
      myExecutorService.execute(() -> {
        try {
          final List<String> channels = ContainerUtil.notNullize(PyCondaPackageService.listChannels());
          consumer.consume(channels);
        }
        catch (ExecutionException e) {
          consumer.consume(e);
        }
      });
    }
    else {
      super.fetchAllRepositories(consumer);
    }
  }

  @Override
  public void addRepository(String repositoryUrl) {
    if (useConda()) {
      ProgressManager.getInstance().run(new Task.Modal(getProject(), PyBundle.message("python.packaging.adding.conda.channel"), true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          indicator.setIndeterminate(true);
          try {
            PyCondaRunKt.runConda(mySdk, Arrays.asList("config", "--add", "channels", repositoryUrl, "--force"));
          }
          catch (ExecutionException e) {
            LOG.warn("Failed to add repository. " + e);
          }
        }
      });
    }
    else {
      super.addRepository(repositoryUrl);
    }
  }

  @Override
  public void removeRepository(String repositoryUrl) {
    if (useConda()) {
      ProgressManager.getInstance().run(new Task.Modal(getProject(), PyBundle.message("python.packaging.removing.conda.channel"), true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          indicator.setIndeterminate(true);
          try {
            PyCondaRunKt.runConda(mySdk, Arrays.asList("config", "--remove", "channels", repositoryUrl, "--force"));
          }
          catch (ExecutionException e) {
            LOG.warn("Failed to remove repository. " + e);
          }
        }
      });
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
      myExecutorService.execute(() -> {
        try {
          consumer.consume(PyCondaPackageService.listPackageVersions(packageName));
        }
        catch (ExecutionException e) {
          consumer.consume(e);
        }
      });
    }
    else {
      super.fetchPackageVersions(packageName, consumer);
    }
  }

  @Override
  public void fetchLatestVersion(@NotNull InstalledPackage pkg, @NotNull CatchingConsumer<String, Exception> consumer) {
    final String packageName = pkg.getName();
    if (useConda()) {
      myExecutorService.execute(() -> {
        try {
          final String latestVersion = ContainerUtil.getFirstItem(PyCondaPackageService.listPackageVersions(packageName));
          consumer.consume(latestVersion);
        }
        catch (ExecutionException e) {
          consumer.consume(e);
        }
      });
    }
    else {
      super.fetchLatestVersion(pkg, consumer);
    }
  }

  @Override
  public boolean shouldFetchLatestVersionsForOnlyInstalledPackages() {
    return false;
  }
}
