// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.config;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.PreloadingActivity;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

final class PasswordConversionEnforcer extends PreloadingActivity {
  private static final String ENFORCED = "tasks.pass.word.conversion.enforced";

  @Override
  public void preload(@NotNull ProgressIndicator indicator) {
    PropertiesComponent propertyComponent = PropertiesComponent.getInstance();
    if (!propertyComponent.isValueSet(ENFORCED)) {
      RecentTaskRepositories.getInstance().getState();
      propertyComponent.setValue(ENFORCED, true);
    }
  }
}
