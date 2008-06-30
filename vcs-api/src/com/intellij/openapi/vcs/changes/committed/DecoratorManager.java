package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.util.Getter;

public interface DecoratorManager {
  void install(final CommittedChangeListDecorator decorator);
  void remove(final CommittedChangeListDecorator decorator);
  void repaintTree();
  void reportLoadedLists(final CommittedChangeListsListener listener);

  // these two for visibility
  void fireVisibilityCalculation();
  void registerCompositePart(final Getter<Boolean> visibilityGetter);
}
