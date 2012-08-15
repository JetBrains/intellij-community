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
package org.jetbrains.maven.embedder;

import org.codehaus.plexus.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Properties;

public class MavenEmbedderSettings {
  public enum UpdatePolicy {
    ALWAYS_UPDATE, DO_NOT_UPDATE
  }

  private boolean workOffline = false;
  private boolean isRecursive = true;
  private File mavenHome;
  private File userSettingsFile;
  private File globalSettingsFile;
  private File localRepository;
  private boolean usePluginRegistry = false;
  private UpdatePolicy snapshotUpdatePolicy = UpdatePolicy.ALWAYS_UPDATE;
  private UpdatePolicy pluginUpdatePolicy = UpdatePolicy.DO_NOT_UPDATE;
  private Properties myProperties;

  @Nullable
  private Logger myLogger;
  @Nullable
  private PlexusComponentConfigurator myConfigurator;

  public boolean isWorkOffline() {
    return workOffline;
  }

  public void setWorkOffline(boolean workOffline) {
    this.workOffline = workOffline;
  }

  public boolean isRecursive() {
    return isRecursive;
  }

  public void setRecursive(boolean recursive) {
    isRecursive = recursive;
  }

  @Nullable
  public File getMavenHome() {
    return mavenHome;
  }

  public void setMavenHome(@Nullable File mavenHome) {
    this.mavenHome = mavenHome;
  }

  @Nullable
  public File getUserSettingsFile() {
    return userSettingsFile;
  }

  public void setUserSettingsFile(@Nullable File userSettingsFile) {
    this.userSettingsFile = userSettingsFile;
  }

  @Nullable
  public File getGlobalSettingsFile() {
    return globalSettingsFile;
  }

  public void setGlobalSettingsFile(@Nullable File globalSettingsFile) {
    this.globalSettingsFile = globalSettingsFile;
  }

  @Nullable
  public File getLocalRepository() {
    return localRepository;
  }

  public void setLocalRepository(@Nullable File localRepository) {
    this.localRepository = localRepository;
  }

  public boolean isUsePluginRegistry() {
    return usePluginRegistry;
  }

  public void setUsePluginRegistry(boolean usePluginRegistry) {
    this.usePluginRegistry = usePluginRegistry;
  }

  @NotNull
  public UpdatePolicy getPluginUpdatePolicy() {
    return pluginUpdatePolicy;
  }

  public void setPluginUpdatePolicy(@Nullable UpdatePolicy value) {
    this.pluginUpdatePolicy = value;
  }

  @NotNull
  public UpdatePolicy getSnapshotUpdatePolicy() {
    return snapshotUpdatePolicy;
  }

  public void setSnapshotUpdatePolicy(@Nullable UpdatePolicy value) {
    this.snapshotUpdatePolicy = value;
  }

  @Nullable
  public Properties getProperties() {
    return myProperties;
  }

  public void setProperties(@Nullable Properties properties) {
    myProperties = properties;
  }

  @Nullable
  public Logger getLogger() {
    return myLogger;
  }

  public void setLogger(@Nullable final Logger logger) {
    myLogger = logger;
  }


  public void setConfigurator(@Nullable final PlexusComponentConfigurator configurator) {
    myConfigurator = configurator;
  }

  @Nullable
  public PlexusComponentConfigurator getConfigurator() {
    return myConfigurator;
  }
}
