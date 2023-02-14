// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.flavors.conda;

import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.execution.target.readableFs.PathInfo;
import com.intellij.execution.target.readableFs.TargetConfigurationReadableFs;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.UserDataHolder;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.sdk.PythonSdkUtil;
import com.jetbrains.python.sdk.add.target.PathValidatorKt;
import com.jetbrains.python.sdk.add.target.ValidationRequest;
import com.jetbrains.python.sdk.flavors.CPythonSdkFlavor;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemDependent;

import javax.swing.*;
import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;

/**
 * Conda doesn't use {@link Sdk#getHomePath()}, see {@link PyCondaFlavorData}
 */
public final class CondaEnvSdkFlavor extends CPythonSdkFlavor<PyCondaFlavorData> {
  private CondaEnvSdkFlavor() {
  }

  public static CondaEnvSdkFlavor getInstance() {
    return PythonSdkFlavor.EP_NAME.findExtension(CondaEnvSdkFlavor.class);
  }


  @Override
  public boolean providePyCharmHosted() {
    // Conda + Colorama doesn't play well with this var, see DS-4036
    return false;
  }

  @Override
  public boolean isPlatformIndependent() {
    return true;
  }

  @Override
  public boolean supportsEmptyData() {
    return false;
  }

  @Override
  public @NotNull Class<PyCondaFlavorData> getFlavorDataClass() {
    return PyCondaFlavorData.class;
  }

  @Override
  public @NotNull Collection<@NotNull Path> suggestLocalHomePaths(@Nullable Module module, @Nullable UserDataHolder context) {
    // There is no such thing as "conda homepath" since conda doesn't store python path
    return Collections.emptyList();
  }

  @Override
  public boolean sdkSeemsValid(@NotNull Sdk sdk,
                               @NotNull PyCondaFlavorData flavorData,
                               @Nullable TargetEnvironmentConfiguration targetConfig) {
    var condaPath = flavorData.getEnv().getFullCondaPathOnTarget();
    boolean executable = isFileExecutable(condaPath, targetConfig);
    if (! executable) {
      Logger.getInstance(CondaEnvSdkFlavor.class).warn("file not executable on conda flavor: "  + condaPath);
    }
    return executable;
  }

  @Override
  public @NotNull String getUniqueId() {
    return "Conda";
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

  /**
   * @deprecated use {@link #validateCondaPath(String, TargetConfigurationReadableFs)}
   */
  @Deprecated
  public static ValidationInfo validateCondaPath(@Nullable @SystemDependent String condaExecutable) {
    return validateCondaPath(condaExecutable, PathInfo.Companion.getLocalPathInfoProvider());
  }

  @Nullable
  public static ValidationInfo validateCondaPath(@Nullable @SystemDependent String condaExecutable,
                                                 @Nullable TargetConfigurationReadableFs pathInfoProvider) {
    return PathValidatorKt.validateExecutableFile(
      new ValidationRequest(
        condaExecutable,
        PyBundle.message("python.add.sdk.conda.executable.path.is.empty"),
        pathInfoProvider,
        null
      ));
  }
}
