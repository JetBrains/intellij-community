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
package org.jetbrains.idea.svn.checkout;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;

public class CheckoutEventHandler implements ISVNEventHandler {
  @Nullable private final ProgressIndicator myIndicator;
  private int myExternalsCount;
  @NotNull private final SvnVcs myVCS;
  private final boolean myIsExport;
  private int myCnt;

  public CheckoutEventHandler(@NotNull SvnVcs vcs, boolean isExport, @Nullable ProgressIndicator indicator) {
    myIndicator = indicator;
    myVCS = vcs;
    myExternalsCount = 1;
    myIsExport = isExport;
    myCnt = 0;
  }

  public void handleEvent(SVNEvent event, double progress) {
    final String path = SvnUtil.getPathForProgress(event);
    if (path == null) {
      return;
    }
    if (event.getAction() == SVNEventAction.UPDATE_EXTERNAL) {
      myExternalsCount++;
      progress(SvnBundle.message("progress.text2.fetching.external.location", event.getFile().getAbsolutePath()));
    }
    else if (event.getAction() == SVNEventAction.UPDATE_ADD) {
      progress2(SvnBundle.message(myIsExport ? "progress.text2.exported" : "progress.text2.checked.out", event.getFile().getName(), myCnt));
      ++ myCnt;
    }
    else if (event.getAction() == SVNEventAction.UPDATE_COMPLETED) {
      myExternalsCount--;
      progress2(
        (SvnBundle.message(myIsExport ? "progress.text2.exported.revision" : "progress.text2.checked.out.revision", event.getRevision())));
      if (myExternalsCount == 0 && event.getRevision() >= 0) {
        myExternalsCount = 1;
        Project project = myVCS.getProject();
        if (project != null) {
          StatusBar.Info.set(SvnBundle.message(myIsExport ? "progress.text2.exported.revision" : "status.text.checked.out.revision", event.getRevision()), project);
        }
      }
    } else if (event.getAction() == SVNEventAction.COMMIT_ADDED) {
      progress2((SvnBundle.message("progress.text2.adding", path)));
    } else if (event.getAction() == SVNEventAction.COMMIT_DELTA_SENT) {
      progress2((SvnBundle.message("progress.text2.transmitting.delta", path)));
    }
  }

  public void checkCancelled() throws SVNCancelException {
    if (myIndicator != null && myIndicator.isCanceled()) {
      throw new SVNCancelException(SVNErrorMessage.create(SVNErrorCode.CANCELLED, "Operation cancelled"));
    }
  }

  private void progress(@NotNull String text) {
    if (myIndicator != null) {
      myIndicator.checkCanceled();
      myIndicator.setText(text);
      myIndicator.setText2("");
    } else {
      ProgressManager.progress(text);
    }
  }

  private void progress2(@NotNull String text) {
    if (myIndicator != null) {
      myIndicator.checkCanceled();
      myIndicator.setText2(text);
    } else {
      ProgressManager.progress2(text);
    }
  }
}
