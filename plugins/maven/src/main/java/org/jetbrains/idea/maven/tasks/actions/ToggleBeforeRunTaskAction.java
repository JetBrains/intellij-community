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
package org.jetbrains.idea.maven.tasks.actions;

import com.intellij.execution.RunManagerEx;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.Pair;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.tasks.MavenBeforeRunTask;
import org.jetbrains.idea.maven.tasks.MavenBeforeRunTasksProvider;
import org.jetbrains.idea.maven.tasks.MavenTasksManager;
import org.jetbrains.idea.maven.utils.MavenDataKeys;
import org.jetbrains.idea.maven.utils.actions.MavenToggleAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import java.util.List;

public class ToggleBeforeRunTaskAction extends MavenToggleAction {
  @Override
  protected boolean isAvailable(AnActionEvent e) {
    return super.isAvailable(e) && getTaskDesc(e) != null;
  }

  @Override
  protected boolean doIsSelected(AnActionEvent e) {
    Pair<MavenProject, String> desc = getTaskDesc(e);
    for (MavenBeforeRunTask each : getRunManager(e).getBeforeRunTasks(MavenBeforeRunTasksProvider.TASK_ID, true)) {
      if (each.isEnabled() && each.isFor(desc.first, desc.second)) return true;
    }
    return false;
  }

  @Override
  public void setSelected(final AnActionEvent e, boolean state) {
    Pair<MavenProject, String> desc = getTaskDesc(e);
    new MavenExecuteBeforeRunDialog(MavenActionUtil.getProject(e), desc.first, desc.second).show();
    MavenTasksManager.getInstance(MavenActionUtil.getProject(e)).fireTasksChanged();
  }

  protected Pair<MavenProject, String> getTaskDesc(AnActionEvent e) {
    MavenProject mavenProject = MavenActionUtil.getMavenProject(e);
    if (mavenProject == null) return null;

    List<String> goals = e.getData(MavenDataKeys.MAVEN_GOALS);
    if (goals == null || goals.size() != 1) return null;

    return Pair.create(mavenProject, goals.get(0));
  }

  private RunManagerEx getRunManager(AnActionEvent e) {
    return RunManagerEx.getInstanceEx(MavenActionUtil.getProject(e));
  }
}
