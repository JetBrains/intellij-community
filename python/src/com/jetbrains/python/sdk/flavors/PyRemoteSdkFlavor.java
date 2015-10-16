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

import com.google.common.collect.Lists;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remote.RemoteFile;
import icons.PythonIcons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

/**
 * @author traff
 */
public class PyRemoteSdkFlavor extends CPythonSdkFlavor {
  private PyRemoteSdkFlavor() {
  }

  private final static String[] NAMES = new String[]{"python", "jython", "pypy", "python.exe", "jython.bat", "pypy.exe"};

  public static PyRemoteSdkFlavor INSTANCE = new PyRemoteSdkFlavor();

  @Override
  public Collection<String> suggestHomePaths() {
    return Lists.newArrayList();
  }

  @Override
  public boolean isValidSdkHome(String path) {
    return StringUtil.isNotEmpty(path) && checkName(NAMES, getExecutableName(path))
           && (path.startsWith("ssh:") || path.startsWith("vagrant:") || path.startsWith("docker:"));
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
