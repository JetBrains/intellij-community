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

import com.google.common.collect.Lists;
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
import java.util.List;
import java.util.Map;

public enum PythonHelpersLocator implements PythonHelper {
  COVERAGEPY("coveragepy", ""), COVERAGE("coverage", "run_coverage"),
  DEBUGGER("pydev", "pydevd"),
  CONSOLE("pydev", "pydevconsole"),
  RUN_IN_CONSOLE("pydev", "pydev_run_in_console"),
  PROFILER("profiler", "run_profiler"),

  LOAD_ENTRY_POINT("pycharm", "pycharm_load_entry_point"),

  // Test runners
  UT("pycharm", "utrunner"),
  SETUPPY("pycharm", "pycharm_setup_runner"),
  NOSE("pycharm", "noserunner"),
  PYTEST("pycharm", "pytestrunner"),
  ATTEST("pycharm", "attestrunner"),
  DOCSTRING("pycharm", "docrunner"),

  BEHAVE("pycharm", "behave_runner"),
  LETTUCE("pycharm", "lettuce_runner"),

  DJANGO_TEST_MANAGE("pycharm", "django_test_manage"),
  DJANGO_MANAGE("pycharm", "django_manage"),
  MANAGE_TASKS_PROVIDER("pycharm", "_jb_manage_tasks_provider")


  ;

  @NotNull
  private PathPythonHelper findModule(String moduleEntryPoint, String path) {
    if (getHelperFile(path + ".zip").isFile()) {
      return new ModulePythonHelper(moduleEntryPoint, path + ".zip");
    }

    if (getHelperFile(path).isDirectory()) {
        return new ModulePythonHelper(moduleEntryPoint, path);
    }

    return new ScriptPythonHelper(moduleEntryPoint, path);
  }

  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.PythonHelpersLocator");
  private static final String COMMUNITY_SUFFIX = "-community";

  private PathPythonHelper myModule;

  PythonHelpersLocator(String pythonPath, String moduleName) {
    myModule = findModule(moduleName, pythonPath);
  }

  public String getPythonPath() {
    return myModule.getPythonPath();
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

  public abstract static class PathPythonHelper implements PythonHelper {
    protected final File myPath;

    PathPythonHelper(String relativePath) {
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

    @Override
    public GeneralCommandLine newCommandLine(String sdkPath, List<String> parameters) {
      List<String> args = Lists.newArrayList();
      args.add(sdkPath);
      args.add(asParamString());
      args.addAll(parameters);
      GeneralCommandLine cmd = new GeneralCommandLine(args);
      addToPythonPath(cmd.getEnvironment());
      return cmd;
    }
  }

  /**
   * Module Python helper can be executed from zip-archive
   */
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

    @Override
    public String getPythonPath() {
      return FileUtil.toSystemDependentName(myPath.getAbsolutePath());
    }
  }

  /**
   * Script Python helper can be executed as a Python script, therefore
   * PYTHONDONTWRITEBYTECODE option is set not to spoil installation
   * with .pyc files
   */
  public static class ScriptPythonHelper extends PathPythonHelper {
    private String myPythonPath;

    public ScriptPythonHelper(String module, String pythonPath) {
      super(new File(pythonPath, module.replace(".", File.separator)).getPath());
      myPythonPath = pythonPath;
    }

    @Override
    public void addToPythonPath(@NotNull Map<String, String> environment) {
      PythonEnvUtil.setPythonDontWriteBytecode(environment);
      PythonEnvUtil.addToPythonPath(environment, myPythonPath);
    }

    @Override
    public String getPythonPath() {
      return myPythonPath;
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

  @Override
  public GeneralCommandLine newCommandLine(String sdkPath, List<String> parameters) {
    return myModule.newCommandLine(sdkPath, parameters);
  }
}
