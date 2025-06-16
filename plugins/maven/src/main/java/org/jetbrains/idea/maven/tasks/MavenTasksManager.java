// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.tasks;

import com.intellij.execution.Executor;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.DefaultJavaProgramRunner;
import com.intellij.execution.impl.RunConfigurationBeforeRunProvider;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.DisposableWrapperList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectBundle;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenSimpleProjectComponent;

import java.util.*;

import static java.util.stream.Collectors.groupingBy;

@Service(Service.Level.PROJECT)
@State(name = "MavenCompilerTasksManager")
public final class MavenTasksManager extends MavenSimpleProjectComponent implements PersistentStateComponent<MavenTasksManagerState>,
                                                                                    Disposable {
  private volatile MavenTasksManagerState myState = new MavenTasksManagerState();
  private final Object myStateLock = new Object();
  private final DisposableWrapperList<Listener> myListeners = new DisposableWrapperList<>();

  public enum Phase {
    BEFORE_COMPILE("maven.tasks.goal.before.compile"),
    AFTER_COMPILE("maven.tasks.goal.after.compile"),
    BEFORE_REBUILD("maven.tasks.goal.before.rebuild"),
    AFTER_REBUILD("maven.tasks.goal.after.rebuild");
    public final @PropertyKey(resourceBundle = TasksBundle.BUNDLE) String myMessageKey;

    Phase(@PropertyKey(resourceBundle = TasksBundle.BUNDLE) String messageKey) {
      myMessageKey = messageKey;
    }
  }

  public static MavenTasksManager getInstance(@NotNull Project project) {
    return project.getService(MavenTasksManager.class);
  }

  public MavenTasksManager(@NotNull Project project) {
    super(project);
  }

  @Override
  public MavenTasksManagerState getState() {
    synchronized (myStateLock) {
      MavenTasksManagerState result = new MavenTasksManagerState();
      result.afterCompileTasks = new HashSet<>(myState.afterCompileTasks);
      result.beforeCompileTasks = new HashSet<>(myState.beforeCompileTasks);
      result.afterRebuildTask = new HashSet<>(myState.afterRebuildTask);
      result.beforeRebuildTask = new HashSet<>(myState.beforeRebuildTask);
      return result;
    }
  }

  @Override
  public void loadState(@NotNull MavenTasksManagerState state) {
    synchronized (myStateLock) {
      myState = state;
    }
    fireTasksChanged();
  }

  private static class MyCompileTask implements CompileTask {
    private final boolean myBefore;

    MyCompileTask(boolean before) {
      myBefore = before;
    }

    @Override
    public boolean execute(@NotNull CompileContext context) {
      MavenTasksManager mavenTasksManager = getInstance(context.getProject());
      return mavenTasksManager.doExecute(myBefore, context);
    }
  }

  @ApiStatus.Internal
  static final class MavenBeforeCompileTask extends MyCompileTask {
    MavenBeforeCompileTask() {
      super(true);
    }
  }

  @ApiStatus.Internal
  static final class MavenAfterCompileTask extends MyCompileTask {
    MavenAfterCompileTask() {
      super(false);
    }
  }

  @Override
  public void dispose() {
    myListeners.clear();
  }

  private boolean doExecute(boolean before, CompileContext context) {
    List<MavenRunnerParameters> parametersList;
    synchronized (myStateLock) {
      var tasks = before ? myState.beforeCompileTasks : myState.afterCompileTasks;

      if (context.isRebuild()) {
        tasks = ContainerUtil.union(before ? myState.beforeRebuildTask : myState.afterRebuildTask, tasks);
      }

      var projectsManager = MavenProjectsManager.getInstance(myProject);
      var affectedModules = ReadAction.compute(() -> Set.of(context.getCompileScope().getAffectedModules()));
      var taskInfos = new ArrayList<MavenTaskInfo>();

      for (var task : tasks) {
        var file = LocalFileSystem.getInstance().findFileByPath(task.getProjectPath());
        if (file == null) continue;
        var mavenProject = projectsManager.findProject(file);
        if (null == mavenProject) continue;
        var module = ReadAction.compute(() -> projectsManager.findModule(mavenProject));
        if (null == module) continue;
        if (!affectedModules.contains(module)) continue;
        var rootProject = projectsManager.findRootProject(mavenProject);
        if (null == rootProject) {
          rootProject = mavenProject;
        }
        taskInfos.add(new MavenTaskInfo(task.getGoal(), file, mavenProject, rootProject));
      }

      var explicitProfiles = projectsManager.getExplicitProfiles();
      parametersList = taskInfosToParametersList(taskInfos, explicitProfiles);
    }

    if (parametersList.isEmpty()) return true;

    return doRunTask(context, parametersList);
  }

  private record MavenTaskInfo(@NotNull String goal,
                               @NotNull VirtualFile file,
                               MavenProject mavenProject,
                               MavenProject rootProject) {
    public Pair<String, MavenProject> goalAndRootProject() {
      return new Pair<>(goal(), rootProject());
    }
  }

  // if there are several tasks with the same goal and rootProject project, group them into one
  private static List<MavenRunnerParameters> taskInfosToParametersList(@NotNull List<MavenTaskInfo> taskInfos,
                                                                       MavenExplicitProfiles explicitProfiles) {
    if (taskInfos.isEmpty()) return List.of();

    var enabledProfiles = explicitProfiles.getEnabledProfiles();
    var disabledProfiles = explicitProfiles.getDisabledProfiles();
    List<MavenRunnerParameters> parametersList = new ArrayList<>();

    Collection<List<MavenTaskInfo>> taskGroups = taskInfos.stream().collect(groupingBy(MavenTaskInfo::goalAndRootProject)).values();
    for (var tasksInGroup : taskGroups) {
      MavenRunnerParameters params;
      var firstTask = tasksInGroup.get(0);
      params = new MavenRunnerParameters(true,
                                         firstTask.file().getParent().getPath(),
                                         firstTask.file().getName(),
                                         Collections.singletonList(firstTask.goal()),
                                         enabledProfiles,
                                         disabledProfiles);
      if (tasksInGroup.size() > 1) {
        var workingDirPath = firstTask.rootProject().getDirectory();
        params.setWorkingDirPath(workingDirPath);
        var projectsCmdOptionValues = tasksInGroup.stream()
          .map(task -> task.mavenProject().getMavenId())
          .map(mavenId -> mavenId.getGroupId() + ":" + mavenId.getArtifactId())
          .toList();
        params.setProjectsCmdOptionValues(projectsCmdOptionValues);
      }
      parametersList.add(params);
    }

    return parametersList;
  }

  private static boolean doRunTask(CompileContext context, List<MavenRunnerParameters> parametersList) {
    try {
      var runner = DefaultJavaProgramRunner.getInstance();
      Executor executor = DefaultRunExecutor.getRunExecutorInstance();

      long executionId = ExecutionEnvironment.getNextUnusedExecutionId();
      int count = 0;
      for (MavenRunnerParameters params : parametersList) {
        RunnerAndConfigurationSettings configuration =
          MavenRunConfigurationType.createRunnerAndConfigurationSettings(null, null, params, context.getProject());
        if (parametersList.size() > 1) {
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


  @TestOnly
  public @NotNull Set<MavenCompilerTask> getTasks(@NotNull Phase phase) {
    synchronized (myStateLock){
      return myState.getTasks(phase);
    }
  }

  public boolean isCompileTaskOfPhase(@NotNull MavenCompilerTask task, @NotNull Phase phase) {
    synchronized (myStateLock) {
      return myState.getTasks(phase).contains(task);
    }
  }

  public void addCompileTasks(List<MavenCompilerTask> tasks, @NotNull Phase phase) {
    synchronized (myStateLock) {
      myState.getTasks(phase).addAll(tasks);
    }
    fireTasksChanged();
  }

  public void removeCompileTasks(List<MavenCompilerTask> tasks, @NotNull Phase phase) {
    synchronized (myStateLock) {
      myState.getTasks(phase).removeAll(tasks);
    }
    fireTasksChanged();
  }

  public String getDescription(MavenProject project, String goal) {
    List<String> result = new ArrayList<>();
    MavenCompilerTask compilerTask = new MavenCompilerTask(project.getPath(), goal);
    synchronized (myStateLock) {
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

  public void addListener(@NotNull Listener l, @NotNull Disposable disposable) {
    myListeners.add(l, disposable);
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
