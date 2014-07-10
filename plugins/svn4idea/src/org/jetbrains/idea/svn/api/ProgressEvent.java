/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.status.StatusType;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNEvent;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public class ProgressEvent {

  private final File myFile;

  private final long myRevision;
  private final SVNURL myURL;

  @NotNull private final StatusType myContentsStatus;
  @NotNull private final StatusType myPropertiesStatus;
  private final SVNErrorMessage myErrorMessage;
  private final EventAction myAction;

  @Nullable
  public static ProgressEvent create(@Nullable SVNEvent event) {
    ProgressEvent result = null;

    if (event != null) {
      if (event.getFile() == null && event.getURL() == null) {
        result = new ProgressEvent(event.getErrorMessage());
      }
      else {
        result =
          new ProgressEvent(event.getFile(), event.getRevision(), StatusType.from(event.getContentsStatus()), StatusType.from(event.getPropertiesStatus()),
                            EventAction.from(event.getAction()), event.getErrorMessage(), event.getURL());
      }
    }

    return result;
  }

  public ProgressEvent(SVNErrorMessage errorMessage) {
    this(null, 0, null, null, EventAction.SKIP, errorMessage, null);
  }

  public ProgressEvent(File file,
                       long revision,
                       @Nullable StatusType contentStatus,
                       @Nullable StatusType propertiesStatus,
                       EventAction action,
                       SVNErrorMessage error,
                       SVNURL url) {
    myFile = file != null ? file.getAbsoluteFile() : null;
    myRevision = revision;
    myContentsStatus = contentStatus == null ? StatusType.INAPPLICABLE : contentStatus;
    myPropertiesStatus = propertiesStatus == null ? StatusType.INAPPLICABLE : propertiesStatus;
    myAction = action;
    myErrorMessage = error;
    myURL = url;
  }

  public File getFile() {
    return myFile;
  }

  public EventAction getAction() {
    return myAction;
  }

  @NotNull
  public StatusType getContentsStatus() {
    return myContentsStatus;
  }

  public SVNErrorMessage getErrorMessage() {
    return myErrorMessage;
  }

  @NotNull
  public StatusType getPropertiesStatus() {
    return myPropertiesStatus;
  }

  public long getRevision() {
    return myRevision;
  }

  public SVNURL getURL() {
    return myURL;
  }

  @Nullable
  public String getPath() {
    return myFile != null ? myFile.getName() : myURL != null ? myURL.toString() : null;
  }

  public String toString() {
    return getAction() + " " + getFile() + " " + getURL();
  }
}