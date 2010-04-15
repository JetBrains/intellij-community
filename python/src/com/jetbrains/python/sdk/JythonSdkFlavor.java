package com.jetbrains.python.sdk;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.io.FileUtil;

import java.io.File;

/**
 * @author yole
 */
public class JythonSdkFlavor extends PythonSdkFlavor {
  private JythonSdkFlavor() {
  }

  public static JythonSdkFlavor INSTANCE = new JythonSdkFlavor();

  @Override
  public boolean isValidSdkHome(String path) {
    File file = new File(path);
    return file.isFile() && FileUtil.getNameWithoutExtension(file).toLowerCase().startsWith("jython");
  }

  @Override
  public String getVersionString(String sdkHome) {
    return getVersionFromOutput(sdkHome, "--version", "(Jython \\S+) on .*", false);
  }

  @Override
  public void addToPythonPath(GeneralCommandLine cmd, String path) {
    cmd.getParametersList().add("-Dpython.path=" + path);
  }
}
