// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@State(name = "PyPackageService", storages = @Storage(value = "packages.xml", roamingType = RoamingType.DISABLED), reportStatistic = false)
@ApiStatus.Internal

public class PyPackageService implements
                              PersistentStateComponent<PyPackageService> {
  public volatile Map<String, Boolean> sdkToUsersite = new ConcurrentHashMap<>();
  public volatile List<String> additionalRepositories = ContainerUtil.createConcurrentList();
  public volatile @SystemIndependent String virtualEnvBasePath;
  public volatile Boolean PYPI_REMOVED = false;

  @Override
  public PyPackageService getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull PyPackageService state) {
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
    return ApplicationManager.getApplication().getService(PyPackageService.class);
  }

  public @Nullable @SystemIndependent String getVirtualEnvBasePath() {
    return virtualEnvBasePath;
  }

  public void setVirtualEnvBasePath(@NotNull @SystemIndependent String virtualEnvBasePath) {
    this.virtualEnvBasePath = virtualEnvBasePath;
  }
}
