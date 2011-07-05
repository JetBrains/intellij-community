package com.jetbrains.python.sdk;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.text.StringUtil;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author yole
 */
public class IronPythonSdkFlavor extends PythonSdkFlavor {
  private IronPythonSdkFlavor() {
  }

  public static IronPythonSdkFlavor INSTANCE = new IronPythonSdkFlavor();

  @Override
  public Collection<String> suggestHomePaths() {
    Set<String> result = new TreeSet<String>();
    String root = System.getenv("ProgramFiles(x86)");
    if (root == null) {
      root = System.getenv("ProgramFiles");
    }
    if (root != null) {
      final File[] dirs = new File(root).listFiles();
      for (File dir : dirs) {
        if (dir.getName().startsWith("IronPython")) {
          File ipy = new File(dir, "ipy.exe");
          if (ipy.exists()) {
            result.add(ipy.getPath());
          }
        }
      }
    }
    WinPythonSdkFlavor.findInPath(result, "ipy.exe");
    WinPythonSdkFlavor.findInPath(result, "ipy64.exe");
    return result;
  }

  @Override
  public boolean isValidSdkHome(String path) {
    final String name = new File(path).getName();
    return name.equals("ipy.exe") || name.equals("ipy64.exe");
  }

  @Override
  public String getVersionString(String sdkHome) {
    return "IronPython " + getVersionFromOutput(sdkHome, "-V", "\\w+ ([0-9\\.]+).*", true);
  }

  @Override
  public Collection<String> getExtraDebugOptions() {
    return Collections.singletonList("-X:Frames");
  }

  @Override
  public void initPythonPath(GeneralCommandLine cmd, Collection<String> path) {
    addToEnv(cmd, "IRONPYTHONPATH", StringUtil.join(path, File.pathSeparator));
  }
}
