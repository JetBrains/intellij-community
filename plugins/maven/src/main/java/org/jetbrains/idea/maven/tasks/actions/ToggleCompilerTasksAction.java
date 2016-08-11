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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.tasks.MavenCompilerTask;
import org.jetbrains.idea.maven.tasks.MavenTasksManager;
import org.jetbrains.idea.maven.utils.MavenDataKeys;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;
import org.jetbrains.idea.maven.utils.actions.MavenToggleAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class ToggleCompilerTasksAction extends MavenToggleAction {

  private final MavenTasksManager.Phase myPhase;

  protected ToggleCompilerTasksAction(MavenTasksManager.Phase phase) {
    myPhase = phase;
  }

  @Override
  protected boolean isAvailable(AnActionEvent e) {
    return super.isAvailable(e) && !getTasks(e.getDataContext()).isEmpty();
  }

  @Override
  protected boolean doIsSelected(AnActionEvent e) {
    final DataContext context = e.getDataContext();
    return hasTask(getTasksManager(context), getTasks(context).get(0));
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    final DataContext context = e.getDataContext();
    final MavenTasksManager tasksManager = getTasksManager(context);
    List<MavenCompilerTask> tasks = getTasks(context);
    if(tasksManager == null) return;
    if (state) {
      addTasks(tasksManager, tasks);
    }
    else {
      removeTasks(tasksManager, tasks);
    }
  }

  protected static List<MavenCompilerTask> getTasks(DataContext context) {
    final List<String> goals = MavenDataKeys.MAVEN_GOALS.getData(context);
    if (goals == null || goals.isEmpty()) return Collections.emptyList();

    MavenProject project = MavenActionUtil.getMavenProject(context);
    if (project == null) return Collections.emptyList();

    List<MavenCompilerTask> result = new ArrayList<>();
    for (String each : goals) {
      result.add(new MavenCompilerTask(project.getPath(), each));
    }
    return result;
  }

  protected boolean hasTask(@Nullable MavenTasksManager manager, MavenCompilerTask task) {
    if(manager == null) return false;
    return manager.isCompileTaskOfPhase(task, myPhase);
  }

  protected void addTasks(MavenTasksManager manager, List<MavenCompilerTask> tasks) {
    manager.addCompileTasks(tasks, myPhase);
  }

  protected void removeTasks(MavenTasksManager manager, List<MavenCompilerTask> tasks) {
    manager.removeCompileTasks(tasks, myPhase);
  }

  @Nullable
  private static MavenTasksManager getTasksManager(DataContext context) {
    final Project project = MavenActionUtil.getProject(context);
    if(project == null) return null;
    return MavenTasksManager.getInstance(project);
  }
}
