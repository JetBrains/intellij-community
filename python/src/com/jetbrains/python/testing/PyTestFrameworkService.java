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
package com.jetbrains.python.testing;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xmlb.XmlSerializerUtil;

import java.util.Map;

@State(name = "PyTestFrameworkService", storages = @Storage("other.xml"))
public class PyTestFrameworkService implements PersistentStateComponent<PyTestFrameworkService> {

  public static PyTestFrameworkService getInstance() {
    return ServiceManager.getService(PyTestFrameworkService.class);
  }

  public Map<String, Boolean> SDK_TO_PYTEST = new HashMap<>();
  public Map <String, Boolean> SDK_TO_NOSETEST = new HashMap<>();
  public Map <String, Boolean> SDK_TO_ATTEST = new HashMap<>();

  @Override
  public PyTestFrameworkService getState() {
    return this;
  }

  @Override
  public void loadState(PyTestFrameworkService state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
