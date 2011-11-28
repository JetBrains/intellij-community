package com.jetbrains.python.sdk;

import com.intellij.openapi.util.io.FileUtil;

import java.io.File;

/**
 * @author traff
 */
public class PyPySdkFlavor extends PythonSdkFlavor {
  private PyPySdkFlavor() {
  }

  public static PyPySdkFlavor INSTANCE = new PyPySdkFlavor();

  @Override
  public boolean isValidSdkHome(String path) {
    File file = new File(path);
    return file.isFile() && FileUtil.getNameWithoutExtension(file).toLowerCase().startsWith("pypy");
  }

  @Override
  public String getVersionString(String sdkHome) {
    return getVersionFromOutput(sdkHome, "--version", "\\[(PyPy .+)\\]", true);
  }
}
