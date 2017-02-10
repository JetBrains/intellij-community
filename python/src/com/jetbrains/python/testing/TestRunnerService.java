/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.testing;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * User: catherine
 */
@State(name = "TestRunnerService")
public class TestRunnerService implements PersistentStateComponent<TestRunnerService> {
  private List<String> myConfigurations = new ArrayList<>();
  public String PROJECT_TEST_RUNNER = "";

  public TestRunnerService() {
    myConfigurations.add(PythonTestConfigurationsModel.PYTHONS_UNITTEST_NAME);
    myConfigurations.add(PythonTestConfigurationsModel.PYTHONS_NOSETEST_NAME);
    myConfigurations.add(PythonTestConfigurationsModel.PY_TEST_NAME);
    myConfigurations.add(PythonTestConfigurationsModel.PYTHONS_ATTEST_NAME);
  }

  public List<String> getConfigurations() {
    return myConfigurations;
  }

  @Override
  public TestRunnerService getState() {
    return this;
  }

  @Override
  public void loadState(TestRunnerService state) {
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
