package com.jetbrains.python.testing;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.xmlb.XmlSerializerUtil;

import java.util.Map;
import java.util.Set;

/**
 * User: catherine
 */
@State(
  name = "TestFrameworkService",
  storages = {
    @Storage(
      file = "$APP_CONFIG$/other.xml"
    )}
)
public class TestFrameworkService implements PersistentStateComponent<TestFrameworkService> {
  public static TestFrameworkService getInstance() {
    return ServiceManager.getService(TestFrameworkService.class);
  }

  public Map <String, Boolean> SDK_TO_PYTEST = new HashMap<String, Boolean>();;
  public Map <String, Boolean> SDK_TO_NOSETEST = new HashMap<String, Boolean>();;
  public Map <String, Boolean> SDK_TO_ATTEST = new HashMap<String, Boolean>();;

  public Set<String> PROCESSED_SDK = new HashSet<String>();;

  public TestFrameworkService() {
    //SDK_TO_PYTEST = new HashMap<String, Boolean>();
    //SDK_TO_NOSETEST = new HashMap<String, Boolean>();
    //SDK_TO_ATTEST = new HashMap<String, Boolean>();
    //PROCESSED_SDK = new HashSet<String>();
  }

  @Override
  public TestFrameworkService getState() {
    return this;
  }

  @Override
  public void loadState(TestFrameworkService state) {
    XmlSerializerUtil.copyBean(state, this);
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

  public Set<String> getSdks() {
    return PROCESSED_SDK;
  }

  public void testInstalled(boolean installed, String sdkHome, String name) {
    if (name.equals("nosetest"))
      noseTestInstalled(installed, sdkHome);
    else if (name.equals("pytest"))
      pyTestInstalled(installed, sdkHome);
    else if (name.equals("attest"))
      atTestInstalled(installed, sdkHome);
  }
}
