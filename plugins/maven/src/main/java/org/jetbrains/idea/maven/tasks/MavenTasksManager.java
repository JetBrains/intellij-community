// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.tasks;

import com.google.common.collect.Sets;
import com.intellij.execution.Executor;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.DefaultJavaProgramRunner;
import com.intellij.execution.impl.RunConfigurationBeforeRunProvider;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectBundle;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenSimpleProjectComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@State(name = "MavenCompilerTasksManager")
public final class MavenTasksManager extends MavenSimpleProjectComponent implements PersistentStateComponent<MavenTasksManagerState> {
  private final AtomicBoolean isInitialized = new AtomicBoolean();

  private MavenTasksManagerState myState = new MavenTasksManagerState();

  private final MavenProjectsManager myProjectsManager;

  private final List<Listener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public enum Phase {
    BEFORE_COMPILE("maven.tasks.goal.before.compile"),
    AFTER_COMPILE("maven.tasks.goal.after.compile"),
    BEFORE_REBUILD("maven.tasks.goal.before.rebuild"),
    AFTER_REBUILD("maven.tasks.goal.after.rebuild");
    @PropertyKey(resourceBundle = TasksBundle.BUNDLE)
    public final String myMessageKey;

    Phase(@PropertyKey(resourceBundle = TasksBundle.BUNDLE) String messageKey) {
      myMessageKey = messageKey;
    }
  }

  public static MavenTasksManager getInstance(@NotNull Project project) {
    return project.getService(MavenTasksManager.class);
  }

  public MavenTasksManager(@NotNull Project project) {
    super(project);

    myProjectsManager = MavenProjectsManager.getInstance(project);
  }

  @Override
  public synchronized MavenTasksManagerState getState() {
    MavenTasksManagerState result = new MavenTasksManagerState();
    result.afterCompileTasks = new THashSet<>(myState.afterCompileTasks);
    result.beforeCompileTasks = new THashSet<>(myState.beforeCompileTasks);
    result.afterRebuildTask = new THashSet<>(myState.afterRebuildTask);
    result.beforeRebuildTask = new THashSet<>(myState.beforeRebuildTask);
    return result;
  }

  @Override
  public void loadState(@NotNull MavenTasksManagerState state) {
    synchronized (this) {
      myState = state;
    }
    if (isInitialized.get()) {
      fireTasksChanged();
    }
  }

  @Override
  public void initializeComponent() {
    if (!isNormalProject()) return;
    if (isInitialized.getAndSet(true)) return;

    class MyCompileTask implements CompileTask {

      private final boolean myBefore;

      MyCompileTask(boolean before) {
        myBefore = before;
      }

      @Override
      public boolean execute(@NotNull CompileContext context) {
        return doExecute(myBefore, context);
      }
    }

    CompilerManager compilerManager = CompilerManager.getInstance(myProject);
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
                                                     file.getName(),
                                                     Collections.singletonList(each.getGoal()),
                                                     explicitProfiles.getEnabledProfiles(),
                                                     explicitProfiles.getDisabledProfiles()));
      }
    }

    return doRunTask(context, parametersList);
  }

  private boolean doRunTask(CompileContext context, List<MavenRunnerParameters> parametersList) {
    try {
      ProgramRunner runner = DefaultJavaProgramRunner.getInstance();
      Executor executor = DefaultRunExecutor.getRunExecutorInstance();

      long executionId = ExecutionEnvironment.getNextUnusedExecutionId();
      int count = 0;
      for (MavenRunnerParameters params : parametersList) {
        RunnerAndConfigurationSettings configuration =
          MavenRunConfigurationType.createRunnerAndConfigurationSettings(null, null, params, context.getProject());
        if(parametersList.size() > 1) {
          configuration
            .setName(MavenProjectBundle.message("maven.before.build.of.count", ++count, parametersList.size(), configuration.getName()));
        }
        ExecutionEnvironment environment = new ExecutionEnvironment(executor, runner, configuration, context.getProject());
        environment.setExecutionId(executionId);
        boolean result = RunConfigurationBeforeRunProvider.doRunTask(executor.getId(), environment, runner);
        if (!result) return false;
      }

      return true;
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      MavenLog.LOG.error("Cannot execute:", e);
      return false;
    }
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
    for (MavenBeforeRunTask each : RunManagerEx.getInstanceEx(myProject).getBeforeRunTasks(MavenBeforeRunTasksProvider.ID)) {
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
