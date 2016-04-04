/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.server.MavenServerManager;

@State(name = "MavenImportPreferences", storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)})
public class MavenWorkspaceSettingsComponent implements PersistentStateComponent<MavenWorkspaceSettings> {
  private MavenWorkspaceSettings mySettings = new MavenWorkspaceSettings();

  private final Project myProject;

  public MavenWorkspaceSettingsComponent(Project project) {
    myProject = project;
    applyDefaults(mySettings);
  }

  public static MavenWorkspaceSettingsComponent getInstance(Project project) {
    return ServiceManager.getService(project, MavenWorkspaceSettingsComponent.class);
  }

  @NotNull
  public MavenWorkspaceSettings getState() {
    MavenExplicitProfiles profiles = MavenProjectsManager.getInstance(myProject).getExplicitProfiles();
    mySettings.setEnabledProfiles(profiles.getEnabledProfiles());
    mySettings.setDisabledProfiles(profiles.getDisabledProfiles());
    return mySettings;
  }

  public void loadState(MavenWorkspaceSettings state) {
    mySettings = state;
    applyDefaults(mySettings);
  }

  public MavenWorkspaceSettings getSettings() {
    return mySettings;
  }

  private static void applyDefaults(MavenWorkspaceSettings settings) {
    if(StringUtil.isEmptyOrSpaces(settings.generalSettings.getMavenHome())) {
      if(MavenServerManager.getInstance().isUsedMaven2ForProjectImport() || ApplicationManager.getApplication().isUnitTestMode()) {
        settings.generalSettings.setMavenHome(MavenServerManager.BUNDLED_MAVEN_2);
      } else {
        settings.generalSettings.setMavenHome(MavenServerManager.BUNDLED_MAVEN_3);
      }
    }
  }
}
