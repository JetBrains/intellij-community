// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
@State(name = "PythonCompatibilityInspectionAdvertiser")
public class PyCompatibilityInspectionAdvertiserSettings implements PersistentStateComponent<PyCompatibilityInspectionAdvertiserSettings> {
  public int version = 0;

  @Override
  public @Nullable PyCompatibilityInspectionAdvertiserSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull PyCompatibilityInspectionAdvertiserSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
