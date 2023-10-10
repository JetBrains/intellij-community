// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Property;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemDependent;

@State(name = "PyCondaPackageService", storages = @Storage(value = "conda_packages.xml", roamingType = RoamingType.DISABLED))
public class PyCondaPackageService implements PersistentStateComponent<PyCondaPackageService> {
  @Nullable @SystemDependent @Property private String PREFERRED_CONDA_PATH = null;

  private static final Logger LOG = Logger.getInstance(PyCondaPackageService.class);

  @Override
  public PyCondaPackageService getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull PyCondaPackageService state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static PyCondaPackageService getInstance() {
    return ApplicationManager.getApplication().getService(PyCondaPackageService.class);
  }

  @Nullable
  @SystemDependent
  public static String getCondaExecutable(@Nullable String sdkPath) {
    if (sdkPath != null) {
      String condaPath = CondaExecutablesLocator.findCondaExecutableRelativeToEnv(sdkPath);
      if (condaPath != null) {
        LOG.info("Using " + condaPath + " as a conda executable for " + sdkPath + " (found as a relative to the env)");
        return condaPath;
      }
    }

    final String preferredCondaPath = getInstance().getPreferredCondaPath();
    if (StringUtil.isNotEmpty(preferredCondaPath)) {
      final String forSdkPath = sdkPath == null ? "" : " for " + sdkPath;
      LOG.info("Using " + preferredCondaPath + " as a conda executable" + forSdkPath + " (specified as a preferred conda path)");
      return preferredCondaPath;
    }

    return CondaExecutablesLocator.getSystemCondaExecutable();
  }

  @Nullable @SystemDependent String getPreferredCondaPath() {
    return PREFERRED_CONDA_PATH;
  }

  public static void onCondaEnvCreated(@NotNull @SystemDependent String condaExecutable) {
    getInstance().PREFERRED_CONDA_PATH = condaExecutable;
  }
}
