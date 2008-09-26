package com.intellij.openapi.vcs.changes;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ChangeListModification {
  LocalChangeList addChangeList(@NotNull String name, final String comment);
  void setDefaultChangeList(@NotNull LocalChangeList list);

  void removeChangeList(final LocalChangeList list);

  void moveChangesTo(final LocalChangeList list, final Change[] changes);

  // added - since ChangeListManager wouldn't pass internal lists, only copies
  boolean setReadOnly(final String name, final boolean value);

  boolean editName(@NotNull String fromName, @NotNull String toName);
  @Nullable
  String editComment(@NotNull String fromName, final String newComment);
}
