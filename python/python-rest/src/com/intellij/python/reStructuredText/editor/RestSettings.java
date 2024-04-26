// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.editor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

@State(name = "RestSettings", storages = @Storage(value="other.xml", roamingType = RoamingType.DISABLED))
public class RestSettings implements PersistentStateComponent<RestSettings> {
  @NotNull private String myCurrentPanel = JBCefApp.isSupported() ? RestConfigurable.JCEF : RestConfigurable.SWING;

  @NotNull
  @NlsSafe
  public String getCurrentPanel() {
    return myCurrentPanel;
  }

  public void setCurrentPanel(@NotNull String currentPanel) {
    myCurrentPanel = currentPanel;
  }

  public static RestSettings getInstance() {
    return ApplicationManager.getApplication().getService(RestSettings.class);
  }


  @Override
  public RestSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull RestSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
