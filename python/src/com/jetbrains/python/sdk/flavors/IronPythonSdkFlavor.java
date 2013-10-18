package com.jetbrains.python.sdk.flavors;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.text.StringUtil;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.util.*;

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

  public String getVersionStringFromOutput(String version) {
    return getName() + " " + version;
  }

  @Override
  public String getVersionRegexp() {
    return "\\w+ ([0-9\\.]+).*";
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
