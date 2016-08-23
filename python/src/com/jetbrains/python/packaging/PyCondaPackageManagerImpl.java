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
package com.jetbrains.python.packaging;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunCanceledByUserException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class PyCondaPackageManagerImpl extends PyPackageManagerImpl {
  public static final String PYTHON = "python";

  PyCondaPackageManagerImpl(@NotNull final Sdk sdk) {
    super(sdk);
  }

  @Override
  public void installManagement() throws ExecutionException {
  }

  @Override
  public boolean hasManagement() throws ExecutionException {
    final Sdk sdk = getSdk();
    return isCondaVEnv(sdk);
  }

  @Override
  protected void installManagement(@NotNull String name) throws ExecutionException {
  }

  @Override
  public void install(@NotNull List<PyRequirement> requirements, @NotNull List<String> extraArgs) throws ExecutionException {
    final ArrayList<String> arguments = new ArrayList<>();
    for (PyRequirement requirement : requirements) {
      arguments.add(requirement.toString());
    }
    arguments.add("-y");
    if (extraArgs.contains("-U")) {
      getCondaOutput("update", arguments);
    }
    else {
      arguments.addAll(extraArgs);
      getCondaOutput("install", arguments);
    }
  }

  private ProcessOutput getCondaOutput(@NotNull final String command, List<String> arguments) throws ExecutionException {
    final Sdk sdk = getSdk();

    final String condaExecutable = PyCondaPackageService.getCondaExecutable(sdk.getHomeDirectory());
    if (condaExecutable == null) throw new PyExecutionException("Cannot find conda", "Conda", Collections.<String>emptyList(), new ProcessOutput());

    final String path = getCondaDirectory();
    if (path == null) throw new PyExecutionException("Empty conda name for " + sdk.getHomePath(), command, arguments);

    final ArrayList<String> parameters = Lists.newArrayList(condaExecutable, command, "-p", path);
    parameters.addAll(arguments);

    final GeneralCommandLine commandLine = new GeneralCommandLine(parameters);
    final CapturingProcessHandler handler = new CapturingProcessHandler(commandLine);
    final ProcessOutput result = handler.runProcess();
    final int exitCode = result.getExitCode();
    if (exitCode != 0) {
      final String message = StringUtil.isEmptyOrSpaces(result.getStdout()) && StringUtil.isEmptyOrSpaces(result.getStderr()) ?
                             "Permission denied" : "Non-zero exit code";
      throw new PyExecutionException(message, "Conda", parameters, result);
    }
    return result;
  }

  @Nullable
  private String getCondaDirectory() {
    final VirtualFile homeDirectory = getSdk().getHomeDirectory();
    if (homeDirectory == null) return null;
    if (SystemInfo.isWindows) return homeDirectory.getParent().getPath();
    return homeDirectory.getParent().getParent().getPath();
  }

  @Override
  public void install(@NotNull String requirementString) throws ExecutionException {
    getCondaOutput("install", Lists.newArrayList(requirementString, "-y"));
  }

  @Override
  public void uninstall(@NotNull List<PyPackage> packages) throws ExecutionException {
    final ArrayList<String> arguments = new ArrayList<>();
    for (PyPackage aPackage : packages) {
      arguments.add(aPackage.getName());
    }
    arguments.add("-y");

    getCondaOutput("remove", arguments);
  }

  @NotNull
  @Override
  protected List<PyPackage> collectPackages() throws ExecutionException {
    final ProcessOutput output = getCondaOutput("list", Lists.newArrayList("-e"));
    final Set<PyPackage> packages = Sets.newConcurrentHashSet(parseCondaToolOutput(output.getStdout()));
    packages.addAll(super.collectPackages());
    return Lists.newArrayList(packages);
  }

  @NotNull
  protected static List<PyPackage> parseCondaToolOutput(@NotNull String s) throws ExecutionException {
    final String[] lines = StringUtil.splitByLines(s);
    final List<PyPackage> packages = new ArrayList<>();
    for (String line : lines) {
      if (line.startsWith("#")) continue;
      final List<String> fields = StringUtil.split(line, "=");
      if (fields.size() < 3) {
        throw new PyExecutionException("Invalid conda output format", "conda", Collections.<String>emptyList());
      }
      final String name = fields.get(0);
      final String version = fields.get(1);
      final List<PyRequirement> requirements = new ArrayList<>();
      if (fields.size() >= 4) {
        final String requiresLine = fields.get(3);
        final String requiresSpec = StringUtil.join(StringUtil.split(requiresLine, ":"), "\n");
        requirements.addAll(PyRequirement.fromText(requiresSpec));
      }
      if (!"Python".equals(name)) {
        packages.add(new PyPackage(name, version, "", requirements));
      }
    }
    return packages;
  }

  public static boolean isCondaVEnv(@NotNull final Sdk sdk) {
    final String condaName = "conda-meta";
    final VirtualFile homeDirectory = sdk.getHomeDirectory();
    if (homeDirectory == null) return false;
    final VirtualFile condaMeta = SystemInfo.isWindows ? homeDirectory.getParent().findChild(condaName) :
                                        homeDirectory.getParent().getParent().findChild(condaName);
    return condaMeta != null;
  }

  @NotNull
  public static String createVirtualEnv(@NotNull String destinationDir, String version) throws ExecutionException {
    final String condaExecutable = PyCondaPackageService.getSystemCondaExecutable();
    if (condaExecutable == null) throw new PyExecutionException("Cannot find conda", "Conda", Collections.<String>emptyList(), new ProcessOutput());

    final ArrayList<String> parameters = Lists.newArrayList(condaExecutable, "create", "-p", destinationDir,
                                                            "python=" + version, "-y");

    final GeneralCommandLine commandLine = new GeneralCommandLine(parameters);
    final CapturingProcessHandler handler = new CapturingProcessHandler(commandLine);
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    final ProcessOutput result = handler.runProcessWithProgressIndicator(indicator);
    if (result.isCancelled()) {
      throw new RunCanceledByUserException();
    }
    final int exitCode = result.getExitCode();
    if (exitCode != 0) {
      final String message = StringUtil.isEmptyOrSpaces(result.getStdout()) && StringUtil.isEmptyOrSpaces(result.getStderr()) ?
                             "Permission denied" : "Non-zero exit code";
      throw new PyExecutionException(message, "Conda", parameters, result);
    }
    final String binary = PythonSdkType.getPythonExecutable(destinationDir);
    final String binaryFallback = destinationDir + File.separator + "bin" + File.separator + "python";
    return (binary != null) ? binary : binaryFallback;
  }

}
