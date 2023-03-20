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

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProjectBundle;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;
import org.jetbrains.idea.maven.utils.actions.MavenToggleAction;

public class ToggleOfflineAction extends MavenToggleAction {
  private static final Logger LOG = Logger.getInstance(ToggleOfflineAction.class);

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);

    if (ActionPlaces.ACTION_SEARCH.equals(e.getPlace())) {
      Presentation p = e.getPresentation();
      p.setText(MavenProjectBundle.message("maven.toggle.offline.search.title"));
    }
  }

  @Override
  protected boolean doIsSelected(AnActionEvent e) {
    final MavenProjectsManager projectsManager = MavenActionUtil.getProjectsManager(e.getDataContext());
    return projectsManager != null && projectsManager.getGeneralSettings().isWorkOffline();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    final MavenProjectsManager projectsManager = MavenActionUtil.getProjectsManager(e.getDataContext());
    if (projectsManager != null) {
      projectsManager.getGeneralSettings().setWorkOffline(state);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}