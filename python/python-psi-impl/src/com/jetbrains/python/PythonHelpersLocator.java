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

import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.util.LazyInitializer;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class PythonHelpersLocator {
  private static final Logger LOG = Logger.getInstance(PythonHelpersLocator.class);
  private static final String PROPERTY_HELPERS_LOCATION = "idea.python.helpers.path";

  private static final LazyInitializer.LazyValue<@Nullable File> maybeCopiedHelpersRoot = new LazyInitializer.LazyValue<>(() -> {
    try {
      if (ApplicationInfo.getInstance().getBuild().isSnapshot()) {
        // Although it runs not directly from the IDE, it's still built locally from sources, and it's supposed that
        // copied code may change between runs.
        return FileUtil.createTempDirectory("python-helpers", null, true);
      }
      else {
        return new File(PathManager.getSystemPath());
      }
    }
    catch (IOException e) {
      throw new UncheckedIOException("Failed to create temporary directory for helpers", e);
    }
  });

  private PythonHelpersLocator() { }

  /**
   * @return the base directory under which various Python scripts and other auxiliary files are stored.
   * @deprecated This method used to be cheap, but now it may invoke I/O. Consider choosing between
   * {@link #predictHelpersPath()} and {@link #getCopiedHelpersPath()}. If you're not sure, prefer {@link #getCopiedHelpersPath()} and watch
   * for exceptions.
   */
  @Deprecated
  public static @NotNull File getHelpersRoot() {
    String property = System.getProperty(PROPERTY_HELPERS_LOCATION);
    if (property != null) {
      return new File(property);
    }
    return assertHelpersLayout(getCopiedHelpersPath(ModuleHelpers.COMMUNITY));
  }

  /**
   * @return the base directory under which various scripts, various Python scripts and other auxiliary files are supposed to be stored.
   * The helper scripts may not be stored there at the time of calling this function.
   */
  public static @NotNull File predictHelpersPath() {
    String property = System.getProperty(PROPERTY_HELPERS_LOCATION);
    if (property != null) {
      return new File(property);
    }
    return predictHelpersPath(ModuleHelpers.COMMUNITY);
  }

  /**
   * @return the base directory under which various scripts, various Python scripts and other auxiliary files are stored.
   * If the files haven't been copied yet, the functions does that blocking the current thread.
   */
  public static @NotNull File getCopiedHelpersPath() {
    logErrorOnEdt();
    String property = System.getProperty(PROPERTY_HELPERS_LOCATION);
    if (property != null) {
      return new File(property);
    }
    return assertHelpersLayout(getCopiedHelpersPath(ModuleHelpers.COMMUNITY));
  }

  /**
   * @deprecated This method used to be cheap, but now it may invoke I/O. Consider choosing between
   * {@link #predictHelpersProPath()} and {@link #getCopiedHelpersProPath()}. If you're unsure, prefer {@link #getCopiedHelpersProPath()}
   * and watch for exceptions.
   */
  @Deprecated
  public static @NotNull Path getHelpersProRoot() {
    return assertHelpersProLayout(getCopiedHelpersPath(ModuleHelpers.PRO)).toPath().normalize();
  }

  /**
   * See {@link #predictHelpersPath()}.
   */
  public static @NotNull Path predictHelpersProPath() {
    return predictHelpersPath(ModuleHelpers.PRO).toPath().normalize();
  }

  /**
   * See {@link #getCopiedHelpersPath()}.
   */
  public static @NotNull Path getCopiedHelpersProPath() {
    logErrorOnEdt();
    return assertHelpersProLayout(getCopiedHelpersPath(ModuleHelpers.PRO)).toPath().normalize();
  }

  @NotNull
  private static File predictHelpersPath(@NotNull ModuleHelpers moduleHelpers) {
    return new File(maybeCopiedHelpersRoot.get(), moduleHelpers.getSubDirectory());
  }

  private static @NotNull File getCopiedHelpersPath(@NotNull ModuleHelpers moduleHelpers) {
    if (PluginManagerCore.isRunningFromSources()) {
      return new File(PathManager.getCommunityHomePath(), moduleHelpers.myCommunityRepoRelativePath);
    }
    else {
      @Nullable File helpersRootDir = ProgressIndicatorUtils.awaitWithCheckCanceled(moduleHelpers.copiedHelpersRoot.get());
      if (helpersRootDir != null) {
        return helpersRootDir;
      }
      else {
        @NonNls String jarPath = PathUtil.getJarPathForClass(PythonHelpersLocator.class);
        return new File(new File(jarPath).getParentFile(), moduleHelpers.myModuleName);
      }
    }
  }

  private static void logErrorOnEdt() {
    try {
      Application app = ApplicationManager.getApplication();
      if (app != null) {
        app.assertIsNonDispatchThread();
      }
    }
    catch (AssertionError err) {
      Logger.getInstance(PythonHelpersLocator.class).error(err);
    }
  }

  private static @Nullable File getPluginBaseDir(@NonNls String jarPath) {
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
  public static @NotNull File getHelperFile(@NotNull String resourceName) {
    return new File(getHelpersRoot(), resourceName);
  }

  public static String getPythonCommunityPath() {
    File pathFromUltimate = new File(PathManager.getHomePath(), "community/python");
    if (pathFromUltimate.exists()) {
      return pathFromUltimate.getPath();
    }
    return new File(PathManager.getHomePath(), "python").getPath();
  }

  private enum ModuleHelpers {
    COMMUNITY("intellij.python.helpers", "helpers", "python/helpers"),
    PRO("intellij.python.helpers.pro", "helpers-pro", "../python/helpers-pro");

    final String myModuleName;
    final String myCommunityRepoRelativePath;

    /**
     * Python creates *.pyc files near to *.py files after importing. It used to break patch updates from Toolbox on macOS
     * due to signature mismatches: macOS refuses to launch such applications and users had to reinstall the IDE.
     * There is no check for macOS though, since such problems may appear in other operating systems as well, and since it's a bad
     * idea to modify the IDE distributive during running in general.
     */
    final LazyInitializer.LazyValue<@NotNull CompletableFuture<@Nullable File>> copiedHelpersRoot;

    ModuleHelpers(@NotNull String moduleName, @NotNull String pluginRelativePath, @NotNull String communityRelativePath) {
      myModuleName = moduleName;
      myCommunityRepoRelativePath = communityRelativePath;

      copiedHelpersRoot = new LazyInitializer.LazyValue<>(() -> {
        String jarPath = PathUtil.getJarPathForClass(PythonHelpersLocator.class);
        final File pluginBaseDir = getPluginBaseDir(jarPath);
        if (pluginBaseDir == null) {
          return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.supplyAsync(() -> {
          try {
            final File helpersRootDir = predictHelpersPath(this);
            if (!helpersRootDir.isDirectory()) {
              NioFiles.createDirectories(helpersRootDir.toPath().getParent());
              FileUtil.copyDir(new File(pluginBaseDir, pluginRelativePath), helpersRootDir, true);
            }
            return helpersRootDir;
          }
          catch (IOException e) {
            throw new UncheckedIOException("Failed to create temporary directory for helpers", e);
          }
        }, ProcessIOExecutorService.INSTANCE);
      });
    }

    @NotNull String getSubDirectory() {
      return "python-helpers-" + ApplicationInfo.getInstance().getBuild().asStringWithoutProductCode() + File.separator + myModuleName;
    }
  }
}
