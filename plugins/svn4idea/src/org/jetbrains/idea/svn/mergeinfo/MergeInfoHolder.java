/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.vcs.changes.committed.CommittedChangeListsListener;
import com.intellij.openapi.vcs.changes.committed.DecoratorManager;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.dialogs.WCInfoWithBranches;
import org.jetbrains.idea.svn.history.RootsAndBranches;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.history.SvnMergeInfoRootPanelManual;

import java.util.Map;

public class MergeInfoHolder {

  @NotNull private final DecoratorManager myManager;
  @NotNull private final SvnMergeInfoCache myMergeInfoCache;
  @NotNull private final RootsAndBranches myMainPanel;
  @NotNull private final SvnMergeInfoRootPanelManual myPanel;

  // used ONLY when refresh is triggered
  @NotNull private final Map<Couple<String>, MergeInfoCached> myCachedMap;

  public MergeInfoHolder(@NotNull Project project,
                         @NotNull DecoratorManager manager,
                         @NotNull RootsAndBranches mainPanel,
                         @NotNull SvnMergeInfoRootPanelManual panel) {
    myManager = manager;
    myMainPanel = mainPanel;
    myPanel = panel;
    myMergeInfoCache = SvnMergeInfoCache.getInstance(project);
    myCachedMap = ContainerUtil.newHashMap();
  }

  @Nullable
  private MergeInfoCached getCurrentCache() {
    return myCachedMap.get(createKey(myPanel.getWcInfo(), myPanel.getBranch()));
  }

  private boolean isEnabledAndConfigured(boolean ignoreEnabled) {
    return (ignoreEnabled || myMainPanel.isHighlightingOn() && myPanel.isEnabled()) &&
           myPanel.getBranch() != null &&
           myPanel.getLocalBranch() != null;
  }

  public boolean refreshEnabled(boolean ignoreEnabled) {
    return isEnabledAndConfigured(ignoreEnabled) && getCurrentCache() == null;
  }

  @NotNull
  private static Couple<String> createKey(@NotNull WCInfoWithBranches root, @NotNull WCInfoWithBranches.Branch branch) {
    return Couple.of(root.getPath(), branch.getUrl());
  }

  @NotNull
  public ListMergeStatus refresh(final boolean ignoreEnabled) {
    final CommittedChangeListsListener refresher = createRefresher(ignoreEnabled);
    if (refresher != null) {
      myManager.reportLoadedLists(refresher);
    }
    myManager.repaintTree();

    return ListMergeStatus.REFRESHING;
  }

  @Nullable
  public CommittedChangeListsListener createRefresher(boolean ignoreEnabled) {
    CommittedChangeListsListener result = null;

    if (refreshEnabled(ignoreEnabled)) {
      // on awt thread
      final MergeInfoCached state = myMergeInfoCache.getCachedState(myPanel.getWcInfo(), myPanel.getLocalBranch());
      myCachedMap.put(createKey(myPanel.getWcInfo(), myPanel.getBranch()), state != null ? state.copy() : new MergeInfoCached());
      myMergeInfoCache.clear(myPanel.getWcInfo(), myPanel.getLocalBranch());

      result = new MyRefresher();
    }

    return result;
  }

  private class MyRefresher implements CommittedChangeListsListener {

    @NotNull private final WCInfoWithBranches myRefreshedRoot;
    private final WCInfoWithBranches.Branch myRefreshedBranch;
    private final String myBranchPath;

    private MyRefresher() {
      myRefreshedRoot = myPanel.getWcInfo();
      myRefreshedBranch = myPanel.getBranch();
      myBranchPath = myPanel.getLocalBranch();
    }

    public void onBeforeStartReport() {
    }

    public boolean report(final CommittedChangeList list) {
      if (list instanceof SvnChangeList) {
        final SvnMergeInfoCache.MergeCheckResult checkState =
          myMergeInfoCache.getState(myRefreshedRoot, (SvnChangeList)list, myRefreshedBranch, myBranchPath);
        // todo make batches - by 10
        final long number = list.getNumber();
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            final MergeInfoCached cachedState = myCachedMap.get(createKey(myRefreshedRoot, myRefreshedBranch));
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

  @NotNull
  public ListMergeStatus check(final CommittedChangeList list, final boolean ignoreEnabled) {
    ListMergeStatus result;

    if (!isEnabledAndConfigured(ignoreEnabled) || !(list instanceof SvnChangeList)) {
      result = ListMergeStatus.ALIEN;
    }
    else {
      MergeInfoCached cachedState = getCurrentCache();
      MergeInfoCached state = myMergeInfoCache.getCachedState(myPanel.getWcInfo(), myPanel.getLocalBranch());

      result = cachedState != null ? check(list, cachedState, true) : state != null ? check(list, state, false) : refresh(ignoreEnabled);
    }

    return result;
  }

  @NotNull
  public ListMergeStatus check(@NotNull CommittedChangeList list, @NotNull MergeInfoCached state, boolean isCached) {
    SvnMergeInfoCache.MergeCheckResult mergeCheckResult = state.getMap().get(list.getNumber());
    ListMergeStatus result = state.copiedAfter(list) ? ListMergeStatus.COMMON : ListMergeStatus.from(mergeCheckResult);

    return ObjectUtils.notNull(result, isCached ? ListMergeStatus.REFRESHING : ListMergeStatus.ALIEN);
  }

  public void updateMixedRevisionsForPanel() {
    myPanel.setMixedRevisions(myMergeInfoCache.isMixedRevisions(myPanel.getWcInfo(), myPanel.getLocalBranch()));
  }
}
