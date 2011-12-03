package com.jetbrains.python.sdk;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

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
    return getVersionFromOutput(sdkHome, "--version", "\\[(PyPy \\S+).*\\]");
  }

  @NotNull
  @Override
  public String getName() {
    return "PyPy";
  }

  @Override
  public LanguageLevel getLanguageLevel(Sdk sdk) {
    final String version = sdk.getVersionString();
    final String prefix = getName() + " ";
    if (version != null && version.startsWith(prefix)) {
      String pypyVersion = version.substring(prefix.length());
      return LanguageLevel.fromPythonVersion(getPythonVersion(pypyVersion));
    }
    return LanguageLevel.getDefault();
  }

  private String getPythonVersion(@NotNull String pypyVersion) {
    final String DEFAULT = "2.4";
    final String LATEST = "2.7";
    final List<String> vs = StringUtil.split(pypyVersion, ".");
    try {
      if (vs.size() >= 2) {
        final int major = Integer.parseInt(vs.get(0));
        final int minor = Integer.parseInt(vs.get(1));
        if (major > 1) {
          return LATEST;
        }
        else if (major == 1) {
          if (minor >= 5) {
            return "2.7";
          }
          else if (minor >= 4) {
            return "2.5";
          }
        }
      }
    }
    catch (NumberFormatException ignored) {
    }
    return DEFAULT;
  }
}
