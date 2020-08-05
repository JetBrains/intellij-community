// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "PythonFoldingSettings", storages = @Storage("editor.xml"))
public class PythonFoldingSettings implements PersistentStateComponent<PythonFoldingSettings> {
  public boolean COLLAPSE_LONG_STRINGS;
  public boolean COLLAPSE_LONG_COLLECTIONS;
  public boolean COLLAPSE_SEQUENTIAL_COMMENTS;

  @Nullable
  @Override
  public PythonFoldingSettings getState() {
    return this;
  }

  @NotNull
  public static PythonFoldingSettings getInstance() {
    return ServiceManager.getService(PythonFoldingSettings.class);
  }

  @Override
  public void loadState(@NotNull PythonFoldingSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public boolean isCollapseLongStrings() {
    return COLLAPSE_LONG_STRINGS;
  }

  public boolean isCollapseLongCollections() {
    return COLLAPSE_LONG_COLLECTIONS;
  }

  public boolean isCollapseSequentialComments() {
    return COLLAPSE_SEQUENTIAL_COMMENTS;
  }

}
