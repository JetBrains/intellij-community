package com.intellij.openapi.vcs.changes;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import com.intellij.util.containers.MultiMap;

public interface ChangeListsWriteOperations {
  @Nullable
  String setDefault(String name);
  boolean setReadOnly(String name, boolean value);
  LocalChangeList addChangeList(@NotNull String name, @Nullable String description);
  boolean removeChangeList(@NotNull String name);
  @Nullable
  MultiMap<LocalChangeList, Change> moveChangesTo(String name, Change[] changes);
  boolean editName(@NotNull String fromName, @NotNull String toName);
  @Nullable
  String editComment(@NotNull String fromName, final String newComment);
}
