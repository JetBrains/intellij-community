package com.jetbrains.python.packaging;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: catherine
 */
@State(name = "PyPackageService",
       storages = {
           @Storage( file = "$APP_CONFIG$/packages.xml")
       }
)
public class PyPackageService implements
                              PersistentStateComponent<PyPackageService> {
  public Map<String, Boolean> sdkToUsersite = new HashMap<String, Boolean>();
  public List<String> additionalRepositories = new ArrayList<String>();
  public Map<String, String> PY_PACKAGES = new HashMap<String, String>();
  public String virtualEnvBasePath;
  
  public long LAST_TIME_CHECKED = 0;

  @Override
  public PyPackageService getState() {
    return this;
  }

  @Override
  public void loadState(PyPackageService state) {
    XmlSerializerUtil.copyBean(state, this);
  }
  
  public void addSdkToUserSite(String sdk, boolean useUsersite) {
    sdkToUsersite.put(sdk, useUsersite);
  }

  public void addRepository(String repository) {
    additionalRepositories.add(repository);
  }

  public void removeRepository(String repository) {
    if (additionalRepositories.contains(repository))
      additionalRepositories.remove(repository);
  }

  public boolean useUserSite(String sdk) {
    if (sdkToUsersite.containsKey(sdk))
      return sdkToUsersite.get(sdk);
    return false;
  }

  public static PyPackageService getInstance(Project project) {
    return ServiceManager.getService(project, PyPackageService.class);
  }

  public String getVirtualEnvBasePath() {
    return virtualEnvBasePath;
  }

  public void setVirtualEnvBasePath(String virtualEnvBasePath) {
    this.virtualEnvBasePath = virtualEnvBasePath;
  }
}
