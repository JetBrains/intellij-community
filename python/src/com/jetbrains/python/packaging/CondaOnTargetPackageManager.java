// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.target.TargetEnvironmentRequest;
import com.jetbrains.python.PySdkBundle;
import com.jetbrains.python.sdk.PythonSdkUtil;
import com.jetbrains.python.sdk.flavors.PyCondaRunTargetsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public final class CondaOnTargetPackageManager {
  private CondaOnTargetPackageManager() { }

  @NotNull
  public static String createCondaEnv(@NotNull TargetEnvironmentRequest targetEnvironmentRequest,
                                      @Nullable String condaExecutable,
                                      @NotNull String destinationDir,
                                      @NotNull String version) throws ExecutionException {
    if (condaExecutable == null) {
      throw new PyExecutionException(PySdkBundle.message("python.sdk.conda.dialog.cannot.find.conda"), "Conda", Collections.emptyList(),
                                     new ProcessOutput());
    }

    List<String> parameters = List.of("create", "-p", destinationDir, "-y", "python=" + version);

    PyCondaRunTargetsKt.runCondaOnTarget(targetEnvironmentRequest, condaExecutable, parameters);
    final String binary = PythonSdkUtil.getPythonExecutable(destinationDir);
    char separator = targetEnvironmentRequest.getTargetPlatform().getPlatform().fileSeparator;
    final String binaryFallback = destinationDir + separator + "bin" + separator + "python";
    return (binary != null) ? binary : binaryFallback;
  }
}
