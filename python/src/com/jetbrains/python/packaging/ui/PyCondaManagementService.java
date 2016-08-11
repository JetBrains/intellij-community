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
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.CatchingConsumer;
import com.intellij.webcore.packaging.PackageVersionComparator;
import com.intellij.webcore.packaging.RepoPackage;
import com.jetbrains.python.packaging.PyCondaPackageService;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PyCondaManagementService extends PyPackageManagementService {
  private static final Logger LOG = Logger.getInstance(PyCondaManagementService.class);
  public PyCondaManagementService(@NotNull final Project project, @NotNull final Sdk sdk) {
    super(project, sdk);
  }

  @Override
  @NotNull
  public List<RepoPackage> getAllPackagesCached() {
    return versionMapToPackageList(PyCondaPackageService.getInstance().getCondaPackages());
  }

  @Override
  @NotNull
  public List<RepoPackage> getAllPackages() {
    return versionMapToPackageList(PyCondaPackageService.getInstance().loadAndGetPackages());
  }

  @Override
  @NotNull
  public List<RepoPackage> reloadAllPackages() {
    return getAllPackages();
  }

  @Override
  public List<String> getAllRepositories() {
    List<String> result = new ArrayList<>();
    result.addAll(PyCondaPackageService.getInstance().loadAndGetChannels());
    return result;
  }

  @Override
  public void addRepository(String repositoryUrl) {
    final String conda = PyCondaPackageService.getCondaExecutable(mySdk.getHomeDirectory());
    final ArrayList<String> parameters = Lists.newArrayList(conda, "config", "--add", "channels",  repositoryUrl, "--force");
    final GeneralCommandLine commandLine = new GeneralCommandLine(parameters);

    try {
      final CapturingProcessHandler handler = new CapturingProcessHandler(commandLine);
      final ProcessOutput result = handler.runProcess();
      final int exitCode = result.getExitCode();
      if (exitCode != 0) {
        final String message = StringUtil.isEmptyOrSpaces(result.getStdout()) && StringUtil.isEmptyOrSpaces(result.getStderr()) ?
                               "Permission denied" : "Non-zero exit code";
        LOG.warn("Failed to add repository " + message);
      }
      PyCondaPackageService.getInstance().addChannel(repositoryUrl);
    }
    catch (ExecutionException e) {
      LOG.warn("Failed to add repository");
    }

  }

  @Override
  public void removeRepository(String repositoryUrl) {
    final String conda = PyCondaPackageService.getCondaExecutable(mySdk.getHomeDirectory());
    final ArrayList<String> parameters = Lists.newArrayList(conda, "config", "--remove", "channels", repositoryUrl, "--force");
    final GeneralCommandLine commandLine = new GeneralCommandLine(parameters);

    try {
      final CapturingProcessHandler handler = new CapturingProcessHandler(commandLine);
      final ProcessOutput result = handler.runProcess();
      final int exitCode = result.getExitCode();
      if (exitCode != 0) {
        final String message = StringUtil.isEmptyOrSpaces(result.getStdout()) && StringUtil.isEmptyOrSpaces(result.getStderr()) ?
                               "Permission denied" : "Non-zero exit code";
        LOG.warn("Failed to remove repository " + message);
      }
      PyCondaPackageService.getInstance().removeChannel(repositoryUrl);
    }
    catch (ExecutionException e) {
      LOG.warn("Failed to remove repository");
    }
  }

  @Override
  public boolean canInstallToUser() {
    return false;
  }

  @Override
  public void fetchPackageVersions(String packageName, CatchingConsumer<List<String>, Exception> consumer) {
    final List<String> versions = PyCondaPackageService.getInstance().getPackageVersions(packageName);
    Collections.sort(versions, Collections.reverseOrder(new PackageVersionComparator()));
    consumer.consume(versions);
  }

}
