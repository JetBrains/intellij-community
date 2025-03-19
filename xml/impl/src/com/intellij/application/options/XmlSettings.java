// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
@State(name = "XmlSettings", storages = @Storage("editor.xml"), category = SettingsCategory.CODE)
public class XmlSettings implements PersistentStateComponent<XmlSettings> {
  public boolean SHOW_XML_ADD_IMPORT_HINTS = true;

  public static XmlSettings getInstance() {
    return ApplicationManager.getApplication().getService(XmlSettings.class);
  }

  @Override
  public XmlSettings getState() {
    return this;
  }

  @Override
  public void loadState(final @NotNull XmlSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
