// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.config;

import com.intellij.configurationStore.SettingsSavingComponentJavaAdapter;
import com.intellij.ide.util.PropertiesComponent;

final class PasswordConversionEnforcer implements SettingsSavingComponentJavaAdapter {
  private static final String ENFORCED = "tasks.pass.word.conversion.enforced";
  private boolean isDone;

  @Override
  public void doSave() {
    if (isDone) {
      return;
    }

    isDone = true;

    PropertiesComponent propertyComponent = PropertiesComponent.getInstance();
    if (!propertyComponent.isValueSet(ENFORCED)) {
      RecentTaskRepositories.getInstance();
      propertyComponent.setValue(ENFORCED, true);
    }
  }
}
