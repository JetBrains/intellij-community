/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import com.jetbrains.python.sdk.PythonEnvUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Map;

public enum PythonHelpersLocator implements PythonHelper {
  COVERAGEPY("", "coveragepy"), COVERAGE("run_coverage", "coverage"),
  DEBUGGER("pydevd", "pydev"),
  CONSOLE("pydevconsole", "pydev");

  @NotNull
  private PathPythonHelper findModule(String moduleName, String path) {
    if (getHelperFile(path + ".zip").isFile()) {
      return new ModulePythonHelper(moduleName, path + ".zip");
    }

    if (getHelperFile(path).isDirectory()) {
        return new ModulePythonHelper(moduleName, path);
    }

    if (getHelperFile(path).isFile()) {
      return new ScriptPythonHelper(path);
    }

    throw new IllegalStateException("Corrupted installation. Helper not found: " + name());
  }

  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.PythonHelpersLocator");
  private static final String COMMUNITY_SUFFIX = "-community";

  private PathPythonHelper myModule;

  PythonHelpersLocator(String moduleName, String pythonPath) {
    myModule = findModule(moduleName, pythonPath);
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
   *
   * @param resourceName a path relative to helper root
   * @return absolute path of the resource
   */
  public static String getHelperPath(String resourceName) {
    return getHelperFile(resourceName).getAbsolutePath();
  }

  /**
   * Finds a resource file by name under helper root.
   *
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

  public static class PathPythonHelper implements PythonHelper {
    protected final File myPath;

    public PathPythonHelper(String relativePath) {
      myPath = getHelperFile(relativePath);
    }

    @Override
    public void addToPythonPath(@NotNull Map<String, String> environment) {
      PythonEnvUtil.addToPythonPath(environment, myPath.getAbsolutePath());
    }

    @Override
    public void addToGroup(@NotNull ParamsGroup group, @NotNull GeneralCommandLine cmd) {
      addToPythonPath(cmd.getEnvironment());
      group.addParameter(asParamString());
    }

    @Override
    public String asParamString() {
      return FileUtil.toSystemDependentName(myPath.getAbsolutePath());
    }
  }

  public static class ModulePythonHelper extends PathPythonHelper {
    private final String myModuleName;

    public ModulePythonHelper(String moduleName, String relativePath) {
      super(relativePath);
      this.myModuleName = moduleName;
    }

    @Override
    public String asParamString() {
      return "-m" + myModuleName;
    }
  }

  public static class ScriptPythonHelper extends PathPythonHelper {
    public ScriptPythonHelper(String relativePath) {
      super(relativePath);
    }

    @Override
    public void addToPythonPath(@NotNull Map<String, String> environment) {
      PythonEnvUtil.setPythonDontWriteBytecode(environment);
      PythonEnvUtil.addToPythonPath(environment, myPath.getParent());
    }
  }


  @Override
  public void addToPythonPath(@NotNull Map<String, String> environment) {
    myModule.addToPythonPath(environment);
  }

  @Override
  public void addToGroup(@NotNull ParamsGroup group, @NotNull GeneralCommandLine cmd) {
    myModule.addToGroup(group, cmd);
  }


  @Override
  public String asParamString() {
    return myModule.asParamString();
  }
}
