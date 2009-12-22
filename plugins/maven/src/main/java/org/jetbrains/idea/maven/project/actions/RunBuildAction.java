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
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.utils.MavenDataKeys;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import java.util.List;

public class RunBuildAction extends MavenAction {
  @Override
  protected boolean isAvailable(AnActionEvent e) {
    return super.isAvailable(e) && checkOrPerform(e, false);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    checkOrPerform(e, true);
  }

  private boolean checkOrPerform(AnActionEvent e, boolean perform) {
    MavenProject project = MavenActionUtil.getMavenProject(e);
    if (project == null) return false;

    List<String> goals = e.getData(MavenDataKeys.MAVEN_GOALS);
    if (goals == null || goals.isEmpty()) return false;

    if (!perform) return true;

    MavenRunnerParameters params = new MavenRunnerParameters(
      true, project.getDirectory(), goals, MavenActionUtil.getProjectsManager(e).getExplicitProfiles());
    MavenRunConfigurationType.runConfiguration(MavenActionUtil.getProject(e), params, e.getDataContext(), null);

    return true;
  }
}
