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
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.IOException;

/**
 * @author Konstantin Kolosovsky.
 */
public class FileWithBranchComparer extends ElementWithBranchComparer {

  @NotNull private final Ref<byte[]> content = new Ref<>();
  @NotNull private final StringBuilder remoteTitleBuilder = new StringBuilder();
  @NotNull private final Ref<Boolean> success = new Ref<>();

  public FileWithBranchComparer(@NotNull Project project,
                                @NotNull VirtualFile virtualFile,
                                @NotNull String branchUrl,
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
    remoteTitleBuilder.append(myElementUrl);
    content.set(SvnUtil.getFileContents(myVcs, SvnTarget.fromURL(myElementUrl), SVNRevision.HEAD, SVNRevision.UNDEFINED));
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
