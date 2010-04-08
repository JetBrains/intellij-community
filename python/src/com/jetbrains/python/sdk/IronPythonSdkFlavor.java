package com.jetbrains.python.sdk;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

/**
 * @author yole
 */
public class IronPythonSdkFlavor extends PythonSdkFlavor {
  private IronPythonSdkFlavor() {
  }

  public static IronPythonSdkFlavor INSTANCE = new IronPythonSdkFlavor();

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
}
