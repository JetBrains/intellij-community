// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.auth;

import org.jetbrains.annotations.NotNull;

public enum AcceptResult {

  REJECTED("r"),
  ACCEPTED_TEMPORARILY("t"),
  ACCEPTED_PERMANENTLY("p");

  // cache all values as values() method returns new array on each call
  private static final AcceptResult[] allValues = values();

  private final @NotNull String code;

  AcceptResult(@NotNull String code) {
    this.code = code;
  }

  @Override
  public String toString() {
    return code;
  }

  public static @NotNull AcceptResult from(int value) {
    if (value < 0 || value >= allValues.length) {
      throw new IllegalArgumentException("Unknown AcceptResult - " + value);
    }

    return allValues[value];
  }
}
