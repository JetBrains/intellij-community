// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.actions;

import com.intellij.ide.actions.NewFileActionWithCategory;
import com.intellij.ide.actions.NonTrivialActionGroup;
import org.jetbrains.annotations.NotNull;

final class CreateXmlDescriptorGroup extends NonTrivialActionGroup implements NewFileActionWithCategory {
  @Override
  public @NotNull String getCategory() {
    return "XML";
  }
}
