// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.changes.committed.RepositoryLocationGroup;
import com.intellij.openapi.vcs.changes.committed.VcsCommittedListsZipper;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Url;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
* @author Konstantin Kolosovsky.
*/
public class SvnCommittedListsZipper implements VcsCommittedListsZipper {

  private static final Logger LOG = Logger.getInstance(SvnCommittedListsZipper.class);

  @NotNull private final SvnVcs myVcs;

  public SvnCommittedListsZipper(@NotNull SvnVcs vcs) {
    myVcs = vcs;
  }

  public Pair<List<RepositoryLocationGroup>, List<RepositoryLocation>> groupLocations(final List<RepositoryLocation> in) {
    final List<RepositoryLocationGroup> groups = new ArrayList<>();
    final List<RepositoryLocation> singles = new ArrayList<>();

    final MultiMap<Url, RepositoryLocation> map = new MultiMap<>();

    for (RepositoryLocation location : in) {
      final SvnRepositoryLocation svnLocation = (SvnRepositoryLocation) location;
      final String url = svnLocation.getURL();

      final Url root = SvnUtil.getRepositoryRoot(myVcs, url);
      if (root == null) {
        // should not occur
        LOG.info("repository root not found for location:"+ location.toPresentableString());
        singles.add(location);
      } else {
        map.putValue(root, svnLocation);
      }
    }

    final Set<Url> keys = map.keySet();
    for (Url key : keys) {
      final Collection<RepositoryLocation> repositoryLocations = map.get(key);
      if (repositoryLocations.size() == 1) {
        singles.add(repositoryLocations.iterator().next());
      } else {
        final SvnRepositoryLocationGroup group = new SvnRepositoryLocationGroup(key, repositoryLocations);
        groups.add(group);
      }
    }
    return Pair.create(groups, singles);
  }

  public CommittedChangeList zip(final RepositoryLocationGroup group, final List<CommittedChangeList> lists) {
    return new SvnChangeList(lists, new SvnRepositoryLocation(group.toPresentableString()));
  }

  public long getNumber(final CommittedChangeList list) {
    return list.getNumber();
  }
}
