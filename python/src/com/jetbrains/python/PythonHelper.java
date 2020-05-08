// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.PythonEnvUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.jetbrains.python.PythonHelpersLocator.getHelperFile;
import static com.jetbrains.python.PythonHelpersLocator.getHelpersRoot;

public enum PythonHelper implements HelperPackage {
  GENERATOR3("generator3/__main__.py"),

  COVERAGEPY("coveragepy", ""),
  COVERAGE("coverage_runner", "run_coverage"),
  DEBUGGER("pydev", "pydevd", HelperDependency.THRIFTPY),

  ATTACH_DEBUGGER("pydev/pydevd_attach_to_process/attach_pydevd.py"),

  CONSOLE("pydev", "pydevconsole", HelperDependency.THRIFTPY),
  PROFILER("profiler", "run_profiler", HelperDependency.THRIFTPY),
  LOAD_PSTAT("profiler", "load_pstat", HelperDependency.THRIFTPY),

  LOAD_ENTRY_POINT("pycharm", "pycharm_load_entry_point"),

  // Test runners
  UT_OLD("pycharm", "utrunner"),
  TOX("pycharm", "_jb_tox_runner"),
  SETUPPY("pycharm", "pycharm_setup_runner"),
  NOSE_OLD("pycharm", "noserunner"),
  PYTEST_OLD("pycharm", "pytestrunner"),
  DOCSTRING("pycharm", "docrunner"),

  // Runners for new test runner API.

  UNITTEST("pycharm", "_jb_unittest_runner"),
  PYTEST("pycharm", "_jb_pytest_runner"),
  TRIAL("pycharm", "_jb_trialtest_runner"),
  NOSE("pycharm", "_jb_nosetest_runner"),

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

  SPHINX_RUNNER("rest_runners/sphinx_runner.py"),

  JUPYTER("pycharm", "jupyter");

  public static final String PY3_HELPER_DEPENDENCIES_DIR = "py3only";
  public static final String PY2_HELPER_DEPENDENCIES_DIR = "py2only";

  @NotNull
  private static PathHelperPackage findModule(String moduleEntryPoint, String path, boolean asModule, String[] thirdPartyDependencies) {
    List<HelperDependency> dependencies = HelperDependency.findThirdPartyDependencies(thirdPartyDependencies);

    if (getHelperFile(path + ".zip").isFile()) {
      return new ModuleHelperPackage(moduleEntryPoint, path + ".zip", dependencies);
    }

    if (!asModule && new File(getHelperFile(path), moduleEntryPoint + ".py").isFile()) {
      return new ScriptPythonHelper(moduleEntryPoint + ".py", getHelperFile(path), dependencies);
    }

    return new ModuleHelperPackage(moduleEntryPoint, path, dependencies);
  }

  private final PathHelperPackage myModule;

  PythonHelper(String pythonPath, String moduleName, String... dependencies) {
    this(pythonPath, moduleName, false, dependencies);
  }

  PythonHelper(String pythonPath, String moduleName, boolean asModule, String... dependencies) {
    myModule = findModule(moduleName, pythonPath, asModule, dependencies);
  }

  PythonHelper(String helperScript) {
    myModule = new ScriptPythonHelper(helperScript, getHelpersRoot(), Collections.emptyList());
  }

  public abstract static class PathHelperPackage implements HelperPackage {
    protected final File myPath;
    @NotNull
    protected final List<HelperDependency> myDependencies;

    PathHelperPackage(String path, @NotNull List<HelperDependency> dependencies) {
      myPath = new File(path);
      myDependencies = dependencies;
    }

    @Override
    public void addToPythonPath(@NotNull Map<String, String> environment) {
      // at first add dependencies
      myDependencies.forEach(dependency -> dependency.addToPythonPath(environment));
      // then add helper script
      PythonEnvUtil.addToPythonPath(environment, getPythonPathEntry());
    }

    @Override
    public @NotNull List<String> getPythonPathEntries() {
      // at first add dependencies
      ArrayList<String> entries = myDependencies.stream()
        .flatMap(dependency -> dependency.getPythonPathEntries().stream())
        .collect(Collectors.toCollection(ArrayList::new));
      // then add helper script
      entries.add(getPythonPathEntry());
      return entries;
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
      final List<String> args = new ArrayList<>();
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
      final String perVersionDependenciesDir = version.isPython2() ? PY2_HELPER_DEPENDENCIES_DIR : PY3_HELPER_DEPENDENCIES_DIR;
      PythonEnvUtil.addToPythonPath(cmd.getEnvironment(), FileUtil.join(getPythonPathEntry(), perVersionDependenciesDir));
      return cmd;
    }
  }

  /**
   * Module Python helper can be executed from zip-archive
   */
  public static class ModuleHelperPackage extends PathHelperPackage {
    private final String myModuleName;

    public ModuleHelperPackage(String moduleName, String relativePath, @NotNull List<HelperDependency> dependencies) {
      super(getHelperFile(relativePath).getAbsolutePath(), dependencies);
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

    public ScriptPythonHelper(String script, File pythonPath, @NotNull List<HelperDependency> dependencies) {
      super(new File(pythonPath, script).getAbsolutePath(), dependencies);
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

  private static final class HelperDependency {
    private static final String THRIFTPY = "thriftpy";

    @NotNull
    private final String myPythonPath;

    private HelperDependency(@NotNull String pythonPath) {myPythonPath = pythonPath;}

    public void addToPythonPath(@NotNull Map<String, String> environment) {
      PythonEnvUtil.addToPythonPath(environment, myPythonPath);
    }

    @NotNull
    public List<String> getPythonPathEntries() {
      return Collections.singletonList(myPythonPath);
    }

    @NotNull
    private static List<HelperDependency> findThirdPartyDependencies(String... dependencies) {
      if (dependencies == null) {
        return Collections.emptyList();
      }
      return ContainerUtil.map(dependencies, s -> getThirdPartyDependency(s));
    }

    @NotNull
    private static HelperDependency getThirdPartyDependency(@NotNull String name) {
      String path = new File(getHelpersThirdPartyDir(), name).getAbsolutePath();
      return new HelperDependency(path);
    }

    @NotNull
    private static File getHelpersThirdPartyDir() {
      return getHelperFile("third_party");
    }
  }

  @NotNull
  @Override
  public String getPythonPathEntry() {
    return myModule.getPythonPathEntry();
  }

  @Override
  public @NotNull List<String> getPythonPathEntries() {
    return myModule.getPythonPathEntries();
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
