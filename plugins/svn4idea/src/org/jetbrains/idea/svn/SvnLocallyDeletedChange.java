// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.LocallyDeletedChange;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static org.jetbrains.idea.svn.SvnBundle.message;

public class SvnLocallyDeletedChange extends LocallyDeletedChange {
  private final @NotNull ConflictState myConflictState;

  public SvnLocallyDeletedChange(@NotNull FilePath path, @NotNull ConflictState state) {
    super(path);
    myConflictState = state;
  }

  @Override
  public Icon getAddIcon() {
    return myConflictState.getIcon();
  }

  @Override
  public String getDescription() {
    String description = myConflictState.getDescription();

    return description != null ? message("label.locally.deleted.file.has.conflicts", description) : null;
  }

  public @NotNull ConflictState getConflictState() {
    return myConflictState;
  }
}
