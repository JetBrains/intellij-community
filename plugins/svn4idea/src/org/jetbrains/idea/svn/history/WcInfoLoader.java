// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.RootUrlInfo;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationManager;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationNew;
import org.jetbrains.idea.svn.branchConfig.SvnBranchItem;
import org.jetbrains.idea.svn.dialogs.WCInfo;
import org.jetbrains.idea.svn.dialogs.WCInfoWithBranches;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class WcInfoLoader {

  @NotNull private final SvnVcs myVcs;
  /**
   * filled when showing for selected location
   */
  @Nullable private final RepositoryLocation myLocation;

  public WcInfoLoader(@NotNull SvnVcs vcs, @Nullable RepositoryLocation location) {
    myVcs = vcs;
    myLocation = location;
  }

  @NotNull
  public List<WCInfoWithBranches> loadRoots() {
    List<WCInfoWithBranches> result = ContainerUtil.newArrayList();

    for (WCInfo info : myVcs.getAllWcInfos()) {
      ContainerUtil.addIfNotNull(result, createInfo(info));
    }

    return result;
  }

  @Nullable
  public WCInfoWithBranches reloadInfo(@NotNull WCInfoWithBranches info) {
    File file = info.getRootInfo().getIoFile();
    RootUrlInfo rootInfo = myVcs.getSvnFileUrlMapping().getWcRootForFilePath(file);

    return rootInfo != null ? createInfo(new WCInfo(rootInfo, SvnUtil.isWorkingCopyRoot(file), SvnUtil.getDepth(myVcs, file))) : null;
  }

  @Nullable
  private WCInfoWithBranches createInfo(@NotNull WCInfo info) {
    if (!info.getFormat().supportsMergeInfo()) {
      return null;
    }

    final String url = info.getUrl().toString();
    if (myLocation != null && !myLocation.toPresentableString().startsWith(url) && !url.startsWith(myLocation.toPresentableString())) {
      return null;
    }
    if (!SvnUtil.checkRepositoryVersion15(myVcs, info.getUrl())) {
      return null;
    }

    // check of WC version
    RootUrlInfo rootForUrl = myVcs.getSvnFileUrlMapping().getWcRootForUrl(info.getUrl());
    return rootForUrl != null ? createInfoWithBranches(info, rootForUrl) : null;
  }

  @NotNull
  private WCInfoWithBranches createInfoWithBranches(@NotNull WCInfo info, @NotNull RootUrlInfo rootUrlInfo) {
    SvnBranchConfigurationNew configuration =
      SvnBranchConfigurationManager.getInstance(myVcs.getProject()).get(rootUrlInfo.getVirtualFile());
    Ref<WCInfoWithBranches.Branch> workingCopyBranch = Ref.create();
    List<WCInfoWithBranches.Branch> branches = ContainerUtil.newArrayList();
    String url = info.getUrl().toString();

    // TODO: Probably could utilize SvnBranchConfigurationNew.UrlListener and SvnBranchConfigurationNew.iterateUrls() behavior
    String trunkUrl = configuration.getTrunkUrl();
    if (trunkUrl != null) {
      add(url, trunkUrl, branches, workingCopyBranch);
    }

    for (String branchUrl : configuration.getBranchUrls()) {
      for (SvnBranchItem branchItem : configuration.getBranches(branchUrl)) {
        add(url, branchItem.getUrl(), branches, workingCopyBranch);
      }
    }

    Collections.sort(branches, (o1, o2) -> Comparing.compare(o1.getUrl(), o2.getUrl()));

    return new WCInfoWithBranches(info, branches, rootUrlInfo.getRoot(), workingCopyBranch.get());
  }

  private static void add(@NotNull String url,
                          @NotNull String branchUrl,
                          @NotNull List<WCInfoWithBranches.Branch> branches,
                          @NotNull Ref<WCInfoWithBranches.Branch> workingCopyBranch) {
    WCInfoWithBranches.Branch branch = new WCInfoWithBranches.Branch(branchUrl);

    if (!Url.isAncestor(branchUrl, url)) {
      branches.add(branch);
    }
    else {
      workingCopyBranch.set(branch);
    }
  }
}
