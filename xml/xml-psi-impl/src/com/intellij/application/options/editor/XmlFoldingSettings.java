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
package com.intellij.application.options.editor;

import com.intellij.lang.XmlCodeFoldingSettings;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

@State(name = "XmlFoldingSettings", storages = @Storage("editor.codeinsight.xml"))
public class XmlFoldingSettings implements XmlCodeFoldingSettings, PersistentStateComponent<XmlFoldingSettings.State> {
  private final XmlFoldingSettings.State myState = new State();

  public static XmlFoldingSettings getInstance() {
    return ServiceManager.getService(XmlFoldingSettings.class);
  }

  public XmlFoldingSettings() {
    // todo: remove after 2017.1 release
    CssFoldingSettings cssFoldingSettings = CssFoldingSettings.getInstance();
    if (cssFoldingSettings != null) {
      myState.COLLAPSE_DATA_URI = cssFoldingSettings.isCollapseDataUri();
    }
  }

  @Override
  public boolean isCollapseXmlTags() {
    return myState.COLLAPSE_XML_TAGS;
  }

  @Override
  public boolean isCollapseHtmlStyleAttribute() {
    return myState.COLLAPSE_HTML_STYLE_ATTRIBUTE;
  }

  @Override
  public boolean isCollapseEntities() {
    return myState.COLLAPSE_ENTITIES;
  }

  @Override
  public boolean isCollapseDataUri() {
    return myState.COLLAPSE_DATA_URI;
  }

  @Override
  @NotNull
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(State state) {
    XmlSerializerUtil.copyBean(state, myState);
  }

  public static final class State {
    public boolean COLLAPSE_XML_TAGS;
    public boolean COLLAPSE_HTML_STYLE_ATTRIBUTE = true;
    public boolean COLLAPSE_ENTITIES = true;
    public boolean COLLAPSE_DATA_URI = true;
  }
}