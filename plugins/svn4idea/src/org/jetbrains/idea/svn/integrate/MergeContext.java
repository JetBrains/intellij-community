// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.DialogTitle;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.dialogs.WCInfo;

import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.SvnUtil.ensureStartSlash;
import static org.jetbrains.idea.svn.SvnUtil.getRelativeUrl;

public class MergeContext {

  private final @NotNull Project myProject;
  private final @NotNull String myBranchName;
  private final @NotNull VirtualFile myRoot;
  private final @NotNull WCInfo myWcInfo;
  private final @NotNull Url mySourceUrl;
  private final @NotNull SvnVcs myVcs;
  private final @NotNull String myRepositoryRelativeSourcePath;
  private final @NotNull String myRepositoryRelativeWorkingCopyPath;

  public MergeContext(@NotNull SvnVcs vcs,
                      @NotNull Url sourceUrl,
                      @NotNull WCInfo wcInfo,
                      @NotNull String branchName,
                      @NotNull VirtualFile root) {
    myVcs = vcs;
    myProject = vcs.getProject();
    myBranchName = branchName;
    myRoot = root;
    mySourceUrl = sourceUrl;
    myWcInfo = wcInfo;
    myRepositoryRelativeSourcePath = ensureStartSlash(getRelativeUrl(myWcInfo.getRepoUrl(), mySourceUrl));
    myRepositoryRelativeWorkingCopyPath = ensureStartSlash(getRelativeUrl(myWcInfo.getRepoUrl(), myWcInfo.getUrl()));
  }

  public @NotNull Project getProject() {
    return myProject;
  }

  public @NotNull String getBranchName() {
    return myBranchName;
  }

  public @NotNull VirtualFile getRoot() {
    return myRoot;
  }

  public @NotNull WCInfo getWcInfo() {
    return myWcInfo;
  }

  public @NotNull Url getSourceUrl() {
    return mySourceUrl;
  }

  public @NotNull String getRepositoryRelativeSourcePath() {
    return myRepositoryRelativeSourcePath;
  }

  public @NotNull String getRepositoryRelativeWorkingCopyPath() {
    return myRepositoryRelativeWorkingCopyPath;
  }

  public @NotNull SvnVcs getVcs() {
    return myVcs;
  }

  public @DialogTitle @NotNull String getMergeTitle() {
    return message("dialog.title.merge.from.branch", myBranchName);
  }
}