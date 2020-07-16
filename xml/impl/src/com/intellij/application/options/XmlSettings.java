// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
@State(name = "XmlSettings", storages = @Storage("editor.xml"))
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
  public void loadState(@NotNull final XmlSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
