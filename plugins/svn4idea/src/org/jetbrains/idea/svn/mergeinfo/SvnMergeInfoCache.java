// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.mergeinfo;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.dialogs.WCInfoWithBranches;
import org.jetbrains.idea.svn.history.CopyData;
import org.jetbrains.idea.svn.history.FirstInBranch;
import org.jetbrains.idea.svn.history.SvnChangeList;

import java.util.Map;

public class SvnMergeInfoCache {

  private final static Logger LOG = Logger.getInstance(SvnMergeInfoCache.class);

  @NotNull private final Project myProject;
  // key - working copy root url
  @NotNull private final Map<Url, MyCurrentUrlData> myCurrentUrlMapping;

  public static Topic<SvnMergeInfoCacheListener> SVN_MERGE_INFO_CACHE =
    new Topic<>("SVN_MERGE_INFO_CACHE", SvnMergeInfoCacheListener.class);

  private SvnMergeInfoCache(@NotNull Project project) {
    myProject = project;
    myCurrentUrlMapping = ContainerUtil.newHashMap();
  }

  public static SvnMergeInfoCache getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, SvnMergeInfoCache.class);
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
    MyCurrentUrlData rootMapping = myCurrentUrlMapping.get(info.getUrl());
    BranchInfo mergeChecker = null;
    if (rootMapping == null) {
      rootMapping = new MyCurrentUrlData();
      myCurrentUrlMapping.put(info.getUrl(), rootMapping);
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
    MyCurrentUrlData rootMapping = myCurrentUrlMapping.get(info.getUrl());

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

    CopyRevison(final SvnVcs vcs, final String path, @NotNull Url repositoryRoot, final String branchUrl, @NotNull Url trunkUrl) {
      myPath = path;
      myRevision = -1;

      Task.Backgroundable task = new Task.Backgroundable(vcs.getProject(), "", false) {
        private CopyData myData;

        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            myData = new FirstInBranch(vcs, repositoryRoot, branchUrl, trunkUrl.toString()).run();
          }
          catch (VcsException e) {
            logAndShow(e);
          }
        }

        @Override
        public void onSuccess() {
          if (myData != null && myData.getCopySourceRevision() != -1) {
            BackgroundTaskUtil.syncPublisher(vcs.getProject(), SVN_MERGE_INFO_CACHE).copyRevisionUpdated();
          }
        }

        @Override
        public void onThrowable(@NotNull Throwable error) {
          logAndShow(error);
        }

        private void logAndShow(@NotNull Throwable error) {
          LOG.info(error);
          VcsBalloonProblemNotifier.showOverChangesView(vcs.getProject(), error.getMessage(), MessageType.ERROR);
        }
      };
      ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, new EmptyProgressIndicator());
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
    @NotNull private final Map<String, BranchInfo> myBranchInfo = ContainerUtil.createSoftMap();

    private MyCurrentUrlData() {
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
