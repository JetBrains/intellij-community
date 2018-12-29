// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.config;

import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.ide.util.PropertiesComponent;

public class PasswordConversionEnforcer implements ApplicationInitializedListener {

  private static final String ENFORCED = "tasks.pass.word.conversion.enforced";

  @Override
  public void componentsInitialized() {
    if (!PropertiesComponent.getInstance().isValueSet(ENFORCED)) {
      RecentTaskRepositories.getInstance().getState();
      PropertiesComponent.getInstance().setValue(ENFORCED, true);
    }
  }
}
