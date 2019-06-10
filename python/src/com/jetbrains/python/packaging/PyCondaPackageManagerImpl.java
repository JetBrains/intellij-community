// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.flavors.PyCondaRunKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class PyCondaPackageManagerImpl extends PyPackageManagerImpl {
  @Nullable private volatile List<PyPackage> mySideCache = null;

  public static final String PYTHON = "python";
  public boolean useConda = true;

  public boolean useConda() {
    return useConda;
  }

  public void useConda(boolean conda) {
    useConda = conda;
  }

  PyCondaPackageManagerImpl(@NotNull final Sdk sdk) {
    super(sdk);
  }

  @Override
  public void install(@Nullable List<PyRequirement> requirements, @NotNull List<String> extraArgs) throws ExecutionException {
    if (useConda) {
      if (requirements == null) return;
      final ArrayList<String> arguments = new ArrayList<>();
      for (PyRequirement requirement : requirements) {
        arguments.add(requirement.getPresentableText());
      }
      arguments.add("-y");
      if (!extraArgs.contains("-U")) {
        arguments.addAll(extraArgs);
      }
      getCondaOutput("install", arguments);
    }
    else {
      super.install(requirements, extraArgs);
    }
  }

  private ProcessOutput getCondaOutput(@NotNull final String command, List<String> arguments) throws ExecutionException {
    final Sdk sdk = getSdk();

    final String path = getCondaDirectory();
    if (path == null) throw new PyExecutionException("Empty conda name for " + sdk.getHomePath(), command, arguments);

    final ArrayList<String> parameters = Lists.newArrayList(command, "-p", path);
    parameters.addAll(arguments);

    return PyCondaRunKt.runConda(sdk, parameters);
  }

  @Nullable
  private String getCondaDirectory() {
    final VirtualFile condaDirectory = getCondaDirectory(getSdk());
    return condaDirectory == null ? null : condaDirectory.getPath();
  }

  @Nullable
  public static VirtualFile getCondaDirectory(@NotNull Sdk sdk) {
    final VirtualFile homeDirectory = sdk.getHomeDirectory();
    if (homeDirectory == null) return null;
    if (SystemInfo.isWindows) return homeDirectory.getParent();
    return homeDirectory.getParent().getParent();
  }

  @Override
  public void install(@NotNull String requirementString) throws ExecutionException {
    if (useConda) {
      super.install(requirementString);
    }
    else {
      getCondaOutput("install", Lists.newArrayList(requirementString, "-y"));
    }
  }

  @Override
  public void uninstall(@NotNull List<PyPackage> packages) throws ExecutionException {
    if (useConda) {
      final ArrayList<String> arguments = new ArrayList<>();
      for (PyPackage aPackage : packages) {
        arguments.add(aPackage.getName());
      }
      arguments.add("-y");

      getCondaOutput("remove", arguments);
    }
    else {
      super.uninstall(packages);
    }
  }

  /**
   * @return packages installed using 'conda' manager only.
   * Use 'useConda' flag to retrieve 'pip' packages
   */
  @NotNull
  @Override
  protected List<PyPackage> collectPackages() throws ExecutionException {
    final List<PyPackage> pipPackages = super.collectPackages();
    final ProcessOutput output = getCondaOutput("list", Lists.newArrayList("-e"));
    final Set<PyPackage> condaPackages = Sets.newConcurrentHashSet(parseCondaToolOutput(output.getStdout()));

    if (useConda) {
      mySideCache = pipPackages;
      return Lists.newArrayList(condaPackages);
    }
    else {
      mySideCache = Lists.newArrayList(condaPackages);
      return super.collectPackages();
    }
  }

  @Override
  public boolean hasManagement() throws ExecutionException {
    return useConda || super.hasManagement();
  }

  @NotNull
  private List<PyPackage> parseCondaToolOutput(@NotNull String s) throws ExecutionException {
    final String[] lines = StringUtil.splitByLines(s);
    final List<PyPackage> packages = new ArrayList<>();
    for (String line : lines) {
      if (line.startsWith("#")) continue;
      final List<String> fields = StringUtil.split(line, "=");
      if (fields.size() < 3) {
        throw new PyExecutionException("Invalid conda output format", "conda", Collections.emptyList());
      }
      final String name = fields.get(0);
      final String version = fields.get(1);
      final List<PyRequirement> requirements = new ArrayList<>();
      if (fields.size() >= 4) {
        final String requiresLine = fields.get(3);
        final String requiresSpec = StringUtil.join(StringUtil.split(requiresLine, ":"), "\n");
        requirements.addAll(parseRequirements(requiresSpec));
      }
      if (!"Python".equals(name)) {
        packages.add(new PyPackage(name, version, "", requirements));
      }
    }
    return packages;
  }

  public static boolean isCondaVirtualEnv(@NotNull Sdk sdk) {
    return isCondaVirtualEnv(sdk.getHomePath());
  }

  public static boolean isCondaVirtualEnv(@Nullable String sdkPath) {
    final VirtualFile condaMeta = findCondaMeta(sdkPath);
    if (condaMeta == null) {
      return false;
    }
    final VirtualFile envs = condaMeta.getParent().findChild("envs");
    return envs == null;
  }

  // Conda virtual environment and system conda
  public static boolean isConda(@NotNull Sdk sdk) {
    return isConda(sdk.getHomePath());
  }

  public static boolean isConda(@Nullable String sdkPath) {
    return findCondaMeta(sdkPath) != null;
  }

  @Nullable
  private static VirtualFile findCondaMeta(@Nullable String sdkPath) {
    if (sdkPath == null) {
      return null;
    }
    final VirtualFile homeDirectory = StandardFileSystems.local().findFileByPath(sdkPath);
    if (homeDirectory == null) {
      return null;
    }
    final VirtualFile condaParent = SystemInfo.isWindows ? homeDirectory.getParent()
                                                           : homeDirectory.getParent().getParent();
    return condaParent.findChild("conda-meta");
  }

  @NotNull
  public static String createVirtualEnv(@Nullable String condaExecutable, @NotNull String destinationDir,
                                        @NotNull String version) throws ExecutionException {
    if (condaExecutable == null) throw new PyExecutionException("Cannot find conda", "Conda", Collections.emptyList(), new ProcessOutput());

    final ArrayList<String> parameters = Lists.newArrayList("create", "-p", destinationDir, "-y", "python=" + version);

    PyCondaRunKt.runConda(condaExecutable, parameters);
    final String binary = PythonSdkType.getPythonExecutable(destinationDir);
    final String binaryFallback = destinationDir + File.separator + "bin" + File.separator + "python";
    return (binary != null) ? binary : binaryFallback;
  }

  @Nullable
  @Override
  public List<PyPackage> getPackages() {
    final List<PyPackage> packagesCache = mySideCache;
    if (packagesCache == null) return null;
    final List<PyPackage> packages = Lists.newArrayList(packagesCache);
    final List<PyPackage> condaPackages = super.getPackages();
    if (condaPackages == null) return null;
    packages.addAll(condaPackages);
    return Collections.unmodifiableList(packages);
  }
}
