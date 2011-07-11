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
  private static final String JYTHONPATH = "JYTHONPATH";

  private JythonSdkFlavor() {
  }

  public static JythonSdkFlavor INSTANCE = new JythonSdkFlavor();

  public static String appendSystemJythonPath(String pythonPath) {
    String syspath = System.getenv(JYTHONPATH);
    if (syspath != null) {
      pythonPath += File.pathSeparator + syspath;
    }
    return pythonPath;
  }

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
    final String jythonPath = StringUtil.join(path, File.pathSeparator);
    addToEnv(cmd, JYTHONPATH, appendSystemJythonPath(jythonPath));
    cmd.getParametersList().add(getPythonPathCmdLineArgument(path));
  }

  public static String getPythonPathCmdLineArgument(Collection<String> path) {
    return "-Dpython.path=" + appendSystemJythonPath(StringUtil.join(path, File.pathSeparator));
  }
}
