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
