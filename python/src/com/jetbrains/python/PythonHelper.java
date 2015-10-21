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
import com.intellij.openapi.util.io.FileUtil;
import com.jetbrains.python.sdk.PythonEnvUtil;
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
  COVERAGEPY("coveragepy", ""), COVERAGE("coverage_runner", "run_coverage"),
  DEBUGGER("pydev", "pydevd"),
  ATTACH_DEBUGGER("pydev", "pydevd_attach_to_process.attach_pydevd"),

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
  MANAGE_TASKS_PROVIDER("pycharm", "_jb_manage_tasks_provider"),

  APPCFG_CONSOLE("pycharm", "appcfg_fetcher"),

  BUILDOUT_ENGULFER("pycharm", "buildout_engulfer"),

  EPYDOC_FORMATTER("epydoc_formatter.py"),
  REST_FORMATTER("rest_formatter.py"),
  GOOGLE_FORMATTER("google_formatter.py"),
  NUMPY_FORMATTER("numpy_formatter.py"),

  EXTRA_SYSPATH("extra_syspath.py"),
  SYSPATH("syspath.py"),

  PEP8("pep8.py"),

  REST_RUNNER("rest_runners/rst2smth.py"),

  SPHINX_RUNNER("rest_runners/sphinx_runner.py");

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

  private PathHelperPackage myModule;

  PythonHelper(String pythonPath, String moduleName) {
    this(pythonPath, moduleName, false);
  }

  PythonHelper(String pythonPath, String moduleName, boolean asModule) {
    myModule = findModule(moduleName, pythonPath, asModule);
  }

  PythonHelper(String helperScript) {
    myModule = new ScriptPythonHelper(helperScript, getHelpersRoot());
  }


  public String getPythonPath() {
    return myModule.getPythonPath();
  }

  public abstract static class PathHelperPackage implements HelperPackage {
    protected final File myPath;

    PathHelperPackage(String path) {
      myPath = new File(path);
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
  public static class ModuleHelperPackage extends PathHelperPackage {
    private final String myModuleName;

    public ModuleHelperPackage(String moduleName, String relativePath) {
      super(getHelperFile(relativePath).getAbsolutePath());
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
  public static class ScriptPythonHelper extends PathHelperPackage {
    private String myPythonPath;

    public ScriptPythonHelper(String script, File pythonPath) {
      super(new File(pythonPath, script).getAbsolutePath());
      myPythonPath = pythonPath.getAbsolutePath();
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
