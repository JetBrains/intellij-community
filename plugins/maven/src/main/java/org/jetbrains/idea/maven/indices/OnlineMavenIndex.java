// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.indices;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.io.File;

/**
 * @deprecated this class used only for backward compatibility for legacy code, which expect to get MavenIndices. To be removed in future releases
 */
@Deprecated
@ApiStatus.ScheduledForRemoval
public class OnlineMavenIndex implements MavenSearchIndex {
  private final String myRepositoryId;
  private final String myRepositoryUrl;

  public OnlineMavenIndex(String repositoryId, String repositoryUrl) {
    myRepositoryId = repositoryId;
    myRepositoryUrl = repositoryUrl;
  }

  @Override
  public void registerId(String repositoryId) throws MavenIndexException {
  }

  @Override
  public void close(boolean releaseIndexContext) {

  }

  @Override
  public String getRepositoryId() {
    return myRepositoryId;
  }

  @Override
  public File getRepositoryFile() {
    return null;
  }

  @Override
  public String getRepositoryUrl() {
    return myRepositoryUrl;
  }

  @Override
  public String getRepositoryPathOrUrl() {
    return myRepositoryUrl;
  }

  @Override
  public Kind getKind() {
    return Kind.ONLINE;
  }

  @Override
  public boolean isFor(Kind kind, String pathOrUrl) {
    return kind == Kind.ONLINE && myRepositoryUrl.equals(pathOrUrl);
  }

  @Override
  public long getUpdateTimestamp() {
    return -1;
  }

  @Override
  public String getFailureMessage() {
    return null;
  }

  @Override
  public void updateOrRepair(boolean fullUpdate, MavenGeneralSettings settings, MavenProgressIndicator progress)
    throws MavenProcessCanceledException {

  }
}
