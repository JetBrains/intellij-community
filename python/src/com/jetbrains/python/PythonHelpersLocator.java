package com.jetbrains.python;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;

public class PythonHelpersLocator {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.PythonHelpersLocator");

  private PythonHelpersLocator() {
  }

  /**
   * @return the base directory under which various scripts, etc are stored.
   */
  public static File getHelpersRoot() {
    @NonNls final String jarPath = PathUtil.getJarPathForClass(PythonHelpersLocator.class);
    if (jarPath.endsWith(".jar")) {
      final File jarFile = new File(jarPath);

      LOG.assertTrue(jarFile.exists(), "jar file cannot be null");
      File pluginBaseDir = jarFile.getParentFile().getParentFile();
      return new File(pluginBaseDir, "helpers");
    }
    return new File(jarPath + "-helpers");
  }

  /**
   * Find a resource by name under helper root.
   * @param resourceName a path relative to helper root
   * @return absolute path of the resource
   */
  public static String getHelperPath(String resourceName) {
    return getHelperFile(resourceName).getAbsolutePath();
  }

  /**
   * Finds a resource file by name under helper root.
   * @param resourceName a path relative to helper root
   * @return a file object pointing to that path; existence is not checked.
   */
  public static File getHelperFile(String resourceName) {
    return new File(getHelpersRoot(), resourceName);
  }

}
