/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.committed.ChangesBunch;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class CachedProvider implements BunchProvider {
  private final Iterator<ChangesBunch> myIterator;
  private long myEarliestKeepedRevision;
  protected ChangesBunch myAlreadyReaded;
  protected Origin myOrigin;

  private boolean myHadBeenAccessed;

  protected CachedProvider(final Iterator<ChangesBunch> iterator, final Origin origin) {
    myIterator = iterator;
    myEarliestKeepedRevision = -1;
    myAlreadyReaded = null;
    myOrigin = origin;
  }

  public abstract void doCacheUpdate(final List<List<Fragment>> fragments);

  protected static List<CommittedChangeList> getAllBeforeVisuallyCached(final List<List<Fragment>> fragmentsListList) {
    final List<CommittedChangeList> lists = new ArrayList<>();
    // take those _after_ committed
    for (List<Fragment> fragmentList : fragmentsListList) {
      for (Fragment fragment : fragmentList) {
        if (Origin.VISUAL.equals(fragment.getOrigin())) {
          break;
        }
        lists.addAll(fragment.getList());
      }
    }

    return lists;
  }

  public long getEarliestRevision() {
    if (myEarliestKeepedRevision == -1) {
      try {
        while (myIterator.hasNext()) {
          final ChangesBunch changesBunch = myIterator.next();
          if (changesBunch == null) {
            break;
          }
          addToLoaded(changesBunch);
          final List<CommittedChangeList> list = myAlreadyReaded.getList();
          if (! list.isEmpty()) {
            myEarliestKeepedRevision = list.get(0).getNumber();
            break;
          }
        }
      } catch (SwitchRevisionsProviderException e) {
        // means that committed cache now should be queried instead of internally cached
        // just return -1 -> queries will be redirected
        myEarliestKeepedRevision = -1;
      }
    }
    return myEarliestKeepedRevision;
  }

  protected void addToLoaded(final ChangesBunch loaded) {
    myAlreadyReaded = loaded;
  }

  @Nullable
  public Fragment getEarliestBunchInInterval(final long earliestRevision, final long oldestRevision, final int desirableSize,
                                             final boolean includeYoungest, final boolean includeOldest) {
    if ((earliestRevision > getEarliestRevision()) || (earliestRevision == -1)) {
      if (myAlreadyReaded == null) {
        return null;
      }
      // just return first
      return createFromLoaded(myAlreadyReaded, earliestRevision, oldestRevision, desirableSize, includeYoungest, includeOldest, false);
    }

      final ChangesBunch loadedBunch = myAlreadyReaded;

      final List<CommittedChangeList> list = loadedBunch.getList();
      if (list.isEmpty()) {
        return null;
      }
      final long oldest = list.get(list.size() - 1).getNumber();

      if ((! includeYoungest) && (oldest == earliestRevision)) {
        return packNext(earliestRevision, oldestRevision, desirableSize, includeOldest, loadedBunch.isConsistentWithPrevious());
      //} else if ((oldest <= earliestRevision) && (youngest >= earliestRevision)) {
      } else if (oldest <= earliestRevision) {
        return createFromLoaded(loadedBunch, earliestRevision, oldestRevision, desirableSize, includeYoungest, includeOldest, false);
      }

    return null;
  }

  private Fragment packNext(final long earliestRevision, final long oldestRevision, final int desirableSize, final boolean includeOldest,
                            final boolean wasConsistentWithPrevious) {
    try {
      if (myIterator.hasNext()) {
        final ChangesBunch changesBunch = myIterator.next();
        if (changesBunch == null) {
          return null;
        }
        addToLoaded(changesBunch);

        // there is no earliestRevision
        // always consistent since there were exactly 'earliest revision' in previous potion
       return createFromLoaded(changesBunch, earliestRevision, oldestRevision, desirableSize, true, includeOldest, wasConsistentWithPrevious);
      }
    } catch (SwitchRevisionsProviderException e) {
      // means that committed cache now should be queried instead of internally cached
      // just return null -> queries will be redirected
    }
    return null;
  }

  public boolean hadBeenSuccessfullyAccessed() {
    return myHadBeenAccessed;
  }

  @Nullable
  private Fragment createFromLoaded(final ChangesBunch loadedBunch, final long earliestRevision, final long oldestRevision,
                                    final int desirableSize, final boolean includeYoungest, final boolean includeOldest, final boolean consistent) {
    boolean consistentWithPrevious = loadedBunch.isConsistentWithPrevious();
    boolean consistentWithYounger = consistent;

    final List<CommittedChangeList> list = loadedBunch.getList();

    final List<CommittedChangeList> sublist = new ArrayList<>();
    for (int i = 0; i < list.size(); i++) {
      final CommittedChangeList changeList = list.get(i);
      if ((! includeOldest) && (changeList.getNumber() == oldestRevision)) {
        continue;
      }
      if (changeList.getNumber() == earliestRevision) {
        consistentWithYounger = true;
      }
      if ((earliestRevision == -1) || (changeList.getNumber() < earliestRevision) || (includeYoungest && (changeList.getNumber() == earliestRevision))) {
        sublist.add(changeList);
      }
      if ((sublist.size() == desirableSize) || (changeList.getNumber() < oldestRevision)) {
        if (! consistentWithPrevious) {
          consistentWithPrevious = (i > 0);
        }
        break;
      }
    }
    if (! myHadBeenAccessed) {
      myHadBeenAccessed = (! sublist.isEmpty());
    }
    return (sublist.isEmpty()) ? null : new Fragment(myOrigin, sublist, consistentWithPrevious, consistentWithYounger, loadedBunch);
  }

  public boolean isEmpty() {
    return getEarliestRevision() == -1;
  }
}
