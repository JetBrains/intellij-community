// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties;

import com.intellij.codeInspection.LocalInspectionTool;
import org.jetbrains.annotations.NotNull;

public abstract class PropertiesInspectionBase extends LocalInspectionTool {
  @Override
  public @NotNull String getGroupDisplayName() {
    return PropertiesBundle.message("properties.files.inspection.group.display.name");
  }
}
