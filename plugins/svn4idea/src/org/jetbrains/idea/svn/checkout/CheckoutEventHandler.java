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

import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.wm.*;
import org.jetbrains.idea.svn.*;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.wc.*;

public class CheckoutEventHandler implements ISVNEventHandler {
  private final ProgressIndicator myIndicator;
  private int myExternalsCount;
  private final SvnVcs myVCS;
  private final boolean myIsExport;

  public CheckoutEventHandler(SvnVcs vcs, boolean isExport, ProgressIndicator indicator) {
    myIndicator = indicator;
    myVCS = vcs;
    myExternalsCount = 1;
    myIsExport = isExport;
  }

  public void handleEvent(SVNEvent event, double progress) {
    final String path = SvnUtil.getPathForProgress(event);
    if (path == null) {
      return;
    }
    if (event.getAction() == SVNEventAction.UPDATE_EXTERNAL) {
      myExternalsCount++;
      myIndicator.setText(SvnBundle.message("progress.text2.fetching.external.location", event.getFile().getAbsolutePath()));
      myIndicator.setText2("");
    }
    else if (event.getAction() == SVNEventAction.UPDATE_ADD) {
      myIndicator.setText2(SvnBundle.message(myIsExport ? "progress.text2.exported" : "progress.text2.checked.out", event.getFile().getName()));
    }
    else if (event.getAction() == SVNEventAction.UPDATE_COMPLETED) {
      myExternalsCount--;
      myIndicator.setText2(SvnBundle.message(myIsExport ? "progress.text2.exported.revision" : "progress.text2.checked.out.revision", event.getRevision()));
      if (myExternalsCount == 0 && event.getRevision() >= 0 && myVCS != null) {
        myExternalsCount = 1;
        Project project = myVCS.getProject();
        if (project != null) {
          StatusBar.Info.set(SvnBundle.message(myIsExport ? "progress.text2.exported.revision" : "status.text.checked.out.revision", event.getRevision()), project);
        }
      }
    } else if (event.getAction() == SVNEventAction.COMMIT_ADDED) {
      myIndicator.setText2(SvnBundle.message("progress.text2.adding", path));
    } else if (event.getAction() == SVNEventAction.COMMIT_DELTA_SENT) {
      myIndicator.setText2(SvnBundle.message("progress.text2.transmitting.delta", path));
    }
  }

  public void checkCancelled() throws SVNCancelException {
    if (myIndicator.isCanceled()) {
      throw new SVNCancelException(SVNErrorMessage.create(SVNErrorCode.CANCELLED, "Operation cancelled"));
    }
  }
}
