package com.jetbrains.python.sdk.flavors;

import com.google.common.collect.Lists;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remotesdk.RemoteFile;
import org.jetbrains.annotations.Nullable;

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
    return StringUtil.isNotEmpty(path) && path.startsWith("ssh:") && checkName(NAMES, getExecutableName(path));
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
    return RemoteFile.detectSystemByPath(path).createRemoteFile(path).getName();
  }
}
