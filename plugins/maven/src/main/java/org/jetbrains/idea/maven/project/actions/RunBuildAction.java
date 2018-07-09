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
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenDataKeys;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import java.util.List;

public class RunBuildAction extends MavenAction {
  @Override
  protected boolean isAvailable(AnActionEvent e) {
    return super.isAvailable(e) && checkOrPerform(e.getDataContext(), false);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    checkOrPerform(e.getDataContext(), true);
  }

  private static boolean checkOrPerform(DataContext context, boolean perform) {
    final List<String> goals = MavenDataKeys.MAVEN_GOALS.getData(context);
    if (goals == null || goals.isEmpty()) return false;

    final Project project = MavenActionUtil.getProject(context);
    if(project == null) return false;
    final MavenProject mavenProject = MavenActionUtil.getMavenProject(context);
    if (mavenProject == null) return false;

    if (!perform) return true;

    final MavenProjectsManager projectsManager = MavenActionUtil.getProjectsManager(context);
    if(projectsManager == null) return false;
    MavenExplicitProfiles explicitProfiles = projectsManager.getExplicitProfiles();
    final MavenRunnerParameters params = new MavenRunnerParameters(true,
                                                                   mavenProject.getDirectory(),
                                                                   mavenProject.getFile().getName(),
                                                                   goals,
                                                                   explicitProfiles.getEnabledProfiles(),
                                                                   explicitProfiles.getDisabledProfiles());
    MavenRunConfigurationType.runConfiguration(project, params, null);

    return true;
  }
}
