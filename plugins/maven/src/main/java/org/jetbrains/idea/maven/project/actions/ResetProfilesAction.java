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
package org.jetbrains.idea.maven.project.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

public class ResetProfilesAction extends MavenAction {
  @Override
  protected boolean isAvailable(@NotNull AnActionEvent e) {
    if (!super.isAvailable(e)) return false;

    MavenProjectsManager manager = MavenActionUtil.getProjectsManager(e.getDataContext());
    if (manager == null) return false;

    if (manager.getAvailableProfiles().isEmpty()) return false;

    MavenExplicitProfiles explicitProfiles = manager.getExplicitProfiles();

    return !explicitProfiles.getEnabledProfiles().isEmpty() || !explicitProfiles.getDisabledProfiles().isEmpty();
  }


  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    MavenProjectsManager manager = MavenActionUtil.getProjectsManager(e.getDataContext());
    if (manager == null) return;
    manager.setExplicitProfiles(MavenExplicitProfiles.NONE);
  }
}
