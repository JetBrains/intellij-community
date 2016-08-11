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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.WCInfoWithBranches;
import org.jetbrains.idea.svn.history.CopyData;
import org.jetbrains.idea.svn.history.FirstInBranch;
import org.jetbrains.idea.svn.history.SvnChangeList;

import java.util.Map;

public class SvnMergeInfoCache {

  private final static Logger LOG = Logger.getInstance(SvnMergeInfoCache.class);

  @NotNull private final Project myProject;
  // key - working copy root url
  @NotNull private final Map<String, MyCurrentUrlData> myCurrentUrlMapping;

  public static Topic<SvnMergeInfoCacheListener> SVN_MERGE_INFO_CACHE =
    new Topic<>("SVN_MERGE_INFO_CACHE", SvnMergeInfoCacheListener.class);

  private SvnMergeInfoCache(@NotNull Project project) {
    myProject = project;
    myCurrentUrlMapping = ContainerUtil.newHashMap();
  }

  public static SvnMergeInfoCache getInstance(@NotNull Project project) {
    return PeriodicalTasksCloser.getInstance().safeGetService(project, SvnMergeInfoCache.class);
  }

  public void clear(@NotNull WCInfoWithBranches info, String branchPath) {
    BranchInfo branchInfo = getBranchInfo(info, branchPath);

    if (branchInfo != null) {
      branchInfo.clear();
    }
  }

  @Nullable
  public MergeInfoCached getCachedState(@NotNull WCInfoWithBranches info, String branchPath) {
    BranchInfo branchInfo = getBranchInfo(info, branchPath);

    return branchInfo != null ? branchInfo.getCached() : null;
  }

  // only refresh might have changed; for branches/roots change, another method is used
  public MergeCheckResult getState(@NotNull WCInfoWithBranches info,
                                   @NotNull SvnChangeList list,
                                   @NotNull WCInfoWithBranches.Branch selectedBranch,
                                   final String branchPath) {
    MyCurrentUrlData rootMapping = myCurrentUrlMapping.get(info.getRootUrl());
    BranchInfo mergeChecker = null;
    if (rootMapping == null) {
      rootMapping = new MyCurrentUrlData();
      myCurrentUrlMapping.put(info.getRootUrl(), rootMapping);
    } else {
      mergeChecker = rootMapping.getBranchInfo(branchPath);
    }
    if (mergeChecker == null) {
      mergeChecker = new BranchInfo(SvnVcs.getInstance(myProject), info, selectedBranch);
      rootMapping.addBranchInfo(branchPath, mergeChecker);
    }

    return mergeChecker.checkList(list, branchPath);
  }

  public boolean isMixedRevisions(@NotNull WCInfoWithBranches info, final String branchPath) {
    BranchInfo branchInfo = getBranchInfo(info, branchPath);

    return branchInfo != null && branchInfo.isMixedRevisionsFound();
  }

  @Nullable
  private BranchInfo getBranchInfo(@NotNull WCInfoWithBranches info, String branchPath) {
    MyCurrentUrlData rootMapping = myCurrentUrlMapping.get(info.getRootUrl());

    return rootMapping != null ? rootMapping.getBranchInfo(branchPath) : null;
  }

  public enum MergeCheckResult {
    COMMON,
    MERGED,
    NOT_MERGED,
    NOT_EXISTS;

    @NotNull
    public static MergeCheckResult getInstance(boolean merged) {
      return merged ? MERGED : NOT_MERGED;
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

    // key - working copy local path
    @NotNull private final Map<String, BranchInfo> myBranchInfo;

    private MyCurrentUrlData() {
      myBranchInfo = new SoftHashMap<>();
    }

    public BranchInfo getBranchInfo(final String branchUrl) {
      return myBranchInfo.get(branchUrl);
    }

    public void addBranchInfo(@NotNull String branchUrl, @NotNull BranchInfo mergeChecker) {
      myBranchInfo.put(branchUrl, mergeChecker);
    }
  }

  public interface SvnMergeInfoCacheListener {
    void copyRevisionUpdated();
  }
}
