package com.intellij.openapi.vcs.changes;

import org.jetbrains.annotations.Nullable;

/**
 * only to be used by {@link ChangeProvider} in order to create IDEA's peer changelist
 * in response to finding not registered VCS native list
 * it can NOT be done through {@link ChangeListManager} interface; it is for external/IDEA user modifications
 */
public interface ChangeListManagerGate {
  @Nullable
  LocalChangeList findChangeList(final String name);
  LocalChangeList addChangeList(final String name, final String comment);
  LocalChangeList findOrCreateList(final String name, final String comment);

  void editComment(final String name, final String comment);
}
