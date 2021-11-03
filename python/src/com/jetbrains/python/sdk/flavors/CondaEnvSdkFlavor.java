// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.flavors;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.sdk.PythonSdkUtil;
import com.jetbrains.python.sdk.conda.PyCondaSdkCustomizer;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemDependent;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class CondaEnvSdkFlavor extends CPythonSdkFlavor {
  private CondaEnvSdkFlavor() {
  }

  public static CondaEnvSdkFlavor getInstance() {
    return PythonSdkFlavor.EP_NAME.findExtension(CondaEnvSdkFlavor.class);
  }

  @Override
  public boolean isPlatformIndependent() {
    return true;
  }

  @NotNull
  @Override
  public Collection<String> suggestHomePaths(@Nullable Module module, @Nullable UserDataHolder context) {
    final List<String> results = new ArrayList<>();
    final Sdk sdk = ReadAction.compute(() -> PythonSdkUtil.findPythonSdk(module));
    try {
      final List<String> environments = PyCondaRunKt.listCondaEnvironments(sdk);
      for (String environment : environments) {
        results.addAll(
          ReadAction.compute(() -> VirtualEnvSdkFlavor.findInRootDirectory(StandardFileSystems.local().findFileByPath(environment)))
        );
      }
      if (!PyCondaSdkCustomizer.Companion.getInstance().getDetectBaseEnvironment()) {
        results.removeIf(path -> PythonSdkUtil.isBaseConda(path));
      }
    }
    catch (ExecutionException e) {
      return Collections.emptyList();
    }
    return results;
  }

  @Override
  public boolean isValidSdkPath(@NotNull File file) {
    if (!super.isValidSdkPath(file)) return false;
    return PythonSdkUtil.isConda(file.getPath());
  }

  @Nullable
  public static File getCondaEnvRoot(@NotNull final String binaryPath) {
    final File binary = new File(binaryPath);
    final File parent = binary.getParentFile();
    if (parent == null) return null;
    final File parent2 = parent.getParentFile();
    if (parent2 == null) return null;
    final File parent3 = parent2.getParentFile();
    if (parent3 != null && "envs".equals(parent3.getName())) {
      return parent2;
    }
    else if ("envs".equals(parent2.getName())) {
      return parent;
    }
    else {
      return null;
    }
  }

  @Override
  public @NotNull Icon getIcon() {
    return PythonIcons.Python.Anaconda;
  }

  @Nullable
  public static ValidationInfo validateCondaPath(@Nullable @SystemDependent String condaExecutable) {
    final String message;

    if (StringUtil.isEmptyOrSpaces(condaExecutable)) {
      message = PyBundle.message("python.add.sdk.conda.executable.path.is.empty");
    }
    else {
      final var file = new File(condaExecutable);

      if (!file.exists()) {
        message = PyBundle.message("python.add.sdk.conda.executable.not.found");
      }
      else if (!file.isFile() || !file.canExecute()) {
        message = PyBundle.message("python.add.sdk.conda.executable.path.is.not.executable");
      }
      else {
        message = null;
      }
    }

    return message == null ? null : new ValidationInfo(message);
  }
}
