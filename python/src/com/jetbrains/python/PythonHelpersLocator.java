/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;

public class PythonHelpersLocator {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.PythonHelpersLocator");

  private static final String COMMUNITY_SUFFIX = "-community";

  private PythonHelpersLocator() {
  }

  /**
   * @return the base directory under which various scripts, etc are stored.
   */
  public static File getHelpersRoot() {
    @NonNls String jarPath = PathUtil.getJarPathForClass(PythonHelpersLocator.class);
    if (jarPath.endsWith(".jar")) {
      final File jarFile = new File(jarPath);

      LOG.assertTrue(jarFile.exists(), "jar file cannot be null");
      File pluginBaseDir = jarFile.getParentFile().getParentFile();
      return new File(pluginBaseDir, "helpers");
    }

    if (jarPath.endsWith(COMMUNITY_SUFFIX)) {
      jarPath = jarPath.substring(0, jarPath.length() - COMMUNITY_SUFFIX.length());
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

  public static String getPythonCommunityPath() {
    File pathFromUltimate = new File(PathManager.getHomePath(), "community/python");
    if (pathFromUltimate.exists()) {
      return pathFromUltimate.getPath();
    }
    return new File(PathManager.getHomePath(), "python").getPath();
  }
}
