package com.jetbrains.python.testing;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.xmlb.XmlSerializerUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

  public Map <String, Boolean> SDK_TO_PYTEST;
  public Map <String, Boolean> SDK_TO_NOSETEST;
  public Map <String, Boolean> SDK_TO_ATTEST;

  public Set<String> PROCESSED_SDK;

  public TestRunnerService() {
    SDK_TO_PYTEST = new HashMap<String, Boolean>();
    SDK_TO_NOSETEST = new HashMap<String, Boolean>();
    SDK_TO_ATTEST = new HashMap<String, Boolean>();
    PROCESSED_SDK = new HashSet<String>();
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

  public void pyTestInstalled(boolean installed, String sdkHome) {
    SDK_TO_PYTEST.put(sdkHome, installed);
  }

  public boolean isPyTestInstalled(String sdkHome) {
    Boolean isInstalled = SDK_TO_PYTEST.get(sdkHome);
    return isInstalled == null? true: isInstalled;
  }

  public void noseTestInstalled(boolean installed, String sdkHome) {
    SDK_TO_NOSETEST.put(sdkHome, installed);
  }

  public boolean isNoseTestInstalled(String sdkHome) {
    Boolean isInstalled = SDK_TO_NOSETEST.get(sdkHome);
    return isInstalled == null? true: isInstalled;
  }

  public void atTestInstalled(boolean installed, String sdkHome) {
    SDK_TO_ATTEST.put(sdkHome, installed);
  }

  public boolean isAtTestInstalled(String sdkHome) {
    Boolean isInstalled = SDK_TO_ATTEST.get(sdkHome);
    return isInstalled == null? true: isInstalled;
  }

  public void addSdk(String sdkHome) {
    PROCESSED_SDK.add(sdkHome);
  }

  public Set getSdks() {
    return PROCESSED_SDK;
  }
}
