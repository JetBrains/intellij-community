package com.jetbrains.python.testing;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * User: catherine
 */
@State(name = "TestRunnerService",
       storages = {@Storage(file = "$MODULE_FILE$")}
)
public class TestRunnerService implements PersistentStateComponent<TestRunnerService> {
  private List<String> myConfigurations = new ArrayList<String>();
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
    return PROJECT_TEST_RUNNER;
  }

}
