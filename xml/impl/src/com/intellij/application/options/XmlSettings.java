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
package com.intellij.application.options;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;

/**
 * @author Dmitry Avdeev
 */
@State(
  name = "XmlSettings",
  storages = @Storage("editor.codeinsight.xml")
)
public class XmlSettings implements PersistentStateComponent<XmlSettings> {
  public boolean SHOW_XML_ADD_IMPORT_HINTS = true;

  public static XmlSettings getInstance() {
    return ServiceManager.getService(XmlSettings.class);
  }

  @Override
  public XmlSettings getState() {
    return this;
  }

  @Override
  public void loadState(final XmlSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
