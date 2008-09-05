package com.intellij.openapi.vcs.changes;

import java.util.List;

public interface LocalChangeListsLoadedListener {
  void processLoadedLists(final List<LocalChangeList> lists);
}
