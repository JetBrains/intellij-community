package com.jetbrains.python;

import org.jetbrains.annotations.NonNls;
import com.intellij.util.PathUtil;
import com.intellij.openapi.diagnostic.Logger;

import java.io.File;

public class PythonHelpersLocator {
    private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.PythonHelpersLocator");

    private PythonHelpersLocator() {
    }

    public static File getHelpersRoot() {
      @NonNls final String jarPath = PathUtil.getJarPathForClass(PythonHelpersLocator.class);
      if (jarPath.endsWith(".jar")) {
        final File jarFile = new File(jarPath);

        LOG.assertTrue(jarFile.exists(), "jar file cannot be null");
        File pluginBaseDir = jarFile.getParentFile().getParentFile();
        return new File(pluginBaseDir, "helpers");
      }
      return new File(jarPath);
    }
}
