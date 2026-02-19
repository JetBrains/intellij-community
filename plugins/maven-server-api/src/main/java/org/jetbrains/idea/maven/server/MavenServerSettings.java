// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.Properties;

public class MavenServerSettings implements Serializable, Cloneable {
  /**
   * do not use debug level {@link MavenServerConsoleIndicator#LEVEL_DEBUG} by default, it can pollute logs
   */
  private int myLoggingLevel = MavenServerConsoleIndicator.LEVEL_INFO;
  private @Nullable String myMavenHome;
  private @Nullable String myUserSettingsFile;
  private @Nullable String myGlobalSettingsFile;
  private @Nullable String myLocalRepository;
  private @NotNull Properties myUserProperties = new Properties();

  private boolean updateSnapshots;
  private boolean isOffline;

  private String projectJdk;

  public @Nullable String getProjectJdk() {
    return projectJdk;
  }

  public void setProjectJdk(@Nullable String projectJdk) {
    this.projectJdk = projectJdk;
  }

  public int getLoggingLevel() {
    return myLoggingLevel;
  }

  public void setLoggingLevel(int loggingLevel) {
    myLoggingLevel = loggingLevel;
  }

  public @NotNull Properties getUserProperties() {
    return myUserProperties;
  }

  public void setUserProperties(@NotNull Properties properties) {
    myUserProperties = properties;
  }

  public @Nullable String getMavenHomePath() {
    return myMavenHome;
  }

  public void setMavenHomePath(@Nullable String mavenHome) {
    myMavenHome = mavenHome;
  }

  public @Nullable String getUserSettingsPath() {
    return myUserSettingsFile;
  }

  public void setUserSettingsPath(@Nullable String userSettingsFile) {
    myUserSettingsFile = userSettingsFile;
  }

  public @Nullable String getGlobalSettingsPath() {
    return myGlobalSettingsFile;
  }

  public void setGlobalSettingsPath(@Nullable String globalSettingsFile) {
    myGlobalSettingsFile = globalSettingsFile;
  }

  public @Nullable String getLocalRepositoryPath() {
    return myLocalRepository;
  }

  public void setLocalRepositoryPath(@Nullable String localRepository) {
    myLocalRepository = localRepository;
  }

  public boolean isOffline() {
    return isOffline;
  }

  public void setOffline(boolean offline) {
    isOffline = offline;
  }

  public boolean isUpdateSnapshots() {
    return updateSnapshots;
  }

  public void setUpdateSnapshots(boolean updateSnapshots) {
    this.updateSnapshots = updateSnapshots;
  }

  @Override
  public MavenServerSettings clone() {
    try {
      return (MavenServerSettings)super.clone();
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }
}
