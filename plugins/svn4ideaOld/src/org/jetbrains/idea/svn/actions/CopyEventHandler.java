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
package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnUtil;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;

public class CopyEventHandler implements ISVNEventHandler {
  private final ProgressIndicator myProgress;

  public CopyEventHandler(ProgressIndicator progress) {
    myProgress = progress;
  }

  public void handleEvent(SVNEvent event, double p) {
    final String path = SvnUtil.getPathForProgress(event);
    if (path == null) {
      return;
    }
    if (event.getAction() == SVNEventAction.COMMIT_ADDED) {
      myProgress.setText2(SvnBundle.message("progress.text2.adding", path));
    }
    else if (event.getAction() == SVNEventAction.COMMIT_DELETED) {
      myProgress.setText2(SvnBundle.message("progress.text2.deleting", path));
    }
    else if (event.getAction() == SVNEventAction.COMMIT_MODIFIED) {
      myProgress.setText2(SvnBundle.message("progress.text2.sending", path));
    }
    else if (event.getAction() == SVNEventAction.COMMIT_REPLACED) {
      myProgress.setText2(SvnBundle.message("progress.text2.replacing", path));
    }
    else if (event.getAction() == SVNEventAction.COMMIT_DELTA_SENT) {
      myProgress.setText2(SvnBundle.message("progress.text2.transmitting.delta", path));
    }
  }

  public void checkCancelled() {
  }
}
