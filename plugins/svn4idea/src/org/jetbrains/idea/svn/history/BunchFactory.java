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

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class BunchFactory {
  private final LiveProvider myLiveProvider;
  private final List<Fragment> myResult;
  private final Iterator<BunchProvider> myProviderIterator;
  private BunchProvider myCurrentProvider;

  // they will move
  private int myBunchSize;
  private long myYoungest;

  public BunchFactory(final CachedProvider internallyCached, final CachedProvider visuallyCached, final LiveProvider liveProvider) {
    myLiveProvider = liveProvider;

    final List<BunchProvider> providers = new ArrayList<>();
    if (internallyCached != null) {
      providers.add(internallyCached);
    }
    if (visuallyCached != null) {
      providers.add(visuallyCached);
    }
    providers.add(myLiveProvider);

    myResult = new ArrayList<>();
    myProviderIterator = providers.iterator();
    while (myProviderIterator.hasNext()) {
      myCurrentProvider = myProviderIterator.next();
      if (! myCurrentProvider.isEmpty()) {
        break;
      }
    }
    myYoungest = -1;
  }

  public List<Fragment> goBack(final int bunchSize, final Ref<Boolean> myYoungestRead) throws VcsException {
    execute(bunchSize);
    myYoungestRead.set(myLiveProvider.isEarliestRevisionWasAccessed());
    return new ArrayList<>(myResult);
  }

  private void addToResult(final Fragment fragment) {
    if ((myBunchSize == 0) || (fragment.getList().isEmpty())) {
      return;
    }

    final List<CommittedChangeList> list = fragment.getList();

    final List<CommittedChangeList> subList = (myBunchSize >= list.size()) ? list : list.subList(0, myBunchSize);
    myResult.add(new Fragment(fragment.getOrigin(), subList, fragment.isConsistentWithOlder(), fragment.isConsistentWithYounger(),
                     fragment.getOriginBunch()));
    myBunchSize -= subList.size();
    myBunchSize = (myBunchSize < 0) ? 0 : myBunchSize;
    myYoungest = subList.get(subList.size() - 1).getNumber();
  }

  private void execute(final int bunchSize) throws VcsException {
    myBunchSize = bunchSize;
    myResult.clear();

    int defender = 1000;
    while (true) {
      while (true) {
        -- defender;
        if (defender == 0) {
          return;
        }
        ProgressManager.checkCanceled();
        final Fragment fragment = myCurrentProvider.getEarliestBunchInInterval(myYoungest, 0,
                                  (myYoungest == -1) ? myBunchSize : (myBunchSize + 1), (myYoungest == -1), true);
        if ((fragment == null) || (fragment.getList().isEmpty())) {
          // switch to next provider
          break;
        }

        ProgressManager.checkCanceled();
        final List<CommittedChangeList> bunchLists = fragment.getList();
        if (! fragment.isConsistentWithYounger()) {
          final long endRevision = bunchLists.get(0).getNumber();
          if ((endRevision < myYoungest) || (myYoungest == -1)) {
            final Fragment liveFragment = myLiveProvider.getEarliestBunchInInterval(myYoungest, endRevision,
                                  (myYoungest == -1) ? (myBunchSize + 1) : (myBunchSize + 2), (myYoungest == -1), false);
            if (liveFragment != null) {
              addToResult(liveFragment);
              if (myBunchSize == 0) {
                return;
              }
            }
          }
        }
        addToResult(fragment);
        if (myBunchSize == 0) {
          return;
        }
      }
      if (myProviderIterator.hasNext()) {
        myCurrentProvider = myProviderIterator.next();
      } else {
        break;
      }
    }
  }
}
