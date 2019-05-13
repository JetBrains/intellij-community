// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.diff;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.api.Url;

import java.io.IOException;

public class FileWithBranchComparer extends ElementWithBranchComparer {

  @NotNull private final Ref<byte[]> content = new Ref<>();
  @NotNull private final StringBuilder remoteTitleBuilder = new StringBuilder();
  @NotNull private final Ref<Boolean> success = new Ref<>();

  public FileWithBranchComparer(@NotNull Project project,
                                @NotNull VirtualFile virtualFile,
                                @NotNull Url branchUrl,
                                long branchRevision) {
    super(project, virtualFile, branchUrl, branchRevision);
  }

  @Override
  protected void beforeCompare() {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.setIndeterminate(true);
    }
  }

  @Override
  protected void compare() throws VcsException {
    remoteTitleBuilder.append(myElementUrl.toDecodedString());
    content.set(SvnUtil.getFileContents(myVcs, Target.on(myElementUrl), Revision.HEAD, Revision.UNDEFINED));
    success.set(true);
  }

  @Override
  protected void showResult() {
    if (!success.isNull()) {
      String title = SvnBundle.message("compare.with.branch.diff.title");

      String title1 = remoteTitleBuilder.toString();
      String title2 = myVirtualFile.getPresentableUrl();

      try {
        DiffContent content1 = DiffContentFactory.getInstance().createFromBytes(myProject, content.get(), myVirtualFile);
        DiffContent content2 = DiffContentFactory.getInstance().create(myProject, myVirtualFile);

        DiffRequest request = new SimpleDiffRequest(title, content1, content2, title1, title2);

        DiffManager.getInstance().showDiff(myProject, request);
      }
      catch (IOException e) {
        reportGeneralException(e);
      }
    }
  }

  @Override
  public String getTitle() {
    return SvnBundle.message("compare.with.branch.progress.loading.content");
  }
}
