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

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author vlan
 */
@State(name = "PackageRequirementsSettings")
public class PyPackageRequirementsSettings implements PersistentStateComponent<PyPackageRequirementsSettings> {
  public static final String DEFAULT_REQUIREMENTS_PATH = "requirements.txt";

  @NotNull
  private String myRequirementsPath = DEFAULT_REQUIREMENTS_PATH;

  @NotNull
  @Override
  public PyPackageRequirementsSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull PyPackageRequirementsSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @NotNull
  public String getRequirementsPath() {
    return myRequirementsPath;
  }

  public void setRequirementsPath(@NotNull String path) {
    myRequirementsPath = path;
  }

  @NotNull
  public static PyPackageRequirementsSettings getInstance(@NotNull Module module) {
    return ModuleServiceManager.getService(module, PyPackageRequirementsSettings.class);
  }
}
