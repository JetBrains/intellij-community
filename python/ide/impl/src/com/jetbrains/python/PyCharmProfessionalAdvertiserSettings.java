// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
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
