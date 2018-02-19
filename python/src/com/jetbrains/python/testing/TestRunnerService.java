// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@State(name = "TestRunnerService")
public class TestRunnerService implements PersistentStateComponent<TestRunnerService> {
  private final List<String> myConfigurations = new ArrayList<>();
  public String PROJECT_TEST_RUNNER = "";

  public TestRunnerService() {
    myConfigurations.add(PythonTestConfigurationsModel.PYTHONS_UNITTEST_NAME);
    for (final String framework : PyTestFrameworkService.getFrameworkNamesArray()) {
      myConfigurations.add(PyTestFrameworkService.getSdkReadableNameByFramework(framework));
    }
  }

  public List<String> getConfigurations() {
    return myConfigurations;
  }

  @Override
  public TestRunnerService getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull TestRunnerService state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public void setProjectConfiguration(String projectConfiguration) {
    PROJECT_TEST_RUNNER = projectConfiguration;
  }

  public static TestRunnerService getInstance(@NotNull Module module) {
    return ModuleServiceManager.getService(module, TestRunnerService.class);
  }

  public String getProjectConfiguration() {
    return PROJECT_TEST_RUNNER.isEmpty() ? PythonTestConfigurationsModel.PYTHONS_UNITTEST_NAME : PROJECT_TEST_RUNNER;
  }

}
