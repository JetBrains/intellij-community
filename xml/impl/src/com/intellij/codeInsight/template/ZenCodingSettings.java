/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInsight.template;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;

/**
 * @author Eugene.Kudelevsky
 */
@State(
  name="DuplocatorSettings",
  storages = {
    @Storage(
      id="DuplocatorSettings",
      file="$APP_CONFIG$/other.xml"
    )}
)
public class ZenCodingSettings implements PersistentStateComponent<ZenCodingSettings> {
  public boolean ENABLED = true;

  public static ZenCodingSettings getInstance() {
    return ServiceManager.getService(ZenCodingSettings.class);
  }

  public ZenCodingSettings getState() {
    return this;
  }

  public void loadState(ZenCodingSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
