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
package org.jetbrains.idea.maven.tasks.actions;

import com.intellij.execution.RunManagerEx;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.tasks.MavenBeforeRunTask;
import org.jetbrains.idea.maven.tasks.MavenBeforeRunTasksProvider;
import org.jetbrains.idea.maven.utils.MavenDataKeys;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;
import org.jetbrains.idea.maven.utils.actions.MavenToggleAction;

import java.util.List;

public class ToggleBeforeRunTaskAction extends MavenToggleAction {
  @Override
  protected boolean isAvailable(AnActionEvent e) {
    return super.isAvailable(e) && getTaskDesc(e.getDataContext()) != null;
  }

  @Override
  protected boolean doIsSelected(AnActionEvent e) {
    final DataContext context = e.getDataContext();
    final Pair<MavenProject, String> desc = getTaskDesc(context);
    if (desc != null) {
      final RunManagerEx runManager = getRunManager(context);
      if(runManager == null) return false;
      for (MavenBeforeRunTask each : runManager.getBeforeRunTasks(MavenBeforeRunTasksProvider.ID)) {
        if (each.isFor(desc.first, desc.second)) return true;
      }
    }
    return false;
  }

  @Override
  public void setSelected(final AnActionEvent e, boolean state) {
    final DataContext context = e.getDataContext();
    final Pair<MavenProject, String> desc = getTaskDesc(context);
    if (desc != null) {
      new MavenExecuteBeforeRunDialog(MavenActionUtil.getProject(context), desc.first, desc.second).show();
    }
  }

  @Nullable
  protected static Pair<MavenProject, String> getTaskDesc(DataContext context) {
    List<String> goals = MavenDataKeys.MAVEN_GOALS.getData(context);
    if (goals == null || goals.size() != 1) return null;

    MavenProject mavenProject = MavenActionUtil.getMavenProject(context);
    if (mavenProject == null) return null;


    return Pair.create(mavenProject, goals.get(0));
  }

  @Nullable
  private static RunManagerEx getRunManager(DataContext context) {
    final Project project = MavenActionUtil.getProject(context);
    if(project == null) return null;
    return RunManagerEx.getInstanceEx(project);
  }
}
