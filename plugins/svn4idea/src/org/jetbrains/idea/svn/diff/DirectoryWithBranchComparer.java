// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.diff;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.ClientFactory;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.api.Url;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.util.NlsContexts.DialogTitle;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.jetbrains.idea.svn.SvnBundle.message;

public class DirectoryWithBranchComparer extends ElementWithBranchComparer {

  private final @DialogTitle @NotNull StringBuilder titleBuilder = new StringBuilder();
  @NotNull private final List<Change> changes = new ArrayList<>();

  public DirectoryWithBranchComparer(@NotNull Project project,
                                     @NotNull VirtualFile virtualFile,
                                     @NotNull Url branchUrl,
                                     long branchRevision) {
    super(project, virtualFile, branchUrl, branchRevision);
  }

  @Override
  protected void compare() throws VcsException {
    titleBuilder.append(message("repository.browser.compare.title", myElementUrl.toDecodedString(),
                                FileUtil.toSystemDependentName(myVirtualFile.getPresentableUrl())));

    Target target1 = Target.on(myElementUrl);
    Target target2 = Target.on(virtualToIoFile(myVirtualFile));

    changes.addAll(getClientFactory().createDiffClient().compare(target1, target2));
  }

  @NotNull
  private ClientFactory getClientFactory() {
    return getClientFactory(myVcs, virtualToIoFile(myVirtualFile));
  }

  @NotNull
  public static ClientFactory getClientFactory(@NotNull SvnVcs vcs, @NotNull File file) {
    // TODO: Fix for svn 1.7 and lower as svn 1.7 command line "--summarize" option for "diff" command does not support comparing working
    // TODO: copy directories with repository directories
    return vcs.getFactory(file);
  }

  @Override
  protected void showResult() {
    AbstractVcsHelper.getInstance(myProject).showWhatDiffersBrowser(changes, titleBuilder.toString());
  }

  @Override
  public @NotNull String getTitle() {
    return message("dialog.title.computing.difference");
  }
}
