// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.designer.propertyTable;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Alexander Lobas
 */
public final class PropertyTableTab {
  private final String myKey;
  private final @Nls String myDescription;
  private final Icon myIcon;

  public PropertyTableTab(@NotNull String key, @NotNull @Nls String description, @NotNull Icon icon) {
    myKey = key;
    myDescription = description;
    myIcon = icon;
  }

  public @NotNull String getKey() {
    return myKey;
  }

  public @NotNull @Nls String getDescription() {
    return myDescription;
  }

  public @NotNull Icon getIcon() {
    return myIcon;
  }
}