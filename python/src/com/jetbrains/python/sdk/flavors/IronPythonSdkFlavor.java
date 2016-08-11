/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.sdk.flavors;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PatternUtil;
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
public class IronPythonSdkFlavor extends PythonSdkFlavor {
  public static final Pattern VERSION_RE = Pattern.compile("\\w+ ([0-9\\.]+).*");

  private IronPythonSdkFlavor() {
  }

  public static IronPythonSdkFlavor INSTANCE = new IronPythonSdkFlavor();

  @Override
  public Collection<String> suggestHomePaths() {
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
  public String getVersionOption() {
    return "-V";
  }

  @Override
  public Collection<String> getExtraDebugOptions() {
    return Collections.singletonList("-X:Frames");
  }

  @Override
  public void initPythonPath(GeneralCommandLine cmd, Collection<String> path) {
    initPythonPath(path, cmd.getEnvironment());
  }

  @Override
  public void initPythonPath(Collection<String> path, Map<String, String> env) {
    addToEnv("IRONPYTHONPATH", StringUtil.join(path, File.pathSeparator), env);
  }

  @NotNull
  @Override
  public String getName() {
    return "IronPython";
  }

  @Override
  public Icon getIcon() {
    return PythonIcons.Python.Dotnet;
  }
}
