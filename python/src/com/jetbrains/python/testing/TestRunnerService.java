// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing;

import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.module.Module;
import com.jetbrains.python.defaultProjectAwareService.PyDefaultProjectAwareModuleConfiguratorImpl;
import com.jetbrains.python.defaultProjectAwareService.PyDefaultProjectAwareService;
import com.jetbrains.python.defaultProjectAwareService.PyDefaultProjectAwareServiceClasses;
import com.jetbrains.python.defaultProjectAwareService.PyDefaultProjectAwareServiceModuleConfigurator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class TestRunnerService
  extends
  PyDefaultProjectAwareService<TestRunnerService.ServiceState, TestRunnerService, TestRunnerService.AppService, TestRunnerService.ModuleService> {

  private static final PyDefaultProjectAwareServiceClasses<ServiceState, TestRunnerService, AppService, ModuleService>
    SERVICE_CLASSES = new PyDefaultProjectAwareServiceClasses<>(AppService.class, ModuleService.class);
  private static final TestRunnerDetector DETECTOR = new TestRunnerDetector();

  protected TestRunnerService() {
    super(new ServiceState());
  }


  public final @NotNull PyAbstractTestFactory<?> getSelectedFactory() {
    return PyTestsSharedKt.getFactoryByIdOrDefault(getProjectConfiguration());
  }

  public final void setSelectedFactory(@NotNull PyAbstractTestFactory<?> factory) {
    setProjectConfiguration(factory.getId());
  }

  public static @NotNull TestRunnerService getInstance(@Nullable Module module) {
    return SERVICE_CLASSES.getService(module);
  }

  public static @NotNull PyDefaultProjectAwareServiceModuleConfigurator getConfigurator() {
    return new PyDefaultProjectAwareModuleConfiguratorImpl<>(SERVICE_CLASSES, DETECTOR);
  }

  /**
   * Use {@link #setSelectedFactory(PyAbstractTestFactory)} (String)}
   */
  public final void setProjectConfiguration(@NotNull String projectConfiguration) {
    getState().PROJECT_TEST_RUNNER = projectConfiguration;
  }

  /**
   * Use {@link #getSelectedFactory()} instead
   */
  public final @NotNull String getProjectConfiguration() {
    return getState().PROJECT_TEST_RUNNER;
  }

  static final class ServiceState {
    public @NotNull String PROJECT_TEST_RUNNER;

    ServiceState(@NotNull String projectTestRunner) {
      assert !projectTestRunner.isEmpty();
      PROJECT_TEST_RUNNER = projectTestRunner;
    }

    ServiceState() {
      this(PythonTestConfigurationType.getInstance().getAutoDetectFactory().getId());
    }
  }


  @State(name = "AppTestRunnerService", storages = @Storage("TestRunnerService.xml"), category = SettingsCategory.TOOLS)
  static final class AppService extends TestRunnerService {
  }

  @State(name = "TestRunnerService")
  static final class ModuleService extends TestRunnerService {
  }
}
