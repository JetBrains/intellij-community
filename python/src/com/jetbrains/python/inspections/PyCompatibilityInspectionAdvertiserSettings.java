// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Nullable
  @Override
  public PyCompatibilityInspectionAdvertiserSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull PyCompatibilityInspectionAdvertiserSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
