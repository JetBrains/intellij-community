/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;
import java.util.Properties;

public class MavenServerSettings implements Serializable, Cloneable {
  public enum UpdatePolicy {
    ALWAYS_UPDATE, DO_NOT_UPDATE
  }

  /**
   * do not use debug level {@link MavenServerConsole#LEVEL_DEBUG} by default, it can pollute logs
   */
  private int myLoggingLevel = MavenServerConsole.LEVEL_INFO;
  @Nullable private File myMavenHome;
  @Nullable private File myUserSettingsFile;
  @Nullable private File myGlobalSettingsFile;
  @Nullable private File myLocalRepository;
  @NotNull private Properties myUserProperties = new Properties();
  private boolean isOffline;
  @NotNull private UpdatePolicy myPluginUpdatePolicy = UpdatePolicy.DO_NOT_UPDATE;
  @NotNull private UpdatePolicy mySnapshotUpdatePolicy = UpdatePolicy.ALWAYS_UPDATE;

  private String projectJdk;

  @Nullable
  public String getProjectJdk() {
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

  @NotNull
  public Properties getUserProperties() {
    return myUserProperties;
  }

  public void setUserProperties(@NotNull Properties properties) {
    myUserProperties = properties;
  }

  @Nullable
  public File getMavenHome() {
    return myMavenHome;
  }

  public void setMavenHome(@Nullable File mavenHome) {
    myMavenHome = mavenHome;
  }

  @Nullable
  public File getUserSettingsFile() {
    return myUserSettingsFile;
  }

  public void setUserSettingsFile(@Nullable File userSettingsFile) {
    myUserSettingsFile = userSettingsFile;
  }

  @Nullable
  public File getGlobalSettingsFile() {
    return myGlobalSettingsFile;
  }

  public void setGlobalSettingsFile(@Nullable File globalSettingsFile) {
    myGlobalSettingsFile = globalSettingsFile;
  }

  @Nullable
  public File getLocalRepository() {
    return myLocalRepository;
  }

  public void setLocalRepository(@Nullable File localRepository) {
    myLocalRepository = localRepository;
  }

  public boolean isOffline() {
    return isOffline;
  }

  public void setOffline(boolean offline) {
    isOffline = offline;
  }

  @NotNull
  public UpdatePolicy getPluginUpdatePolicy() {
    return myPluginUpdatePolicy;
  }

  public void setPluginUpdatePolicy(@NotNull UpdatePolicy pluginUpdatePolicy) {
    myPluginUpdatePolicy = pluginUpdatePolicy;
  }

  @NotNull
  public UpdatePolicy getSnapshotUpdatePolicy() {
    return mySnapshotUpdatePolicy;
  }

  public void setSnapshotUpdatePolicy(@NotNull UpdatePolicy snapshotUpdatePolicy) {
    mySnapshotUpdatePolicy = snapshotUpdatePolicy;
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
