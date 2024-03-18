// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTrackerSettings;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;


@State(name = "MavenImportPreferences", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
@SuppressWarnings("LightServiceMigrationCode")
// must be not a light service,
// because SystemFileProcessor uses ComponentManagerEx.getServiceByClassName API to get instance of this service
public final class MavenWorkspaceSettingsComponent implements PersistentStateComponent<MavenWorkspacePersistedSettings> {
  private MavenWorkspacePersistedSettings mySettings;

  private final Project myProject;

  public MavenWorkspaceSettingsComponent(@NotNull Project project) {
    myProject = project;
    mySettings = new MavenWorkspacePersistedSettings(new MavenWorkspaceSettings());
    mySettings.getGeneralSettings().setProject(project);
    applyDefaults(mySettings.getRealSettings());
  }

  public static MavenWorkspaceSettingsComponent getInstance(@NotNull Project project) {
    return project.getService(MavenWorkspaceSettingsComponent.class);
  }

  @Override
  @NotNull
  public MavenWorkspacePersistedSettings getState() {
    MavenExplicitProfiles profiles = MavenProjectsManager.getInstance(myProject).getExplicitProfiles();
    mySettings.setEnabledProfiles(profiles.getEnabledProfiles());
    mySettings.setDisabledProfiles(profiles.getDisabledProfiles());
    return mySettings;
  }

  @Override
  public void loadState(@NotNull MavenWorkspacePersistedSettings state) {
    mySettings = state;
    applyDefaults(mySettings.getRealSettings());
    migrateSettings(mySettings.getRealSettings());
  }

  public MavenWorkspaceSettings getSettings() {
    return mySettings.getRealSettings();
  }

  private void applyDefaults(MavenWorkspaceSettings settings) {
    settings.getGeneralSettings().setProject(myProject);
  }

  @SuppressWarnings("removal")
  private void migrateSettings(MavenWorkspaceSettings settings) {
    MavenImportingSettings importingSettings = settings.getImportingSettings();
    if (importingSettings.isImportAutomatically()) {
      importingSettings.setImportAutomatically(false);
      ExternalSystemProjectTrackerSettings projectTrackerSettings = ExternalSystemProjectTrackerSettings.getInstance(myProject);
      projectTrackerSettings.setAutoReloadType(ExternalSystemProjectTrackerSettings.AutoReloadType.ALL);
    }
  }
}
