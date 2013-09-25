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
package org.jetbrains.idea.svn.checkin;

import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.commandLine.CommitEventHandler;
import org.jetbrains.idea.svn.commandLine.CommitEventType;
import org.jetbrains.idea.svn.SvnBundle;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;

import java.io.File;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/26/13
 * Time: 11:13 AM
 */
public class IdeaCommitHandler implements CommitEventHandler, ISVNEventHandler {
  private final ProgressIndicator myProgress;

  public IdeaCommitHandler(ProgressIndicator progress) {
    myProgress = progress;
  }

  @Override
  public void commitEvent(CommitEventType type, File target) {
    if (myProgress == null) return;
    myProgress.checkCanceled();

    updateProgress(type, target.getPath());
  }

  @Override
  public void committedRevision(long revNum) {
    if (myProgress == null) return;
    myProgress.checkCanceled();
    myProgress.setText2(SvnBundle.message("status.text.comitted.revision", revNum));
  }

  public void handleEvent(SVNEvent event, double p) {
    final String path = SvnUtil.getPathForProgress(event);
    if (path == null) {
      return;
    }

    updateProgress(convert(event.getAction()), path);
  }

  public void checkCancelled() {
  }

  private void updateProgress(@NotNull CommitEventType type, @NotNull String target) {
    if (CommitEventType.adding.equals(type)) {
      myProgress.setText2(SvnBundle.message("progress.text2.adding", target));
    } else if (CommitEventType.deleting.equals(type)) {
      myProgress.setText2(SvnBundle.message("progress.text2.deleting", target));
    } else if (CommitEventType.sending.equals(type)) {
      myProgress.setText2(SvnBundle.message("progress.text2.sending", target));
    } else if (CommitEventType.replacing.equals(type)) {
      myProgress.setText2(SvnBundle.message("progress.text2.replacing", target));
    } else if (CommitEventType.transmittingDeltas.equals(type)) {
      myProgress.setText2(SvnBundle.message("progress.text2.transmitting.delta", target));
    }
  }

  private static CommitEventType convert(@NotNull SVNEventAction action) {
    CommitEventType result = null;

    if (SVNEventAction.COMMIT_ADDED.equals(action)) {
      result = CommitEventType.adding;
    } else if (SVNEventAction.COMMIT_DELETED.equals(action)) {
      result = CommitEventType.deleting;
    } else if (SVNEventAction.COMMIT_MODIFIED.equals(action)) {
      result = CommitEventType.sending;
    } else if (SVNEventAction.COMMIT_REPLACED.equals(action)) {
      result = CommitEventType.replacing;
    } else if (SVNEventAction.COMMIT_DELTA_SENT.equals(action)) {
      result = CommitEventType.transmittingDeltas;
    } else if (SVNEventAction.SKIP.equals(action)) {
      result = CommitEventType.skipped;
    }

    if (result == null) {
      throw new IllegalArgumentException("Unknown action " + action);
    }

    return result;
  }
}
