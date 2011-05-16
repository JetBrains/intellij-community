package com.jetbrains.python.sdk;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;

import java.io.File;
import java.util.Collection;

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
  public void initPythonPath(GeneralCommandLine cmd, Collection<String> path) {
    cmd.getParametersList().add(getPythonPathCmdLineArgument(path));
  }

  public static String getPythonPathCmdLineArgument(Collection<String> path) {
    return "-Dpython.path=" + StringUtil.join(path, File.pathSeparator);
  }
}
