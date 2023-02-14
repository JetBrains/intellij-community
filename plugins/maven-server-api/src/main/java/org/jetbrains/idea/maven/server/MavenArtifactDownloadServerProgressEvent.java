// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import java.io.Serializable;

public class MavenArtifactDownloadServerProgressEvent implements Serializable {

  public enum ArtifactEventType {
    DOWNLOAD_STARTED,
    DOWNLOAD_COMPLETED,
    DOWNLOAD_FAILED
  }

  private final MavenServerProgressIndicator.ResolveType myResolveType;
  private final ArtifactEventType myArtifactEventType;
  private final String myDependencyId;
  private final String myErrorMessage;
  private final String myStackTrace;

  public MavenArtifactDownloadServerProgressEvent(MavenServerProgressIndicator.ResolveType type,
                                                  ArtifactEventType eventType,
                                                  String id,
                                                  String message,
                                                  String trace) {
    myResolveType = type;
    myArtifactEventType = eventType;
    myDependencyId = id;
    myErrorMessage = message;
    myStackTrace = trace;
  }

  public MavenServerProgressIndicator.ResolveType getResolveType() {
    return myResolveType;
  }

  public ArtifactEventType getArtifactEventType() {
    return myArtifactEventType;
  }

  public String getDependencyId() {
    return myDependencyId;
  }

  public String getErrorMessage() {
    return myErrorMessage;
  }

  public String getStackTrace() {
    return myStackTrace;
  }
}
