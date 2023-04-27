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

import java.io.Serializable;
import java.util.Properties;

public class MavenServerSettings implements Serializable, Cloneable {
  /**
   * do not use debug level {@link MavenServerConsole#LEVEL_DEBUG} by default, it can pollute logs
   */
  private int myLoggingLevel = MavenServerConsole.LEVEL_INFO;
  @Nullable private String myMavenHome;
  @Nullable private String myUserSettingsFile;
  @Nullable private String myGlobalSettingsFile;
  @Nullable private String myLocalRepository;
  @NotNull private Properties myUserProperties = new Properties();
  private boolean isOffline;

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
  public String getMavenHomePath() {
    return myMavenHome;
  }

  public void setMavenHomePath(@Nullable String mavenHome) {
    myMavenHome = mavenHome;
  }

  @Nullable
  public String getUserSettingsPath() {
    return myUserSettingsFile;
  }

  public void setUserSettingsPath(@Nullable String userSettingsFile) {
    myUserSettingsFile = userSettingsFile;
  }

  @Nullable
  public String getGlobalSettingsPath() {
    return myGlobalSettingsFile;
  }

  public void setGlobalSettingsPath(@Nullable String globalSettingsFile) {
    myGlobalSettingsFile = globalSettingsFile;
  }

  @Nullable
  public String getLocalRepositoryPath() {
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
