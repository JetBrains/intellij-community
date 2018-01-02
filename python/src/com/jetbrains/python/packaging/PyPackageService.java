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
package com.jetbrains.python.packaging;

import com.intellij.openapi.components.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

import java.util.List;
import java.util.Map;

@State(name = "PyPackageService", storages = @Storage(value = "packages.xml", roamingType = RoamingType.DISABLED))
public class PyPackageService implements
                              PersistentStateComponent<PyPackageService> {
  public volatile Map<String, Boolean> sdkToUsersite = ContainerUtil.newConcurrentMap();
  public volatile List<String> additionalRepositories = ContainerUtil.createConcurrentList();
  @SystemIndependent public volatile String virtualEnvBasePath;
  public volatile Boolean PYPI_REMOVED = false;

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
    if (repository == null) return;
    if (PyPIPackageUtil.isPyPIRepository(repository)) {
      PYPI_REMOVED = false;
    }
    else {
      if (!repository.endsWith("/")) repository += "/";
      additionalRepositories.add(repository);
    }
  }

  public void removeRepository(final String repository) {
    if (additionalRepositories.contains(repository))
      additionalRepositories.remove(repository);
    else if (PyPIPackageUtil.isPyPIRepository(repository)) {
      PYPI_REMOVED = true;
    }
  }

  public boolean useUserSite(String sdk) {
    if (sdkToUsersite.containsKey(sdk))
      return sdkToUsersite.get(sdk);
    return false;
  }

  public static PyPackageService getInstance() {
    return ServiceManager.getService(PyPackageService.class);
  }

  @Nullable
  @SystemIndependent
  public String getVirtualEnvBasePath() {
    return virtualEnvBasePath;
  }

  public void setVirtualEnvBasePath(@NotNull @SystemIndependent String virtualEnvBasePath) {
    this.virtualEnvBasePath = virtualEnvBasePath;
  }
}
