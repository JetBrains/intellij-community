// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties;

import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;


public class PropertiesNamesValidator implements NamesValidator {
  @Override
  public boolean isKeyword(final @NotNull String name, final Project project) {
    return false;
  }

  @Override
  public boolean isIdentifier(final @NotNull String name, final Project project) {
    return true;
  }
}
