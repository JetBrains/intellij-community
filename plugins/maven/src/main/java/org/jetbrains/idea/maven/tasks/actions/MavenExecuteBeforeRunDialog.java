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

import com.intellij.execution.impl.BaseExecuteBeforeRunDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.tasks.MavenBeforeRunTask;
import org.jetbrains.idea.maven.tasks.MavenBeforeRunTasksProvider;
import org.jetbrains.idea.maven.tasks.TasksBundle;

public class MavenExecuteBeforeRunDialog extends BaseExecuteBeforeRunDialog<MavenBeforeRunTask> {
  private final MavenProject myMavenProject;
  private final String myGoal;

  public MavenExecuteBeforeRunDialog(Project project, MavenProject mavenProject, String goal) {
    super(project);
    myMavenProject = mavenProject;
    myGoal = goal;
    init();
  }

  @Override
  protected String getTargetDisplayString() {
    return TasksBundle.message("maven.tasks.goal");
  }

  @Override
  protected Key<MavenBeforeRunTask> getTaskID() {
    return MavenBeforeRunTasksProvider.TASK_ID;
  }

  @Override
  protected boolean isRunning(MavenBeforeRunTask task) {
    return task.isFor(myMavenProject, myGoal);
  }

  @Override
  protected void update(MavenBeforeRunTask task) {
    task.setProjectPath(myMavenProject.getPath());
    task.setGoal(myGoal);
  }

  @Override
  protected void clear(MavenBeforeRunTask task) {
    task.setProjectPath(null);
    task.setGoal(null);
  }
}
