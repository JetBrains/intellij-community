// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.flavors;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PatternUtil;
import com.jetbrains.python.sdk.PythonEnvUtil;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.*;
import java.util.regex.Pattern;


public final class IronPythonSdkFlavor extends PythonSdkFlavor {
  public static final Pattern VERSION_RE = Pattern.compile("\\w+ ([0-9\\.]+).*");

  private IronPythonSdkFlavor() {
  }

  @Override
  public boolean isPlatformIndependent() {
    return true;
  }

  @Override
  public @NotNull String envPathParam() {
    return "IRONPYTHONPATH";
  }

  @NotNull
  @Override
  public Collection<String> suggestHomePaths(@Nullable Module module, @Nullable UserDataHolder context) {
    Set<String> result = new TreeSet<>();
    String root = System.getenv("ProgramFiles(x86)");
    if (root == null) {
      root = System.getenv("ProgramFiles");
    }
    if (root != null) {
      final File[] dirs = new File(root).listFiles();
      if (dirs != null) {
        for (File dir : dirs) {
          if (dir.getName().startsWith("IronPython")) {
            File ipy = new File(dir, "ipy.exe");
            if (ipy.exists()) {
              result.add(ipy.getPath());
            }
          }
        }
      }
    }
    WinPythonSdkFlavor.findInPath(result, "ipy.exe");
    WinPythonSdkFlavor.findInPath(result, "ipy64.exe");
    return result;
  }

  @Override
  public boolean isValidSdkPath(@NotNull File file) {
    final String name = file.getName();
    return name.equals("ipy.exe") || name.equals("ipy64.exe");
  }

  @Nullable
  @Override
  public String getVersionStringFromOutput(@NotNull String output) {
    final String match = PatternUtil.getFirstMatch(Arrays.asList(StringUtil.splitByLines(output)), VERSION_RE);
    return match != null ? getName() + " " + match : null;
  }

  @Override
  public @NotNull Collection<String> getExtraDebugOptions() {
    return Collections.singletonList("-X:Frames");
  }

  @Override
  public void initPythonPath(@NotNull GeneralCommandLine cmd, boolean passParentEnvs, @NotNull Collection<String> path) {
    initPythonPath(path, passParentEnvs, cmd.getEnvironment());
  }

  @Override
  public void initPythonPath(@NotNull Collection<String> path, boolean passParentEnvs, @NotNull Map<String, String> env) {
    PythonEnvUtil.addToEnv("IRONPYTHONPATH", StringUtil.join(path, File.pathSeparator), env);
  }

  @NotNull
  @Override
  public String getName() {
    return "IronPython";
  }

  @Override
  public @NotNull Icon getIcon() {
    return PythonIcons.Python.Dotnet;
  }
}
