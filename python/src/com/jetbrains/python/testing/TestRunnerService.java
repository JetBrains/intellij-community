// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing;

import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.module.Module;
import com.jetbrains.python.defaultProjectAwareService.PyDefaultProjectAwareService;
import com.jetbrains.python.defaultProjectAwareService.PyDefaultProjectAwareServiceModuleConfigurator;
import com.jetbrains.python.defaultProjectAwareService.PyDefaultProjectAwareModuleConfiguratorImpl;
import com.jetbrains.python.defaultProjectAwareService.PyDefaultProjectAwareServiceClasses;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

abstract public class TestRunnerService
  extends
  PyDefaultProjectAwareService<TestRunnerService.ServiceState, TestRunnerService, TestRunnerService.AppService, TestRunnerService.ModuleService> {

  private final static String DEFAULT_TEST_RUNNER = PythonTestConfigurationsModel.PYTHONS_UNITTEST_NAME;

  private static final PyDefaultProjectAwareServiceClasses<ServiceState, TestRunnerService, AppService, ModuleService>
    SERVICE_CLASSES = new PyDefaultProjectAwareServiceClasses<>(AppService.class, ModuleService.class);
  private static final TestRunnerDetector DETECTOR = new TestRunnerDetector();
  private final List<String> myConfigurations = new ArrayList<>();

  protected TestRunnerService() {
    super(new ServiceState());
    myConfigurations.add(PythonTestConfigurationsModel.PYTHONS_UNITTEST_NAME);
    for (final String framework : PyTestFrameworkService.getFrameworkNamesArray()) {
      myConfigurations.add(PyTestFrameworkService.getSdkReadableNameByFramework(framework));
    }
  }

  @NotNull
  public final List<String> getConfigurations() {
    return myConfigurations;
  }


  public final void setProjectConfiguration(@NotNull String projectConfiguration) {
    getState().PROJECT_TEST_RUNNER = projectConfiguration;
  }

  @NotNull
  public static TestRunnerService getInstance(@Nullable Module module) {
    return SERVICE_CLASSES.getService(module);
  }

  @NotNull
  public static PyDefaultProjectAwareServiceModuleConfigurator getConfigurator() {
    return new PyDefaultProjectAwareModuleConfiguratorImpl<>(SERVICE_CLASSES, DETECTOR);
  }

  @NotNull
  public final String getProjectConfiguration() {
    String runnerFromConfig = getState().PROJECT_TEST_RUNNER;
    // User may have "py.test" in her .xml file
    if ("py.test".equals(runnerFromConfig)) {
      runnerFromConfig = "pytest";
    }
    return runnerFromConfig;
  }

  static final class ServiceState {
    @NotNull
    public String PROJECT_TEST_RUNNER;

    ServiceState(@NotNull String projectTestRunner) {
      assert !projectTestRunner.isEmpty();
      PROJECT_TEST_RUNNER = projectTestRunner;
    }

    ServiceState() {
      this(DEFAULT_TEST_RUNNER);
    }
  }


  @State(name = "AppTestRunnerService", storages = @Storage("TestRunnerService.xml"))
  static final class AppService extends TestRunnerService {
  }

  @State(name = "TestRunnerService")
  static final class ModuleService extends TestRunnerService {
  }
}
