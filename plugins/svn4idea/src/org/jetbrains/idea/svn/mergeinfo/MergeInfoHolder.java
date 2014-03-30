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
package org.jetbrains.idea.svn.mergeinfo;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.changes.committed.CommittedChangeListsListener;
import com.intellij.openapi.vcs.changes.committed.DecoratorManager;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.dialogs.WCInfoWithBranches;
import org.jetbrains.idea.svn.dialogs.WCPaths;
import org.jetbrains.idea.svn.history.SvnChangeList;

import java.awt.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class MergeInfoHolder {
  private final DecoratorManager myManager;
  private final Consumer<Boolean> myMixedRevisionsConsumer;
  private final SvnMergeInfoCache myMergeInfoCache;

  private final static String ourIntegratedText = SvnBundle.message("committed.changes.merge.status.integrated.text");
  private final static String ourNotIntegratedText = SvnBundle.message("committed.changes.merge.status.not.integrated.text");
  private final static SimpleTextAttributes ourNotIntegratedAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, Color.RED);
  private final static SimpleTextAttributes ourIntegratedAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, Color.GREEN);
  private final static SimpleTextAttributes ourRefreshAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, Color.GRAY);
  
  // used ONLY when refresh is triggered
  private final Map<Pair<String, String>, MergeinfoCached> myCachedMap;

  private final Getter<WCInfoWithBranches> myRootGetter;
  private final Getter<WCInfoWithBranches.Branch> myBranchGetter;
  private final Getter<String> myWcPathGetter;
  private final Getter<Boolean> myEnabledHolder;
  private final MyDecorator myDecorator;

  public MergeInfoHolder(final Project project, final DecoratorManager manager, final Getter<WCInfoWithBranches> rootGetter,
                         final Getter<WCInfoWithBranches.Branch> branchGetter,
                         final Getter<String> wcPathGetter, Getter<Boolean> enabledHolder, final Consumer<Boolean> mixedRevisionsConsumer) {
    myRootGetter = rootGetter;
    myBranchGetter = branchGetter;
    myWcPathGetter = wcPathGetter;
    myEnabledHolder = enabledHolder;
    myManager = manager;
    myMixedRevisionsConsumer = mixedRevisionsConsumer;
    myMergeInfoCache = SvnMergeInfoCache.getInstance(project);
    myCachedMap = new HashMap<Pair<String, String>, MergeinfoCached>();

    myDecorator = new MyDecorator();
  }

  private MergeinfoCached getCurrentCache() {
    return myCachedMap.get(createKey(myRootGetter.get(), myBranchGetter.get()));
  }

  private boolean enabledAndGettersFilled(final boolean ignoreEnabled) {
    if ((! ignoreEnabled) && (! Boolean.TRUE.equals(myEnabledHolder.get()))) {
      return false;
    }
    return (myRootGetter.get() != null) && (myBranchGetter.get() != null) && (myWcPathGetter.get() != null);
  }

  public boolean refreshEnabled(final boolean ignoreEnabled) {
    return enabledAndGettersFilled(ignoreEnabled) && (getCurrentCache() == null);
  }

  private static Pair<String, String> createKey(final WCPaths root, final WCInfoWithBranches.Branch branch) {
    return new Pair<String, String>(root.getPath(), branch.getUrl());
  }

  public void refresh(final boolean ignoreEnabled) {
    final CommittedChangeListsListener refresher = createRefresher(ignoreEnabled);
    if (refresher != null) {
      myManager.reportLoadedLists(new MyRefresher());
    }
    myManager.repaintTree();
  }

  @Nullable
  public CommittedChangeListsListener createRefresher(final boolean ignoreEnabled) {
    if (refreshEnabled(ignoreEnabled)) {
      // on awt thread
      final MergeinfoCached state = myMergeInfoCache.getCachedState(myRootGetter.get(), myWcPathGetter.get());
      myCachedMap.put(createKey(myRootGetter.get(), myBranchGetter.get()), (state == null) ? new MergeinfoCached() :
          new MergeinfoCached(new HashMap<Long, SvnMergeInfoCache.MergeCheckResult>(state.getMap()), state.getCopyRevision()));
      myMergeInfoCache.clear(myRootGetter.get(), myWcPathGetter.get());

      return new MyRefresher();
    }
    return null;
  }

  private class MyRefresher implements CommittedChangeListsListener {
    private final WCInfoWithBranches myRefreshedRoot;
    private final WCInfoWithBranches.Branch myRefreshedBranch;
    private final String myBranchPath;

    private MyRefresher() {
      myRefreshedRoot = myRootGetter.get();
      myRefreshedBranch = myBranchGetter.get();
      myBranchPath = myWcPathGetter.get();
    }

    public void onBeforeStartReport() {
    }

    public boolean report(final CommittedChangeList list) {
      if (list instanceof SvnChangeList) {
        final SvnChangeList svnList = (SvnChangeList) list;
        final String wcPath = svnList.getWcPath() + File.separator;
        // todo check if this needed
        /*if (! myRefreshedRoot.getPath().equals(wcPath)) {
          return true;
        } */

        // prepare state. must be in non awt thread
        final SvnMergeInfoCache.MergeCheckResult checkState = myMergeInfoCache.getState(myRefreshedRoot, (SvnChangeList)list, myRefreshedBranch,
                                                                                   myBranchPath);
        // todo make batches - by 10
        final long number = list.getNumber();
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            final MergeinfoCached cachedState = myCachedMap.get(createKey(myRefreshedRoot, myRefreshedBranch));
            if (cachedState != null) {
              cachedState.getMap().put(number, checkState);
            }
            myManager.repaintTree();
          }
        });
      }
      return true;
    }

    public void onAfterEndReport() {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          myCachedMap.remove(createKey(myRefreshedRoot, myRefreshedBranch));
          updateMixedRevisionsForPanel();
          myManager.repaintTree();
        }
      });
    }
  }

  public static interface ListChecker {
    ListMergeStatus check(final CommittedChangeList list, final boolean ignoreEnabled);
  }

  class MyDecorator implements ListChecker {
    private ListMergeStatus convert(SvnMergeInfoCache.MergeCheckResult result, final boolean refreshing) {
      if (result != null) {
        if (SvnMergeInfoCache.MergeCheckResult.MERGED.equals(result)) {
          return ListMergeStatus.MERGED;
        } else if (SvnMergeInfoCache.MergeCheckResult.COMMON.equals(result)) {
          return ListMergeStatus.COMMON;
        } else {
          return ListMergeStatus.NOT_MERGED;
        }
      }
      if (refreshing) {
        return ListMergeStatus.REFRESHING;
      }
      return ListMergeStatus.ALIEN;
    }

    public ListMergeStatus check(final CommittedChangeList list, final boolean ignoreEnabled) {
      if (! enabledAndGettersFilled(ignoreEnabled)) {
        return ListMergeStatus.ALIEN;
      }

      if (! (list instanceof SvnChangeList)) {
        return ListMergeStatus.ALIEN;
      }

      final MergeinfoCached cachedState = getCurrentCache();
      if (cachedState != null) {
        if (cachedState.getCopyRevision() != -1 && cachedState.getCopyRevision() >= list.getNumber()) {
          return ListMergeStatus.COMMON;
        }
        final SvnMergeInfoCache.MergeCheckResult result = cachedState.getMap().get(list.getNumber());
        return convert(result, true);
      } else {
        final MergeinfoCached state = myMergeInfoCache.getCachedState(myRootGetter.get(), myWcPathGetter.get());
        if (state == null) {
          refresh(ignoreEnabled);
          return ListMergeStatus.REFRESHING;
        } else {
          if (state.getCopyRevision() != -1 && state.getCopyRevision() >= list.getNumber()) {
            return ListMergeStatus.COMMON;
          }
          return convert(state.getMap().get(list.getNumber()), false);
        }
      }
    }
  }

  public ListChecker getDecorator() {
    return myDecorator;
  }

  public void updateMixedRevisionsForPanel() {
    myMixedRevisionsConsumer.consume(myMergeInfoCache.isMixedRevisions(myRootGetter.get(), myWcPathGetter.get()));    
  }
}
