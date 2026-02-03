// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.integrate;

import org.jetbrains.annotations.NotNull;

public enum LocalChangesAction {
  cancel("Cancel"),
  continueMerge("Continue merge"),
  shelve("Shelve local changes"),
  inspect("Inspect changes");

  private final @NotNull String myTitle;

  LocalChangesAction(@NotNull String title) {
    myTitle = title;
  }

  @Override
  public String toString() {
    return myTitle;
  }
}
