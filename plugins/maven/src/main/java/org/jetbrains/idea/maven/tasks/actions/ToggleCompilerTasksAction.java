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

import com.intellij.openapi.actionSystem.AnActionEvent;
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
  @Override
  protected boolean isAvailable(AnActionEvent e) {
    return super.isAvailable(e) && !getTasks(e).isEmpty();
  }

  @Override
  protected boolean doIsSelected(AnActionEvent e) {
    return hasTask(getTasksManager(e), getTasks(e).get(0));
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    List<MavenCompilerTask> tasks = getTasks(e);
    if (state) {
      addTasks(getTasksManager(e), tasks);
    }
    else {
      removeTasks(getTasksManager(e), tasks);
    }
  }

  protected List<MavenCompilerTask> getTasks(AnActionEvent e) {
    MavenProject project = MavenActionUtil.getMavenProject(e);
    if (project == null) return Collections.EMPTY_LIST;

    List<String> goals = e.getData(MavenDataKeys.MAVEN_GOALS);
    if (goals == null || goals.isEmpty()) return Collections.EMPTY_LIST;

    List<MavenCompilerTask> result = new ArrayList<MavenCompilerTask>();
    for (String each : goals) {
      result.add(new MavenCompilerTask(project.getPath(), each));
    }
    return result;
  }

  protected abstract boolean hasTask(MavenTasksManager manager, MavenCompilerTask task);

  protected abstract void addTasks(MavenTasksManager manager, List<MavenCompilerTask> tasks);

  protected abstract void removeTasks(MavenTasksManager manager, List<MavenCompilerTask> tasks);

  private MavenTasksManager getTasksManager(AnActionEvent e) {
    return MavenTasksManager.getInstance(MavenActionUtil.getProject(e));
  }
}
