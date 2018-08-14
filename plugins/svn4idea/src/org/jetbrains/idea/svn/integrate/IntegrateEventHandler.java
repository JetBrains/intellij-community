// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.update.FileGroup;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.EventAction;
import org.jetbrains.idea.svn.api.ProgressEvent;
import org.jetbrains.idea.svn.status.StatusType;
import org.jetbrains.idea.svn.update.UpdateEventHandler;

public class IntegrateEventHandler extends UpdateEventHandler {
  public IntegrateEventHandler(final SvnVcs vcs, final ProgressIndicator progressIndicator) {
    super(vcs, progressIndicator, null);
  }

  @Override
  protected boolean handleInDescendants(final ProgressEvent event) {
    if ((event.getAction() == EventAction.UPDATE_UPDATE) && (event.getContentsStatus() == StatusType.UNCHANGED) &&
          (event.getPropertiesStatus() == StatusType.UNKNOWN)) {
        myText2 = SvnBundle.message("progres.text2.updated", event.getFile().getName());
      return true;
    } else if (event.getAction() == EventAction.DELETE) {
      addFileToGroup(FileGroup.REMOVED_FROM_REPOSITORY_ID, event);
      myText2 = SvnBundle.message("progress.text2.deleted", event.getFile().getName());
      return true;
    }
    return false;
  }
}
