// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.history;

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
import java.util.ArrayList;
import java.util.List;

import static com.intellij.vcsUtil.VcsUtil.getFilePath;
import static java.util.Comparator.comparing;

public class WcInfoLoader {

  private final @NotNull SvnVcs myVcs;
  /**
   * filled when showing for selected location
   */
  private final @Nullable RepositoryLocation myLocation;

  public WcInfoLoader(@NotNull SvnVcs vcs, @Nullable RepositoryLocation location) {
    myVcs = vcs;
    myLocation = location;
  }

  public @NotNull List<WCInfoWithBranches> loadRoots() {
    List<WCInfoWithBranches> result = new ArrayList<>();

    for (WCInfo info : myVcs.getAllWcInfos()) {
      ContainerUtil.addIfNotNull(result, createInfo(info));
    }

    return result;
  }

  public @Nullable WCInfoWithBranches reloadInfo(@NotNull WCInfoWithBranches info) {
    File file = info.getRootInfo().getIoFile();
    RootUrlInfo rootInfo = myVcs.getSvnFileUrlMapping().getWcRootForFilePath(getFilePath(info.getRootInfo().getVirtualFile()));

    return rootInfo != null ? createInfo(new WCInfo(rootInfo, SvnUtil.isWorkingCopyRoot(file), SvnUtil.getDepth(myVcs, file))) : null;
  }

  private @Nullable WCInfoWithBranches createInfo(@NotNull WCInfo info) {
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

  private @NotNull WCInfoWithBranches createInfoWithBranches(@NotNull WCInfo info, @NotNull RootUrlInfo rootUrlInfo) {
    SvnBranchConfigurationNew configuration =
      SvnBranchConfigurationManager.getInstance(myVcs.getProject()).get(rootUrlInfo.getVirtualFile());
    Ref<WCInfoWithBranches.Branch> workingCopyBranch = Ref.create();
    List<WCInfoWithBranches.Branch> branches = new ArrayList<>();

    Url trunk = configuration.getTrunk();
    if (trunk != null) {
      add(info.getUrl(), trunk, branches, workingCopyBranch);
    }

    for (Url branchUrl : configuration.getBranchLocations()) {
      for (SvnBranchItem branchItem : configuration.getBranches(branchUrl)) {
        add(info.getUrl(), branchItem.getUrl(), branches, workingCopyBranch);
      }
    }

    branches.sort(comparing(branch -> branch.getUrl().toDecodedString()));

    return new WCInfoWithBranches(info, branches, rootUrlInfo.getRoot(), workingCopyBranch.get());
  }

  private static void add(@NotNull Url url,
                          @NotNull Url branchUrl,
                          @NotNull List<WCInfoWithBranches.Branch> branches,
                          @NotNull Ref<WCInfoWithBranches.Branch> workingCopyBranch) {
    WCInfoWithBranches.Branch branch = new WCInfoWithBranches.Branch(branchUrl);

    if (!SvnUtil.isAncestor(branchUrl, url)) {
      branches.add(branch);
    }
    else {
      workingCopyBranch.set(branch);
    }
  }
}
