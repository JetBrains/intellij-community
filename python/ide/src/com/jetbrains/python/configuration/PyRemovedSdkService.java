/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.configuration;

import com.intellij.openapi.components.*;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

@State(
  name = "PyRemovedSdkService",
  storages = {
    @Storage(
      file = StoragePathMacros.APP_CONFIG + "/removedInterpreters.xml"
    )}
)
public class PyRemovedSdkService implements PersistentStateComponent<PyRemovedSdkService> {

  public static PyRemovedSdkService getInstance() {
    return ServiceManager.getService(PyRemovedSdkService.class);
  }

  public Set<String> REMOVED_SDKS = new HashSet<String>();

  public void removeSdk(@NotNull final Sdk sdk) {
    REMOVED_SDKS.add(sdk.getHomePath());
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

  @Override
  public PyRemovedSdkService getState() {
    return this;
  }

  @Override
  public void loadState(PyRemovedSdkService state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
