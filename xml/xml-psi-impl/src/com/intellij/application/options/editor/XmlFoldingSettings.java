// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.editor;

import com.intellij.lang.XmlCodeFoldingSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

@State(name = "XmlFoldingSettings", storages = @Storage("editor.xml"), category = SettingsCategory.CODE)
public final class XmlFoldingSettings implements XmlCodeFoldingSettings, PersistentStateComponent<XmlFoldingSettings.State> {
  private final XmlFoldingSettings.State myState = new State();

  public static XmlFoldingSettings getInstance() {
    return ApplicationManager.getApplication().getService(XmlFoldingSettings.class);
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
  public void loadState(@NotNull State state) {
    XmlSerializerUtil.copyBean(state, myState);
  }

  public static final class State {
    public boolean COLLAPSE_XML_TAGS;
    public boolean COLLAPSE_HTML_STYLE_ATTRIBUTE = true;
    public boolean COLLAPSE_ENTITIES = true;
    public boolean COLLAPSE_DATA_URI = true;
  }
}