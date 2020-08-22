// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.flavors;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remote.RemoteFile;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;

public final class PyRemoteSdkFlavor extends CPythonSdkFlavor {
  private PyRemoteSdkFlavor() {
  }

  private final static String[] NAMES = new String[]{"python", "jython", "pypy", "python.exe", "jython.bat", "pypy.exe"};
  private final static String[] REMOTE_SDK_HOME_PREFIXES = new String[]{"ssh:", "vagrant:", "docker:", "docker-compose:", "sftp:"};

  @Override
  public boolean isPlatformIndependent() {
    return true;
  }

  @NotNull
  @Override
  public Collection<String> suggestHomePaths(@Nullable Module module, @Nullable UserDataHolder context) {
    return new ArrayList<>();
  }

  @Override
  public boolean isValidSdkHome(String path) {
    return StringUtil.isNotEmpty(path) && checkName(NAMES, getExecutableName(path)) && checkName(REMOTE_SDK_HOME_PREFIXES, path);
  }

  private static boolean checkName(String[] names, @Nullable String name) {
    if (name == null) {
      return false;
    }
    for (String n : names) {
      if (name.startsWith(n)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static String getExecutableName(String path) {
    return RemoteFile.createRemoteFile(path).getName();
  }

  @Override
  public Icon getIcon() {
    return PythonIcons.Python.RemoteInterpreter;
  }
}
