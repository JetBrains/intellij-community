// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "PyCharmProfessionalAdvertiser")
public class PyCharmProfessionalAdvertiserSettings implements PersistentStateComponent<PyCharmProfessionalAdvertiserSettings> {
  public boolean shown = false;

  @Nullable
  @Override
  public PyCharmProfessionalAdvertiserSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull PyCharmProfessionalAdvertiserSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
