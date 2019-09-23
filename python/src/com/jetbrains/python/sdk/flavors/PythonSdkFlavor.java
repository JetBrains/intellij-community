// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.flavors;

import com.google.common.collect.Lists;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PatternUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.run.CommandLinePatcher;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonEnvUtil;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public abstract class PythonSdkFlavor {
  private static final Pattern VERSION_RE = Pattern.compile("(Python \\S+).*");
  private static final Logger LOG = Logger.getInstance(PythonSdkFlavor.class);


  /**
   * @param context used as cache.
   *                If provided, must have "session"-scope.
   *                Session could be one dialog or wizard.
   */
  @NotNull
  public Collection<String> suggestHomePaths(@Nullable final Module module, @Nullable final UserDataHolder context) {
    return suggestHomePaths(module);
  }


  /**
   * @deprecated use {@link #suggestHomePaths(Module, UserDataHolder)}.
   * Will be deleted in 2020.3
   */
  @SuppressWarnings("unused")
  @Deprecated
  @NotNull
  public Collection<String> suggestHomePaths(@Nullable final Module module) {
    return Collections.emptyList();
  }

  public static List<PythonSdkFlavor> getApplicableFlavors() {
    return getApplicableFlavors(true);
  }

  public static List<PythonSdkFlavor> getApplicableFlavors(boolean addPlatformIndependent) {
    List<PythonSdkFlavor> result = new ArrayList<>();

    if (SystemInfo.isWindows) {
      result.add(ServiceManager.getService(WinPythonSdkFlavor.class));
    }
    else if (SystemInfo.isMac) {
      result.add(MacPythonSdkFlavor.INSTANCE);
    }
    else if (SystemInfo.isUnix) {
      result.add(UnixPythonSdkFlavor.INSTANCE);
    }

    if (addPlatformIndependent) {
      result.addAll(getPlatformIndependentFlavors());
    }

    result.addAll(getPlatformFlavorsFromExtensions(addPlatformIndependent));

    return result;
  }

  public static List<PythonSdkFlavor> getPlatformFlavorsFromExtensions(boolean isInpedendent) {
    List<PythonSdkFlavor> result = new ArrayList<>();
    for (PythonFlavorProvider provider : PythonFlavorProvider.EP_NAME.getExtensionList()) {
      PythonSdkFlavor flavor = provider.getFlavor(isInpedendent);
      if (flavor != null) {
        result.add(flavor);
      }
    }
    return result;
  }

  public static List<PythonSdkFlavor> getPlatformIndependentFlavors() {
    List<PythonSdkFlavor> result = Lists.newArrayList();
    result.add(JythonSdkFlavor.INSTANCE);
    result.add(IronPythonSdkFlavor.INSTANCE);
    result.add(PyPySdkFlavor.INSTANCE);
    result.add(VirtualEnvSdkFlavor.INSTANCE);
    result.add(CondaEnvSdkFlavor.INSTANCE);
    result.add(PyRemoteSdkFlavor.INSTANCE);

    return result;
  }

  @Nullable
  public static PythonSdkFlavor getFlavor(@NotNull final Sdk sdk) {
    final SdkAdditionalData data = sdk.getSdkAdditionalData();
    if (data instanceof PythonSdkAdditionalData) {
      PythonSdkFlavor flavor = ((PythonSdkAdditionalData)data).getFlavor();
      if (flavor != null) {
        return flavor;
      }
    }
    return getFlavor(sdk.getHomePath());
  }

  @Nullable
  public static PythonSdkFlavor getFlavor(@Nullable String sdkPath) {
    if (sdkPath == null) return null;

    for (PythonSdkFlavor flavor : getApplicableFlavors()) {
      if (flavor.isValidSdkHome(sdkPath)) {
        return flavor;
      }
    }
    return null;
  }

  @Nullable
  public static PythonSdkFlavor getPlatformIndependentFlavor(@Nullable final String sdkPath) {
    if (sdkPath == null) return null;

    for (PythonSdkFlavor flavor : getPlatformIndependentFlavors()) {
      if (flavor.isValidSdkHome(sdkPath)) {
        return flavor;
      }
    }

    for (PythonSdkFlavor flavor: getPlatformFlavorsFromExtensions(true)) {
      if (flavor.isValidSdkHome(sdkPath)) {
        return flavor;
      }
    }
    return null;
  }

  /**
   * Checks if the path is the name of a Python interpreter of this flavor.
   *
   * @param path path to check.
   * @return true if paths points to a valid home.
   */
  public boolean isValidSdkHome(String path) {
    File file = new File(path);
    return file.isFile() && isValidSdkPath(file);
  }

  public boolean isValidSdkPath(@NotNull File file) {
    return StringUtil.toLowerCase(FileUtilRt.getNameWithoutExtension(file.getName())).startsWith("python");
  }

  @Nullable
  public String getVersionString(@Nullable String sdkHome) {
    if (sdkHome == null) {
      return null;
    }
    final String runDirectory = new File(sdkHome).getParent();
    final ProcessOutput processOutput = PySdkUtil.getProcessOutput(runDirectory, new String[]{sdkHome, getVersionOption()}, 10000);
    return getVersionStringFromOutput(processOutput);
  }

  @Nullable
  public String getVersionStringFromOutput(@NotNull ProcessOutput processOutput) {
    if (processOutput.getExitCode() != 0) {
      String errors = processOutput.getStderr();
      if (StringUtil.isEmpty(errors)) {
        errors = processOutput.getStdout();
      }
      LOG.warn("Couldn't get interpreter version: process exited with code " + processOutput.getExitCode() + "\n" + errors);
      return null;
    }
    final String result = getVersionStringFromOutput(processOutput.getStderr());
    if (result != null) {
      return result;
    }
    return getVersionStringFromOutput(processOutput.getStdout());
  }

  @Nullable
  public String getVersionStringFromOutput(@NotNull String output) {
    return PatternUtil.getFirstMatch(Arrays.asList(StringUtil.splitByLines(output)), VERSION_RE);
  }

  public String getVersionOption() {
    return "-V";
  }

  public Collection<String> getExtraDebugOptions() {
    return Collections.emptyList();
  }

  public void initPythonPath(GeneralCommandLine cmd, boolean passParentEnvs, Collection<String> path) {
    initPythonPath(path, passParentEnvs, cmd.getEnvironment());
  }

  @NotNull
  public abstract String getName();

  @NotNull
  public LanguageLevel getLanguageLevel(@NotNull Sdk sdk) {
    return getLanguageLevelFromVersionString(sdk.getVersionString());
  }

  @NotNull
  public LanguageLevel getLanguageLevel(@NotNull String sdkHome) {
    return getLanguageLevelFromVersionString(getVersionString(sdkHome));
  }

  @NotNull
  public LanguageLevel getLanguageLevelFromVersionString(@Nullable String version) {
    final String prefix = getName() + " ";
    if (version != null && version.startsWith(prefix)) {
      return LanguageLevel.fromPythonVersion(version.substring(prefix.length()));
    }
    return LanguageLevel.getDefault();
  }

  public Icon getIcon() {
    return PythonIcons.Python.Python;
  }

  public void initPythonPath(Collection<String> path, boolean passParentEnvs, Map<String, String> env) {
    PythonEnvUtil.initPythonPath(env, passParentEnvs, path);
  }

  public VirtualFile getSdkPath(VirtualFile path) {
    return path;
  }

  @Nullable
  public CommandLinePatcher commandLinePatcher() {
    return null;
  }
}
