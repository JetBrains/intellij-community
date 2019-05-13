// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.checkout;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.EventAction;
import org.jetbrains.idea.svn.api.ProgressEvent;
import org.jetbrains.idea.svn.api.ProgressTracker;

public class CheckoutEventHandler implements ProgressTracker {
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

  @Override
  public void consume(ProgressEvent event) {
    if (event.getPath() == null) {
      return;
    }
    if (event.getAction() == EventAction.UPDATE_EXTERNAL) {
      myExternalsCount++;
      progress(SvnBundle.message("progress.text2.fetching.external.location", event.getFile().getAbsolutePath()));
    }
    else if (event.getAction() == EventAction.UPDATE_ADD) {
      progress2(SvnBundle.message(myIsExport ? "progress.text2.exported" : "progress.text2.checked.out", event.getFile().getName(), myCnt));
      ++ myCnt;
    }
    else if (event.getAction() == EventAction.UPDATE_COMPLETED) {
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
    } else if (event.getAction() == EventAction.COMMIT_ADDED) {
      progress2((SvnBundle.message("progress.text2.adding", event.getPath())));
    } else if (event.getAction() == EventAction.COMMIT_DELTA_SENT) {
      progress2((SvnBundle.message("progress.text2.transmitting.delta", event.getPath())));
    }
  }

  @Override
  public void checkCancelled() throws ProcessCanceledException {
    if (myIndicator != null) {
      myIndicator.checkCanceled();
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
