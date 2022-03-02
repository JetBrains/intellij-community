/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

public final class PythonHelpersLocator {
  private static final Logger LOG = Logger.getInstance(PythonHelpersLocator.class);
  private static final String PROPERTY_HELPERS_LOCATION = "idea.python.helpers.path";

  private PythonHelpersLocator() {}

  /**
   * @return the base directory under which various scripts, etc are stored.
   */
  @NotNull
  public static File getHelpersRoot() {
    String property = System.getProperty(PROPERTY_HELPERS_LOCATION);
    if (property != null) {
      return new File(property);
    }
    return assertHelpersLayout(getHelperRoot("intellij.python.helpers", "/python/helpers"));
  }

  @NotNull
  public static File getHelpersProRoot() {
    return assertHelpersProLayout(getHelperRoot("intellij.python.helpers.pro", "/../python/helpers-pro"));
  }

  @NotNull
  private static File getHelperRoot(@NotNull String moduleName, @NotNull String relativePath) {
    @NonNls String jarPath = PathUtil.getJarPathForClass(PythonHelpersLocator.class);

    if (PluginManagerCore.isRunningFromSources()) {
      return new File(PathManager.getCommunityHomePath() + relativePath);
    }
    else {
      final File pluginBaseDir = getPluginBaseDir(jarPath);
      if (pluginBaseDir != null) {
        return new File(pluginBaseDir, PathUtil.getFileName(relativePath));
      }
      else {
        return new File(new File(jarPath).getParentFile(), moduleName);
      }
    }
  }

  @Nullable
  private static File getPluginBaseDir(@NonNls String jarPath) {
    if (jarPath.endsWith(".jar")) {
      final File jarFile = new File(jarPath);

      LOG.assertTrue(jarFile.exists(), "jar file cannot be null");
      return jarFile.getParentFile().getParentFile();
    }
    return null;
  }

  private static @NotNull File assertHelpersLayout(@NotNull File root) {
    final String path = root.getAbsolutePath();

    LOG.assertTrue(root.exists(), "Helpers root does not exist " + path);
    for (String child : List.of("generator3", "pycharm", "pycodestyle.py", "pydev", "syspath.py", "typeshed")) {
      LOG.assertTrue(new File(root, child).exists(), "No '" + child + "' inside " + path);
    }

    return root;
  }

  private static @NotNull File assertHelpersProLayout(@NotNull File root) {
    final String path = root.getAbsolutePath();

    LOG.assertTrue(root.exists(), "Helpers pro root does not exist " + path);
    LOG.assertTrue(new File(root, "jupyter_debug").exists(), "No 'jupyter_debug' inside " + path);

    return root;
  }

  /**
   * Find a resource by name under helper root.
   *
   * @param resourceName a path relative to helper root
   * @return absolute path of the resource
   */
  public static String getHelperPath(@NonNls @NotNull String resourceName) {
    return getHelperFile(resourceName).getAbsolutePath();
  }

  /**
   * Finds a resource file by name under helper root.
   *
   * @param resourceName a path relative to helper root
   * @return a file object pointing to that path; existence is not checked.
   */
  @NotNull
  public static File getHelperFile(@NotNull String resourceName) {
    return new File(getHelpersRoot(), resourceName);
  }


  public static String getPythonCommunityPath() {
    File pathFromUltimate = new File(PathManager.getHomePath(), "community/python");
    if (pathFromUltimate.exists()) {
      return pathFromUltimate.getPath();
    }
    return new File(PathManager.getHomePath(), "python").getPath();
  }
}
