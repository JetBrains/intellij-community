// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.numpy.documentation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * @author avereshchagin
 */
public class NumPyDocStringParameter {
  private final String myName;
  private final String myType;
  private final String myDescription;

  public NumPyDocStringParameter(@NotNull String name, @Nullable String type, @Nullable String description) {
    myName = name;
    myType = type;
    myDescription = description;
  }

  public String getName() {
    return myName;
  }

  public String getType() {
    return myType;
  }

  public String getDescription() {
    return myDescription;
  }
}
