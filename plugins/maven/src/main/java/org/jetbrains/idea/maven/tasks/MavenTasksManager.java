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
package org.jetbrains.idea.maven.tasks;

import com.intellij.execution.RunManagerEx;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.utils.SimpleProjectComponent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@State(name = "MavenCompilerTasksManager", storages = {@Storage(id = "default", file = "$PROJECT_FILE$")})
public class MavenTasksManager extends SimpleProjectComponent implements PersistentStateComponent<MavenTasksManagerState> {
  private final AtomicBoolean isInitialized = new AtomicBoolean();

  private MavenTasksManagerState myState = new MavenTasksManagerState();

  private final MavenProjectsManager myProjectsManager;
  private final MavenRunner myRunner;

  private final List<Listener> myListeners = ContainerUtil.createEmptyCOWList();

  public static MavenTasksManager getInstance(Project project) {
    return project.getComponent(MavenTasksManager.class);
  }

  public MavenTasksManager(Project project, MavenProjectsManager projectsManager, MavenRunner runner) {
    super(project);
    myProjectsManager = projectsManager;
    myRunner = runner;
  }

  public synchronized MavenTasksManagerState getState() {
    MavenTasksManagerState result = new MavenTasksManagerState();
    result.afterCompileTasks = new THashSet<MavenCompilerTask>(myState.afterCompileTasks);
    result.beforeCompileTasks = new THashSet<MavenCompilerTask>(myState.beforeCompileTasks);
    return result;
  }

  public void loadState(MavenTasksManagerState state) {
    synchronized (this) {
      myState = state;
    }
    if (isInitialized.get()) {
      fireTasksChanged();
    }
  }

  @Override
  public void initComponent() {
    if (!isNormalProject()) return;
    if (isInitialized.getAndSet(true)) return;

    CompilerManager compilerManager = CompilerManager.getInstance(myProject);

    compilerManager.addBeforeTask(new CompileTask() {
      public boolean execute(CompileContext context) {
        return doExecute(true, context);
      }
    });
    compilerManager.addAfterTask(new CompileTask() {
      public boolean execute(CompileContext context) {
        return doExecute(false, context);
      }
    });
  }

  private boolean doExecute(boolean before, CompileContext context) {
    List<MavenRunnerParameters> parametersList;
    synchronized (this) {
      parametersList = new ArrayList<MavenRunnerParameters>();
      Set<MavenCompilerTask> tasks = before ? myState.beforeCompileTasks : myState.afterCompileTasks;
      for (MavenCompilerTask each : tasks) {
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(each.getProjectPath());
        if (file == null) continue;
        parametersList.add(new MavenRunnerParameters(true,
                                                     file.getParent().getPath(),
                                                     Arrays.asList(each.getGoal()),
                                                     myProjectsManager.getExplicitProfiles()));
      }
    }
    return myRunner.runBatch(parametersList, null, null, TasksBundle.message("maven.tasks.executing"), context.getProgressIndicator());
  }

  public synchronized boolean isBeforeCompileTask(MavenCompilerTask task) {
    return myState.beforeCompileTasks.contains(task);
  }

  public void addBeforeCompileTasks(List<MavenCompilerTask> tasks) {
    synchronized (this) {
      myState.beforeCompileTasks.addAll(tasks);
    }
    fireTasksChanged();
  }

  public void removeBeforeCompileTasks(List<MavenCompilerTask> tasks) {
    synchronized (this) {
      myState.beforeCompileTasks.removeAll(tasks);
    }
    fireTasksChanged();
  }

  public synchronized boolean isAfterCompileTask(MavenCompilerTask task) {
    return myState.afterCompileTasks.contains(task);
  }

  public void addAfterCompileTasks(List<MavenCompilerTask> tasks) {
    synchronized (this) {
      myState.afterCompileTasks.addAll(tasks);
    }
    fireTasksChanged();
  }

  public void removeAfterCompileTasks(List<MavenCompilerTask> tasks) {
    synchronized (this) {
      myState.afterCompileTasks.removeAll(tasks);
    }
    fireTasksChanged();
  }

  public String getDescription(MavenProject project, String goal) {
    List<String> result = new ArrayList<String>();
    MavenCompilerTask compilerTask = new MavenCompilerTask(project.getPath(), goal);
    synchronized (this) {
      if (myState.beforeCompileTasks.contains(compilerTask)) {
        result.add(TasksBundle.message("maven.tasks.goal.before.compile"));
      }
      if (myState.afterCompileTasks.contains(compilerTask)) {
        result.add(TasksBundle.message("maven.tasks.goal.after.compile"));
      }
    }
    RunManagerEx runManager = RunManagerEx.getInstanceEx(myProject);
    for (MavenBeforeRunTask each : runManager.getBeforeRunTasks(MavenBeforeRunTasksProvider.TASK_ID, true)) {
      if (each.isFor(project, goal)) {
        result.add(TasksBundle.message("maven.tasks.goal.before.run"));
        break;
      }
    }

    return StringUtil.join(result, ", ");
  }

  public void addListener(Listener l) {
    myListeners.add(l);
  }

  public void fireTasksChanged() {
    for (Listener each : myListeners) {
      each.compileTasksChanged();
    }
  }

  public interface Listener {
    void compileTasksChanged();
  }
}