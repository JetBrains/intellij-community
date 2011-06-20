package com.jetbrains.python.testing;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * User: catherine
 */
@State(name = "TestRunnerService",
      storages = {
      @Storage(id = "default", file = "$PROJECT_FILE$"),
      @Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/testrunner.xml", scheme = StorageScheme.DIRECTORY_BASED)
      }
)
public class TestRunnerService implements PersistentStateComponent<TestRunnerService> {
  private List<String> myConfigurations = new ArrayList<String>();
  public String PROJECT_CONFIGURATION = PythonTestConfigurationsModel.PYTHONS_UNITTEST_NAME;

  public TestRunnerService() {
    myConfigurations.add(PythonTestConfigurationsModel.PYTHONS_UNITTEST_NAME);
    myConfigurations.add(PythonTestConfigurationsModel.PYTHONS_NOSETEST_NAME);
    myConfigurations.add(PythonTestConfigurationsModel.PY_TEST_NAME);
    myConfigurations.add(PythonTestConfigurationsModel.PYTHONS_ATTEST_NAME);
  }

  public List<String> getConfigurations() {
    return myConfigurations;
  }
  public void registerConfiguration(final String newConfiguration) {
    myConfigurations.add(newConfiguration);
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
    PROJECT_CONFIGURATION = projectConfiguration;
  }

  public static TestRunnerService getInstance(Project project) {
    return ServiceManager.getService(project, TestRunnerService.class);
  }
  public String getProjectConfiguration() {
    return PROJECT_CONFIGURATION;
  }
}
