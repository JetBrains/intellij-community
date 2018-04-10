// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.status.StatusType;

import java.io.File;

public class ProgressEvent {

  private final File myFile;

  private final long myRevision;
  private final Url myURL;

  @NotNull private final StatusType myContentsStatus;
  @NotNull private final StatusType myPropertiesStatus;
  @Nullable private final String myErrorMessage;
  private final EventAction myAction;

  public ProgressEvent(@Nullable String errorMessage) {
    this(null, 0, null, null, EventAction.SKIP, errorMessage, null);
  }

  public ProgressEvent(File file,
                       long revision,
                       @Nullable StatusType contentStatus,
                       @Nullable StatusType propertiesStatus,
                       EventAction action,
                       @Nullable String errorMessage,
                       Url url) {
    myFile = file != null ? file.getAbsoluteFile() : null;
    myRevision = revision;
    myContentsStatus = contentStatus == null ? StatusType.INAPPLICABLE : contentStatus;
    myPropertiesStatus = propertiesStatus == null ? StatusType.INAPPLICABLE : propertiesStatus;
    myAction = action;
    myErrorMessage = errorMessage;
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

  @Nullable
  public String getErrorMessage() {
    return myErrorMessage;
  }

  @NotNull
  public StatusType getPropertiesStatus() {
    return myPropertiesStatus;
  }

  public long getRevision() {
    return myRevision;
  }

  public Url getURL() {
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