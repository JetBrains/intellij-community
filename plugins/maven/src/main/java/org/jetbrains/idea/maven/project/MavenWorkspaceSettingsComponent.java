// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTrackerSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.jetbrains.idea.maven.utils.MavenWslUtil;

import java.io.File;

@State(name = "MavenImportPreferences", storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)})
public class MavenWorkspaceSettingsComponent implements PersistentStateComponent<MavenWorkspaceSettings> {
  private MavenWorkspaceSettings mySettings;

  private final Project myProject;

  public MavenWorkspaceSettingsComponent(Project project) {
    myProject = project;
    mySettings = new MavenWorkspaceSettings();
    mySettings.generalSettings.setProject(project);
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      mySettings.generalSettings.setMavenHome(MavenServerManager.BUNDLED_MAVEN_3);
    }
    else {
      applyDefaults(mySettings);
    }
  }

  public static MavenWorkspaceSettingsComponent getInstance(Project project) {
    return ServiceManager.getService(project, MavenWorkspaceSettingsComponent.class);
  }

  @Override
  @NotNull
  public MavenWorkspaceSettings getState() {
    MavenExplicitProfiles profiles = MavenProjectsManager.getInstance(myProject).getExplicitProfiles();
    mySettings.setEnabledProfiles(profiles.getEnabledProfiles());
    mySettings.setDisabledProfiles(profiles.getDisabledProfiles());
    return mySettings;
  }

  @Override
  public void loadState(@NotNull MavenWorkspaceSettings state) {
    mySettings = state;
    applyDefaults(mySettings);
    migrateSettings(mySettings);
  }

  public MavenWorkspaceSettings getSettings() {
    return mySettings;
  }

  private void applyDefaults(MavenWorkspaceSettings settings) {
    settings.generalSettings.setProject(myProject);
    if (StringUtil.isEmptyOrSpaces(settings.generalSettings.getMavenHome())) {
      String home = MavenWslUtil.resolveWslAware(myProject,
                                              () -> MavenServerManager.BUNDLED_MAVEN_3,
                                              wsl -> {
                                                File file = MavenWslUtil.resolveMavenHomeDirectory(wsl, null);
                                                return file == null ? null : file.getAbsolutePath();
                                              });
      settings.generalSettings.setMavenHome(home);
    }
  }

  @SuppressWarnings("deprecation")
  private void migrateSettings(MavenWorkspaceSettings settings) {
    MavenImportingSettings importingSettings = settings.importingSettings;
    if (importingSettings.isImportAutomatically()) {
      importingSettings.setImportAutomatically(false);
      ExternalSystemProjectTrackerSettings projectTrackerSettings = ExternalSystemProjectTrackerSettings.getInstance(myProject);
      projectTrackerSettings.setAutoReloadType(ExternalSystemProjectTrackerSettings.AutoReloadType.ALL);
    }
  }
}
