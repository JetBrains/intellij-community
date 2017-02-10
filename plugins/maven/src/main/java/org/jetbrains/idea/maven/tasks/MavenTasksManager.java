/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.google.common.collect.Sets;
import com.intellij.execution.RunManagerEx;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenSimpleProjectComponent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@State(name = "MavenCompilerTasksManager")
public class MavenTasksManager extends MavenSimpleProjectComponent implements PersistentStateComponent<MavenTasksManagerState> {
  private final AtomicBoolean isInitialized = new AtomicBoolean();

  private MavenTasksManagerState myState = new MavenTasksManagerState();

  private final MavenProjectsManager myProjectsManager;
  private final MavenRunner myRunner;

  private final List<Listener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public enum Phase {
    BEFORE_COMPILE("maven.tasks.goal.before.compile"),
    AFTER_COMPILE("maven.tasks.goal.after.compile"),
    BEFORE_REBUILD("maven.tasks.goal.before.rebuild"),
    AFTER_REBUILD("maven.tasks.goal.after.rebuild");

    public final String myMessageKey;

    Phase(String messageKey) {
      myMessageKey = messageKey;
    }
  }

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
    result.afterCompileTasks = new THashSet<>(myState.afterCompileTasks);
    result.beforeCompileTasks = new THashSet<>(myState.beforeCompileTasks);
    result.afterRebuildTask = new THashSet<>(myState.afterRebuildTask);
    result.beforeRebuildTask = new THashSet<>(myState.beforeRebuildTask);
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

    class MyCompileTask implements CompileTask {

      private final boolean myBefore;

      MyCompileTask(boolean before) {
        myBefore = before;
      }

      @Override
      public boolean execute(CompileContext context) {
        return doExecute(myBefore, context);
      }
    }

    compilerManager.addBeforeTask(new MyCompileTask(true));
    compilerManager.addAfterTask(new MyCompileTask(false));
  }

  private boolean doExecute(boolean before, CompileContext context) {
    List<MavenRunnerParameters> parametersList;
    synchronized (this) {
      parametersList = new ArrayList<>();
      Set<MavenCompilerTask> tasks = before ? myState.beforeCompileTasks : myState.afterCompileTasks;

      if (context.isRebuild()) {
        tasks = Sets.union(before ? myState.beforeRebuildTask : myState.afterRebuildTask, tasks);
      }

      for (MavenCompilerTask each : tasks) {
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(each.getProjectPath());
        if (file == null) continue;
        MavenExplicitProfiles explicitProfiles = myProjectsManager.getExplicitProfiles();
        parametersList.add(new MavenRunnerParameters(true,
                                                     file.getParent().getPath(),
                                                     Arrays.asList(each.getGoal()),
                                                     explicitProfiles.getEnabledProfiles(),
                                                     explicitProfiles.getDisabledProfiles()));
      }
    }
    return myRunner.runBatch(parametersList, null, null, TasksBundle.message("maven.tasks.executing"), context.getProgressIndicator());
  }

  public synchronized boolean isCompileTaskOfPhase(@NotNull MavenCompilerTask task, @NotNull Phase phase) {
    return myState.getTasks(phase).contains(task);
  }

  public void addCompileTasks(List<MavenCompilerTask> tasks, @NotNull Phase phase) {
    synchronized (this) {
      myState.getTasks(phase).addAll(tasks);
    }
    fireTasksChanged();
  }

  public void removeCompileTasks(List<MavenCompilerTask> tasks, @NotNull Phase phase) {
    synchronized (this) {
      myState.getTasks(phase).removeAll(tasks);
    }
    fireTasksChanged();
  }

  public String getDescription(MavenProject project, String goal) {
    List<String> result = new ArrayList<>();
    MavenCompilerTask compilerTask = new MavenCompilerTask(project.getPath(), goal);
    synchronized (this) {
      for (Phase phase : Phase.values()) {
        if (myState.getTasks(phase).contains(compilerTask)) {
          result.add(TasksBundle.message(phase.myMessageKey));
        }
      }
    }
    RunManagerEx runManager = RunManagerEx.getInstanceEx(myProject);
    for (MavenBeforeRunTask each : runManager.getBeforeRunTasks(MavenBeforeRunTasksProvider.ID)) {
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
