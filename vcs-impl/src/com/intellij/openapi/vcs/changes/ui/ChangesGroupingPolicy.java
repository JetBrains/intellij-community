package com.intellij.openapi.vcs.changes.ui;

import org.jetbrains.annotations.Nullable;

public interface ChangesGroupingPolicy {
  @Nullable
  ChangesBrowserNode getParentNodeFor(final ChangesBrowserNode node, final ChangesBrowserNode rootNode);
}