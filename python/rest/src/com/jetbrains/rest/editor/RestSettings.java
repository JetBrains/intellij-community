// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.rest.editor;

import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import static com.jetbrains.rest.editor.RestConfigurable.JAVA_FX;

@State(name = "RestSettings", storages = @Storage(value="other.xml", roamingType = RoamingType.DISABLED))
public class RestSettings implements PersistentStateComponent<RestSettings> {
  @NotNull private String myCurrentPanel = JAVA_FX;

  @NotNull
  public String getCurrentPanel() {
    return myCurrentPanel;
  }

  public void setCurrentPanel(@NotNull String currentPanel) {
    myCurrentPanel = currentPanel;
  }

  public static RestSettings getInstance() {
    return ServiceManager.getService(RestSettings.class);
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
