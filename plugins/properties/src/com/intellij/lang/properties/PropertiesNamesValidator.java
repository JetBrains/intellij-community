// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;


public class PropertiesNamesValidator implements NamesValidator {
  @Override
  public boolean isKeyword(@NotNull final String name, final Project project) {
    return false;
  }

  @Override
  public boolean isIdentifier(@NotNull final String name, final Project project) {
    return true;
  }
}
