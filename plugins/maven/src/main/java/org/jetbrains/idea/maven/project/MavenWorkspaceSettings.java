// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class MavenWorkspaceSettings {

  private MavenGeneralSettings generalSettings = new MavenGeneralSettings();
  private MavenImportingSettings importingSettings = new MavenImportingSettings();

  public List<String> enabledProfiles = new ArrayList<>();
  public List<String> disabledProfiles = new ArrayList<>();

  public void setEnabledProfiles(Collection<String> profiles) {
    enabledProfiles.clear();
    enabledProfiles.addAll(profiles);
  }

  public void setDisabledProfiles(Collection<String> profiles) {
    disabledProfiles.clear();
    disabledProfiles.addAll(profiles);
  }

  public MavenGeneralSettings getGeneralSettings() {
    return generalSettings;
  }

  public void setGeneralSettings(MavenGeneralSettings generalSettings) {
    this.generalSettings = generalSettings;
  }

  public MavenImportingSettings getImportingSettings() {
    return importingSettings;
  }

  public void setImportingSettings(MavenImportingSettings importingSettings) {
    this.importingSettings = importingSettings;
  }
}