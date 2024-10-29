// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging;

import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.PySdkBundle;
import com.jetbrains.python.sdk.PythonSdkUtil;
import com.jetbrains.python.sdk.VirtualEnvReader;
import com.jetbrains.python.sdk.flavors.PyCondaRunKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

public class PyCondaPackageManagerImpl extends PyPackageManagerImpl {
  private volatile @Nullable List<PyPackage> mySideCache = null;

  public static final String PYTHON = "python";
  public boolean useConda = true;

  public boolean useConda() {
    return useConda;
  }

  public void useConda(boolean conda) {
    useConda = conda;
  }

  PyCondaPackageManagerImpl(final @NotNull Sdk sdk) {
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

  private ProcessOutput getCondaOutput(final @NotNull String command, List<String> arguments) throws ExecutionException {
    final Sdk sdk = getSdk();

    final String path = getCondaDirectory();
    if (path == null) throw new PyExecutionException(PySdkBundle.message("python.sdk.conda.dialog.empty.conda.name", sdk.getHomePath()), command, arguments);

    final ArrayList<String> parameters = Lists.newArrayList(command, "-p", path);
    parameters.addAll(arguments);

    return PyCondaRunKt.runConda(sdk, parameters);
  }

  private @Nullable String getCondaDirectory() {
    final VirtualFile condaDirectory = PythonSdkUtil.getCondaDirectory(getSdk());
    return condaDirectory == null ? null : condaDirectory.getPath();
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
  @Override
  protected @NotNull List<PyPackage> collectPackages() throws ExecutionException {
    final List<PyPackage> pipPackages = super.collectPackages();
    final ProcessOutput output = getCondaOutput("list", List.of("-e"));
    List<PyPackage> condaPackages = parseCondaToolOutput(output.getStdout());

    if (useConda) {
      mySideCache = pipPackages;
      return condaPackages;
    }
    else {
      mySideCache = condaPackages;
      return super.collectPackages();
    }
  }

  @Override
  public boolean hasManagement() throws ExecutionException {
    return useConda || super.hasManagement();
  }

  private @NotNull List<PyPackage> parseCondaToolOutput(@NotNull String output) throws PyExecutionException {
    Set<PyPackage> packages = new HashSet<>();
    for (String line : StringUtil.splitByLines(output)) {
      if (line.startsWith("#")) continue;

      PyPackage pkg = parsePackaging(line,
                                     "=",
                                     false,
                                     PySdkBundle.message("python.sdk.conda.dialog.invalid.conda.output.format"),
                                     "conda");
      if (pkg != null) {
        packages.add(pkg);
      }
    }
    return new ArrayList<>(packages);
  }

  public static @NotNull String createVirtualEnv(@Nullable String condaExecutable, @NotNull String destinationDir,
                                                 @NotNull String version) throws ExecutionException {
    if (condaExecutable == null) {
      throw new PyExecutionException(PySdkBundle.message("python.sdk.conda.dialog.cannot.find.conda"), "Conda", Collections.emptyList(),
                                     new ProcessOutput());
    }

    final ArrayList<String> parameters = Lists.newArrayList("create", "-p", destinationDir, "-y", "python=" + version);

    PyCondaRunKt.runConda(condaExecutable, parameters);
    final Path binary =  VirtualEnvReader.getInstance().findPythonInPythonRoot(Path.of(destinationDir));
    final String binaryFallback = destinationDir + File.separator + "bin" + File.separator + "python";
    return (binary != null) ? binary.toString() : binaryFallback;
  }

  @Override
  public @Nullable List<PyPackage> getPackages() {
    final List<PyPackage> packagesCache = mySideCache;
    if (packagesCache == null) return null;
    final List<PyPackage> packages = Lists.newArrayList(packagesCache);
    final List<PyPackage> condaPackages = super.getPackages();
    if (condaPackages == null) return null;
    packages.addAll(condaPackages);
    return Collections.unmodifiableList(packages);
  }
}
