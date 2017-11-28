/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.diff;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.jetbrains.idea.svn.api.ClientFactory;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

/**
 * @author Konstantin Kolosovsky.
 */
public class DirectoryWithBranchComparer extends ElementWithBranchComparer {

  @NotNull private final StringBuilder titleBuilder = new StringBuilder();
  @NotNull private final List<Change> changes = new ArrayList<>();

  public DirectoryWithBranchComparer(@NotNull Project project,
                                     @NotNull VirtualFile virtualFile,
                                     @NotNull String branchUrl,
                                     long branchRevision) {
    super(project, virtualFile, branchUrl, branchRevision);
  }

  @Override
  protected void compare() throws VcsException {
    titleBuilder.append(SvnBundle.message("repository.browser.compare.title", myElementUrl,
                                          FileUtil.toSystemDependentName(myVirtualFile.getPresentableUrl())));

    SvnTarget target1 = SvnTarget.fromURL(myElementUrl);
    SvnTarget target2 = SvnTarget.fromFile(virtualToIoFile(myVirtualFile));

    changes.addAll(getClientFactory().createDiffClient().compare(target1, target2));
  }

  @NotNull
  private ClientFactory getClientFactory() {
    return getClientFactory(myVcs, virtualToIoFile(myVirtualFile));
  }

  @NotNull
  public static ClientFactory getClientFactory(@NotNull SvnVcs vcs, @NotNull File file) {
    WorkingCopyFormat format = vcs.getWorkingCopyFormat(file);

    // svn 1.7 command line "--summarize" option for "diff" command does not support comparing working copy directories with repository
    // directories - that is why command line is only used explicitly for svn 1.8
    return format.isOrGreater(WorkingCopyFormat.ONE_DOT_EIGHT) ? vcs.getCommandLineFactory() : vcs.getSvnKitFactory();
  }

  @Override
  protected void onCancel() {
    changes.clear();
  }

  @Override
  protected void showResult() {
    AbstractVcsHelper.getInstance(myProject).showWhatDiffersBrowser(null, changes, titleBuilder.toString());
  }

  @Override
  public String getTitle() {
    return SvnBundle.message("progress.computing.difference");
  }
}
