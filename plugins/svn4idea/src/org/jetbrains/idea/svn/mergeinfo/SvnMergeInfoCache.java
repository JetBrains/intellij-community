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

import com.intellij.lifecycle.PeriodicalTasksCloser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.TransparentlyFailedValue;
import com.intellij.openapi.vcs.changes.TransparentlyFailedValueI;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SoftHashMap;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.WCInfoWithBranches;
import org.jetbrains.idea.svn.dialogs.WCPaths;
import org.jetbrains.idea.svn.history.CopyData;
import org.jetbrains.idea.svn.history.FirstInBranch;
import org.jetbrains.idea.svn.history.SvnChangeList;

import java.util.Map;

public class SvnMergeInfoCache {
  private final static Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.mergeinfo.SvnMergeInfoCache");

  private final Project myProject;
  private final Map<String, MyCurrentUrlData> myCurrentUrlMapping;

  public static Topic<SvnMergeInfoCacheListener> SVN_MERGE_INFO_CACHE =
    new Topic<SvnMergeInfoCacheListener>("SVN_MERGE_INFO_CACHE", SvnMergeInfoCacheListener.class);

  private SvnMergeInfoCache(final Project project) {
    myProject = project;
    myCurrentUrlMapping = ContainerUtil.newHashMap();
  }

  public static SvnMergeInfoCache getInstance(final Project project) {
    return PeriodicalTasksCloser.getInstance().safeGetService(project, SvnMergeInfoCache.class);
  }

  public void clear(final WCPaths info, final String branchPath) {
    final String currentUrl = info.getRootUrl();

    final MyCurrentUrlData rootMapping = myCurrentUrlMapping.get(currentUrl);
    if (rootMapping != null) {
      final BranchInfo mergeChecker = rootMapping.getBranchInfo(branchPath);
      if (mergeChecker != null) {
        mergeChecker.clear();
      }
    }
  }

  @Nullable
  public MergeinfoCached getCachedState(final WCPaths info, final String branchPath) {
    final String currentUrl = info.getRootUrl();

    MyCurrentUrlData rootMapping = myCurrentUrlMapping.get(currentUrl);
    if (rootMapping != null) {
      final BranchInfo branchInfo = rootMapping.getBranchInfo(branchPath);
      if (branchInfo != null) {
        return branchInfo.getCached();
      }
    }
    return null;
  }

  // only refresh might have changed; for branches/roots change, another method is used
  public MergeCheckResult getState(final WCInfoWithBranches info, final SvnChangeList list, final WCInfoWithBranches.Branch selectedBranch) {
    return getState(info, list, selectedBranch, null);
  }

  // only refresh might have changed; for branches/roots change, another method is used
  public MergeCheckResult getState(final WCInfoWithBranches info, final SvnChangeList list, final WCInfoWithBranches.Branch selectedBranch,
                                   final String branchPath) {
    final String currentUrl = info.getRootUrl();
    final String branchUrl = selectedBranch.getUrl();

    MyCurrentUrlData rootMapping = myCurrentUrlMapping.get(currentUrl);
    BranchInfo mergeChecker = null;
    if (rootMapping == null) {
      rootMapping = new MyCurrentUrlData();
      myCurrentUrlMapping.put(currentUrl, rootMapping);
    } else {
      mergeChecker = rootMapping.getBranchInfo(branchPath);
    }
    if (mergeChecker == null) {
      mergeChecker = new BranchInfo(SvnVcs.getInstance(myProject), info.getRepoUrl(), branchUrl, currentUrl, info.getTrunkRoot());
      rootMapping.addBranchInfo(branchPath, mergeChecker);
    }

    return mergeChecker.checkList(list, branchPath);
  }

  public boolean isMixedRevisions(final WCInfoWithBranches info, final String branchPath) {
    final String currentUrl = info.getRootUrl();
    final MyCurrentUrlData rootMapping = myCurrentUrlMapping.get(currentUrl);
    if (rootMapping != null) {
      final BranchInfo branchInfo = rootMapping.getBranchInfo(branchPath);
      if (branchInfo != null) {
        return branchInfo.isMixedRevisionsFound();
      }
    }
    return false;
  }

  public static enum MergeCheckResult {
    COMMON,
    MERGED,
    NOT_MERGED,
    NOT_EXISTS,
    NOT_EXISTS_PARTLY_MERGED;

    public static MergeCheckResult getInstance(final boolean merged) {
      // not exists assumed to be already checked
      if (merged) {
        return MERGED;
      }
      return NOT_MERGED;
    }
  }

  static class CopyRevison {
    private final String myPath;
    private volatile long myRevision;

    CopyRevison(final SvnVcs vcs, final String path, final String repositoryRoot, final String branchUrl, final String trunkUrl) {
      myPath = path;
      myRevision = -1;

      final TransparentlyFailedValueI<CopyData, VcsException> result = new TransparentlyFailedValue<CopyData, VcsException>() {
        @Override
        public void set(CopyData copyData) {
          if (copyData == null) return;
          myRevision = copyData.getCopySourceRevision();
          if (myRevision != -1) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                if (vcs.getProject().isDisposed()) return;
                vcs.getProject().getMessageBus().syncPublisher(SVN_MERGE_INFO_CACHE).copyRevisionUpdated();
              }
            });
          }
        }

        @Override
        public void fail(VcsException e) {
          LOG.info(e);
          VcsBalloonProblemNotifier.showOverChangesView(vcs.getProject(), e.getMessage(), MessageType.ERROR);
        }

        @Override
        public void failRuntime(RuntimeException e) {
          LOG.info(e);
          VcsBalloonProblemNotifier.showOverChangesView(vcs.getProject(), e.getMessage(), MessageType.ERROR);
        }
      };
      ApplicationManager.getApplication().executeOnPooledThread(new FirstInBranch(vcs, repositoryRoot, branchUrl, trunkUrl, result));
    }

    public String getPath() {
      return myPath;
    }

    public long getRevision() {
      return myRevision;
    }
  }

  private static class MyCurrentUrlData {
    private final Map<String, BranchInfo> myBranchInfo;

    private MyCurrentUrlData() {
      myBranchInfo = new SoftHashMap<String, BranchInfo>();
    }

    public BranchInfo getBranchInfo(final String branchUrl) {
      return myBranchInfo.get(branchUrl);
    }

    public void addBranchInfo(final String branchUrl, final BranchInfo mergeChecker) {
      myBranchInfo.put(branchUrl, mergeChecker);
    }
  }

  public interface SvnMergeInfoCacheListener {
    void copyRevisionUpdated();
  }
}
