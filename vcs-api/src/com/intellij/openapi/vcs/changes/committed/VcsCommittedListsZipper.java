package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;

import java.util.List;

public interface VcsCommittedListsZipper {
  Pair<List<RepositoryLocationGroup>, List<RepositoryLocation>> groupLocations(final List<RepositoryLocation> in);
  CommittedChangeList zip(final RepositoryLocationGroup group, final List<CommittedChangeList> lists);
  long getNumber(final CommittedChangeList list);
}
