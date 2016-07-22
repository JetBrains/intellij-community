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
package com.jetbrains.python.sdk;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

@State(name = "PySdkService", storages = @Storage("removedInterpreters.xml"))
public class PySdkService implements PersistentStateComponent<PySdkService> {

  public static PySdkService getInstance() {
    return ServiceManager.getService(PySdkService.class);
  }

  public Set<String> REMOVED_SDKS = new HashSet<>();
  public Set<String> ADDED_SDKS = new HashSet<>();

  public void removeSdk(@NotNull final Sdk sdk) {
    final String homePath = sdk.getHomePath();
    if (ADDED_SDKS.contains(homePath))
      ADDED_SDKS.remove(homePath);
    REMOVED_SDKS.add(homePath);
  }

  public void addSdk(@NotNull final Sdk sdk) {
    ADDED_SDKS.add(sdk.getHomePath());
  }

  public Set<String> getAddedSdks() {
    return ADDED_SDKS;
  }

  public void restoreSdk(@NotNull final Sdk sdk) {
    final String homePath = sdk.getHomePath();
    if (REMOVED_SDKS.contains(homePath)) {
      REMOVED_SDKS.remove(homePath);
    }
  }

  public boolean isRemoved(@NotNull final Sdk sdk) {
    final String homePath = sdk.getHomePath();
    return REMOVED_SDKS.contains(homePath);
  }

  public void solidifySdk(@NotNull final Sdk sdk) {
    final String homePath = sdk.getHomePath();
    if (ADDED_SDKS.contains(homePath)) {
      ADDED_SDKS.remove(homePath);
    }
  }

  @Override
  public PySdkService getState() {
    return this;
  }

  @Override
  public void loadState(PySdkService state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
