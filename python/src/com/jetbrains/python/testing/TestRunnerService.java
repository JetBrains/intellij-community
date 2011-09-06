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
      @Storage( file = "$PROJECT_FILE$"),
      @Storage( file = "$PROJECT_CONFIG_DIR$/testrunner.xml", scheme = StorageScheme.DIRECTORY_BASED)
      }
)
public class TestRunnerService implements PersistentStateComponent<TestRunnerService> {
  private List<String> myConfigurations = new ArrayList<String>();
  public String PROJECT_CONFIGURATION = PythonTestConfigurationsModel.PYTHONS_UNITTEST_NAME;

  public String PY_TEST_INSTALLED = "False";
  public String NOSE_TEST_INSTALLED = "False";
  public String AT_TEST_INSTALLED = "False";

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

  public void pyTestInstalled(String installed) {
    PY_TEST_INSTALLED = installed;
  }

  public String isPyTestInstalled() {
    return PY_TEST_INSTALLED;
  }

  public void noseTestInstalled(String installed) {
    NOSE_TEST_INSTALLED = installed;
  }

  public String isNoseTestInstalled() {
    return NOSE_TEST_INSTALLED;
  }

  public void atTestInstalled(String installed) {
    AT_TEST_INSTALLED = installed;
  }

  public String isAtTestInstalled() {
    return AT_TEST_INSTALLED;
  }
}
