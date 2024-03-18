// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.flavors;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PatternUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.icons.PythonIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PyPySdkFlavor extends PythonSdkFlavor<PyFlavorData.Empty> {


  public static PyPySdkFlavor getInstance() {
    return PythonSdkFlavor.EP_NAME.findExtension(PyPySdkFlavor.class);
  }

  private static final Pattern VERSION_RE = Pattern.compile("\\[(PyPy \\S+).*\\]");
  private static final Pattern PYTHON_VERSION_RE = Pattern.compile("(Python \\S+).*");
  private static final Pattern VERSION_STRING_RE = Pattern.compile("PyPy (\\S+)( \\[Python (\\S+)\\])?");

  private PyPySdkFlavor() {
  }

  @Override
  public @NotNull Class<PyFlavorData.Empty> getFlavorDataClass() {
    return PyFlavorData.Empty.class;
  }

  @Override
  public boolean isPlatformIndependent() {
    return true;
  }

  @Override
  public boolean isValidSdkPath(@NotNull File file) {
    return StringUtil.toLowerCase(FileUtilRt.getNameWithoutExtension(file.getName())).startsWith("pypy");
  }

  @Nullable
  @Override
  public String getVersionStringFromOutput(@NotNull String output) {
    final List<String> lines = Arrays.asList(StringUtil.splitByLines(output));
    final String version = PatternUtil.getFirstMatch(lines, VERSION_RE);
    final String pythonVersion = PatternUtil.getFirstMatch(lines, PYTHON_VERSION_RE);
    if (version != null) {
      final StringBuilder builder = new StringBuilder();
      builder.append(version);
      if (pythonVersion != null) {
        builder.append(" [");
        builder.append(pythonVersion);
        builder.append("]");
      }
      return builder.toString();
    }
    return null;
  }

  @Override
  public @NotNull String getVersionOption() {
    return "--version";
  }

  @NotNull
  @Override
  public String getName() {
    return "PyPy";
  }

  @NotNull
  @Override
  public LanguageLevel getLanguageLevel(@NotNull Sdk sdk) {
    final String versionString = sdk.getVersionString();
    if (versionString != null) {
      final Matcher matcher = VERSION_STRING_RE.matcher(versionString);
      if (matcher.matches()) {
        final String version = matcher.group(1);
        final String pythonVersion = matcher.group(3);
        if (pythonVersion != null) {
          return LanguageLevel.fromPythonVersion(pythonVersion);
        }
        else if (version != null) {
          return LanguageLevel.fromPythonVersion(getPythonVersion(version));
        }
      }
    }
    return LanguageLevel.getDefault();
  }

  private static String getPythonVersion(@NotNull String pypyVersion) {
    final String DEFAULT = "2.4";
    final String LATEST = "2.7";
    final List<String> vs = StringUtil.split(pypyVersion, ".");
    try {
      if (vs.size() >= 2) {
        final int major = Integer.parseInt(vs.get(0));
        final int minor = Integer.parseInt(vs.get(1));
        if (major > 1) {
          return LATEST;
        }
        else if (major == 1) {
          if (minor >= 5) {
            return "2.7";
          }
          else if (minor >= 4) {
            return "2.5";
          }
        }
      }
    }
    catch (NumberFormatException ignored) {
    }
    return DEFAULT;
  }

  @Override
  public @NotNull Icon getIcon() {
    return PythonIcons.Python.Pypy;
  }
}
