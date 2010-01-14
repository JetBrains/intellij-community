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
package org.jetbrains.idea.maven.project.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.ProjectBundle;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;
import org.jetbrains.idea.maven.utils.MavenDataKeys;

import java.util.Collection;
import java.util.List;

public class ToggleProfileAction extends MavenAction {
  public void update(AnActionEvent e) {
    super.update(e);
    if (!isAvailable(e)) return;

    MavenProjectsManager projectsManager = MavenActionUtil.getProjectsManager(e);
    List<String> profiles = e.getData(MavenDataKeys.MAVEN_PROFILES);

    e.getPresentation().setText(isActive(projectsManager, profiles)
                                ? ProjectBundle.message("maven.profile.deactivate")
                                : ProjectBundle.message("maven.profile.activate"));
  }

  @Override
  protected boolean isAvailable(AnActionEvent e) {
    if (!super.isAvailable(e)) return false;

    List<String> selectedProfiles = e.getData(MavenDataKeys.MAVEN_PROFILES);
    if (selectedProfiles == null || selectedProfiles.isEmpty()) return false;

    Collection<String> activeProfiles = MavenActionUtil.getProjectsManager(e).getExplicitProfiles();
    int activeCount = 0;
    for (String profile : selectedProfiles) {
      if (activeProfiles.contains(profile)) {
        activeCount++;
      }
    }
    return activeCount == 0 || activeCount == selectedProfiles.size();
  }

  private boolean isActive(MavenProjectsManager projectsManager, List<String> profiles) {
    return projectsManager.getExplicitProfiles().contains(profiles.get(0));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    MavenProjectsManager manager = MavenActionUtil.getProjectsManager(e);
    List<String> selectedProfiles = e.getData(MavenDataKeys.MAVEN_PROFILES);

    Collection<String> activeProfiles = manager.getExplicitProfiles();
    if (isActive(manager, selectedProfiles)) {
      activeProfiles.removeAll(selectedProfiles);
    }
    else {
      activeProfiles.addAll(selectedProfiles);
    }
    manager.setExplicitProfiles(activeProfiles);
  }
}
