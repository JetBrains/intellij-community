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

import com.google.common.collect.Lists;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.PythonEnvUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.Map;

import static com.jetbrains.python.PythonHelpersLocator.getHelperFile;
import static com.jetbrains.python.PythonHelpersLocator.getHelpersRoot;

/**
 * @author traff
 */
public enum PythonHelper implements HelperPackage {
  COVERAGEPY("coveragepy", ""),
  COVERAGE("coverage_runner", "run_coverage"),
  DEBUGGER("pydev", "pydevd"),
  
  ATTACH_DEBUGGER("pydev/pydevd_attach_to_process/attach_pydevd.py"),

  CONSOLE("pydev", "pydevconsole"),
  RUN_IN_CONSOLE("pydev", "pydev_run_in_console"),
  PROFILER("profiler", "run_profiler"),

  LOAD_ENTRY_POINT("pycharm", "pycharm_load_entry_point"),

  // Test runners
  UT("pycharm", "utrunner"),
  TOX("pycharm", "_jb_tox_runner"),
  SETUPPY("pycharm", "pycharm_setup_runner"),
  NOSE("pycharm", "noserunner"),
  PYTEST("pycharm", "pytestrunner"),
  ATTEST("pycharm", "attestrunner"),
  DOCSTRING("pycharm", "docrunner"),

  BEHAVE("pycharm", "behave_runner"),
  LETTUCE("pycharm", "lettuce_runner"),

  DJANGO_TEST_MANAGE("pycharm", "django_test_manage"),
  DJANGO_MANAGE("pycharm", "django_manage"),
  MANAGE_TASKS_PROVIDER("pycharm", "_jb_manage_tasks_provider"),

  APPCFG_CONSOLE("pycharm", "appcfg_fetcher"),

  BUILDOUT_ENGULFER("pycharm", "buildout_engulfer"),

  DOCSTRING_FORMATTER("docstring_formatter.py"),

  EXTRA_SYSPATH("extra_syspath.py"),
  SYSPATH("syspath.py"),

  PYCODESTYLE("pycodestyle.py"),

  REST_RUNNER("rest_runners/rst2smth.py"),

  SPHINX_RUNNER("rest_runners/sphinx_runner.py");

  public static final String PY3_HELPER_DEPENDENCIES_DIR = "py3only";
  public static final String PY2_HELPER_DEPENDENCIES_DIR = "py2only";

  @NotNull
  private static PathHelperPackage findModule(String moduleEntryPoint, String path, boolean asModule) {
    if (getHelperFile(path + ".zip").isFile()) {
      return new ModuleHelperPackage(moduleEntryPoint, path + ".zip");
    }

    if (!asModule && new File(getHelperFile(path), moduleEntryPoint + ".py").isFile()) {
      return new ScriptPythonHelper(moduleEntryPoint + ".py", getHelperFile(path));
    }

    return new ModuleHelperPackage(moduleEntryPoint, path);
  }

  private final PathHelperPackage myModule;

  PythonHelper(String pythonPath, String moduleName) {
    this(pythonPath, moduleName, false);
  }

  PythonHelper(String pythonPath, String moduleName, boolean asModule) {
    myModule = findModule(moduleName, pythonPath, asModule);
  }

  PythonHelper(String helperScript) {
    myModule = new ScriptPythonHelper(helperScript, getHelpersRoot());
  }

  public abstract static class PathHelperPackage implements HelperPackage {
    protected final File myPath;

    PathHelperPackage(String path) {
      myPath = new File(path);
    }

    @Override
    public void addToPythonPath(@NotNull Map<String, String> environment) {
      PythonEnvUtil.addToPythonPath(environment, getPythonPathEntry());
    }

    @Override
    public void addToGroup(@NotNull ParamsGroup group, @NotNull GeneralCommandLine cmd) {
      addToPythonPath(cmd.getEnvironment());
      group.addParameter(asParamString());
    }

    @NotNull
    @Override
    public String asParamString() {
      return FileUtil.toSystemDependentName(myPath.getAbsolutePath());
    }

    @NotNull
    @Override
    public GeneralCommandLine newCommandLine(@NotNull String sdkPath, @NotNull List<String> parameters) {
      final List<String> args = Lists.newArrayList();
      args.add(sdkPath);
      args.add(asParamString());
      args.addAll(parameters);
      final GeneralCommandLine cmd = new GeneralCommandLine(args);
      final Map<String, String> env = cmd.getEnvironment();
      addToPythonPath(env);
      PythonEnvUtil.resetHomePathChanges(sdkPath, env);
      return cmd;
    }

    @NotNull
    @Override
    public GeneralCommandLine newCommandLine(@NotNull Sdk pythonSdk, @NotNull List<String> parameters) {
      final String sdkHomePath = pythonSdk.getHomePath();
      assert sdkHomePath != null;
      final GeneralCommandLine cmd = newCommandLine(sdkHomePath, parameters);
      final LanguageLevel version = PythonSdkType.getLanguageLevelForSdk(pythonSdk);
      final String perVersionDependenciesDir = version.isPy3K() ? PY3_HELPER_DEPENDENCIES_DIR : PY2_HELPER_DEPENDENCIES_DIR;
      PythonEnvUtil.addToPythonPath(cmd.getEnvironment(), FileUtil.join(getPythonPathEntry(), perVersionDependenciesDir));
      return cmd;
    }
  }

  /**
   * Module Python helper can be executed from zip-archive
   */
  public static class ModuleHelperPackage extends PathHelperPackage {
    private final String myModuleName;

    public ModuleHelperPackage(String moduleName, String relativePath) {
      super(getHelperFile(relativePath).getAbsolutePath());
      this.myModuleName = moduleName;
    }

    @NotNull
    @Override
    public String asParamString() {
      return "-m" + myModuleName;
    }

    @NotNull
    @Override
    public String getPythonPathEntry() {
      return FileUtil.toSystemDependentName(myPath.getAbsolutePath());
    }
  }

  /**
   * Script Python helper can be executed as a Python script, therefore
   * PYTHONDONTWRITEBYTECODE option is set not to spoil installation
   * with .pyc files
   */
  public static class ScriptPythonHelper extends PathHelperPackage {
    private final String myPythonPath;

    public ScriptPythonHelper(String script, File pythonPath) {
      super(new File(pythonPath, script).getAbsolutePath());
      myPythonPath = pythonPath.getAbsolutePath();
    }

    @Override
    public void addToPythonPath(@NotNull Map<String, String> environment) {
      PythonEnvUtil.setPythonDontWriteBytecode(environment);
      super.addToPythonPath(environment);
    }

    @NotNull
    @Override
    public String getPythonPathEntry() {
      return myPythonPath;
    }
  }

  
  @NotNull
  @Override
  public String getPythonPathEntry() {
    return myModule.getPythonPathEntry();
  }

  @Override
  public void addToPythonPath(@NotNull Map<String, String> environment) {
    myModule.addToPythonPath(environment);
  }

  @Override
  public void addToGroup(@NotNull ParamsGroup group, @NotNull GeneralCommandLine cmd) {
    myModule.addToGroup(group, cmd);
  }

  @NotNull
  @Override
  public String asParamString() {
    return myModule.asParamString();
  }

  @NotNull
  @Override
  public GeneralCommandLine newCommandLine(@NotNull String sdkPath, @NotNull List<String> parameters) {
    return myModule.newCommandLine(sdkPath, parameters);
  }

  @NotNull
  @Override
  public GeneralCommandLine newCommandLine(@NotNull Sdk pythonSdk, @NotNull List<String> parameters) {
    return myModule.newCommandLine(pythonSdk, parameters);
  }

}
