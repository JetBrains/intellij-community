// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.util.xmlb.annotations.Transient;

import java.util.Collection;

public final class MavenWorkspacePersistedSettings {

  private final MavenWorkspaceSettings wrappee;

  public MavenWorkspacePersistedSettings(MavenWorkspaceSettings wrappee) { this.wrappee = wrappee; }

  public MavenWorkspacePersistedSettings() { this.wrappee = new MavenWorkspaceSettings(); }

  public void setEnabledProfiles(Collection<String> profiles) {
    wrappee.setEnabledProfiles(profiles);
  }

  public void setDisabledProfiles(Collection<String> profiles) {
    wrappee.setDisabledProfiles(profiles);
  }

  public MavenGeneralSettings getGeneralSettings() {
    return wrappee.getGeneralSettings().cloneForPersistence();
  }

  public void setGeneralSettings(MavenGeneralSettings generalSettings) {
    wrappee.setGeneralSettings(generalSettings);
  }

  public MavenImportingSettings getImportingSettings() {
    return wrappee.getImportingSettings();
  }

  public void setImportingSettings(MavenImportingSettings importingSettings) {
    wrappee.setImportingSettings(importingSettings);
  }

  @Transient
  MavenWorkspaceSettings getRealSettings() {
    return wrappee;
  }
}