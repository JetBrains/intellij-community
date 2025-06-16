// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.build;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.scratch.JavaScratchConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.artifacts.ArtifactPropertiesProvider;
import com.intellij.task.*;
import com.intellij.task.impl.JpsProjectTaskRunner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;
import static org.jetbrains.idea.maven.utils.MavenUtil.isMavenModule;

public final class MavenProjectTaskRunner extends ProjectTaskRunner {
  @Override
  public Promise<Result> run(@NotNull Project project, @NotNull ProjectTaskContext context, ProjectTask @NotNull ... tasks) {
    AsyncPromise<Result> promise = new AsyncPromise<>();
    ProjectTaskNotification callback = new ProjectTaskNotificationAdapter(promise);
    Map<Class<? extends ProjectTask>, List<ProjectTask>> taskMap = JpsProjectTaskRunner.groupBy(Arrays.asList(tasks));

    buildModuleFiles(project, context, callback, getFromGroupedMap(taskMap, ModuleFilesBuildTask.class, emptyList()));
    buildModules(project, context, callback, getFromGroupedMap(taskMap, ModuleResourcesBuildTask.class, emptyList()));
    buildModules(project, context, callback, getFromGroupedMap(taskMap, ModuleBuildTask.class, emptyList()));

    buildArtifacts(project, context, callback, getFromGroupedMap(taskMap, ProjectModelBuildTask.class, emptyList()));
    return promise;
  }

  @Override
  public boolean canRun(@NotNull ProjectTask projectTask) {
    throw new UnsupportedOperationException("MavenProjectTaskRunner#canRun(ProjectTask)");
  }

  @Override
  public boolean canRun(@NotNull Project project, @NotNull ProjectTask projectTask, @Nullable ProjectTaskContext context) {
    if (context != null && context.getRunConfiguration() instanceof JavaScratchConfiguration) {
      return false;
    }
    return canRun(project, projectTask);
  }

  @Override
  public boolean canRun(@NotNull Project project, @NotNull ProjectTask projectTask) {
    if (!MavenRunner.getInstance(project).getSettings().isDelegateBuildToMaven()) {
      return false;
    }

    if (projectTask instanceof ModuleBuildTask) {
      Module module = ((ModuleBuildTask)projectTask).getModule();
      return isMavenModule(module);
    }

    if (projectTask instanceof ProjectModelBuildTask buildTask) {
      if (buildTask.getBuildableElement() instanceof Artifact artifact) {
        MavenArtifactProperties properties = null;
        for (ArtifactPropertiesProvider provider : artifact.getPropertiesProviders()) {
          if (provider instanceof MavenArtifactPropertiesProvider) {
            ArtifactProperties<?> artifactProperties = artifact.getProperties(provider);
            if (artifactProperties instanceof MavenArtifactProperties) {
              properties = (MavenArtifactProperties)artifactProperties;
              break;
            }
          }
        }
        if (properties == null || properties.getModuleName() == null) {
          return false;
        }

        Module module = ModuleManager.getInstance(project).findModuleByName(properties.getModuleName());
        if (!isMavenModule(module)) {
          return false;
        }

        for (MavenArtifactBuilder artifactBuilder : MavenArtifactBuilder.EP_NAME.getExtensions()) {
          if (artifactBuilder.isApplicable(buildTask)) {
            return true;
          }
        }
      }
    }

    if (projectTask instanceof ExecuteRunConfigurationTask task) {
      RunProfile runProfile = task.getRunProfile();
      if (runProfile instanceof JavaScratchConfiguration) {
        return false;
      }
      if (runProfile instanceof ModuleBasedConfiguration) {
        RunConfigurationModule module = ((ModuleBasedConfiguration<?, ?>)runProfile).getConfigurationModule();
        if (!isMavenModule(module.getModule())) {
          return false;
        }
        for (MavenExecutionEnvironmentProvider environmentProvider : MavenExecutionEnvironmentProvider.EP_NAME.getExtensions()) {
          if (environmentProvider.isApplicable(task)) {
            return true;
          }
        }
      }
    }

    return false;
  }

  @Override
  public @Nullable ExecutionEnvironment createExecutionEnvironment(@NotNull Project project,
                                                                   @NotNull ExecuteRunConfigurationTask task,
                                                                   @Nullable Executor executor) {
    for (MavenExecutionEnvironmentProvider environmentProvider : MavenExecutionEnvironmentProvider.EP_NAME.getExtensions()) {
      if (environmentProvider.isApplicable(task)) {
        return environmentProvider.createExecutionEnvironment(project, task, executor);
      }
    }
    return null;
  }

  private static <T extends ProjectTask> List<? extends T>
  getFromGroupedMap(Map<Class<? extends ProjectTask>, List<ProjectTask>> map, Class<T> key, List<? extends T> defaultValue) {
    List<?> result = map.get(key);
    if (result == null) return defaultValue;
    //noinspection unchecked
    return (List<? extends T>)result;
  }

  private static void buildModules(@NotNull Project project,
                                   @NotNull ProjectTaskContext context,
                                   @Nullable ProjectTaskNotification callback,
                                   @NotNull Collection<? extends ModuleBuildTask> moduleBuildTasks) {
    if (moduleBuildTasks.isEmpty()) return;

    MavenRunner mavenRunner = MavenRunner.getInstance(project);
    MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstance(project);

    MavenExplicitProfiles explicitProfiles = mavenProjectsManager.getExplicitProfiles();
    Map<MavenProject, List<MavenProject>> rootProjectsToModules = new HashMap<>();

    boolean buildOnlyResources = false;
    for (ModuleBuildTask moduleBuildTask : moduleBuildTasks) {
      MavenProject mavenProject = mavenProjectsManager.findProject(moduleBuildTask.getModule());
      if (mavenProject == null) continue;

      buildOnlyResources = buildOnlyResources || moduleBuildTask instanceof ModuleResourcesBuildTask;
      MavenProject rootProject = mavenProjectsManager.findRootProject(mavenProject);
      rootProjectsToModules.computeIfAbsent(rootProject, p -> new ArrayList<>()).add(mavenProject);
    }

    boolean clean = moduleBuildTasks.stream().anyMatch(task -> !(task instanceof ModuleFilesBuildTask) && !task.isIncrementalBuild());
    boolean compileOnly = moduleBuildTasks.stream().allMatch(task -> task instanceof ModuleFilesBuildTask);
    boolean includeDependentModules = moduleBuildTasks.stream().anyMatch(ModuleBuildTask::isIncludeDependentModules);
    String goal = getGoal(buildOnlyResources, compileOnly);
    List<MavenRunnerParameters> commands = new ArrayList<>();
    for (Map.Entry<MavenProject, List<MavenProject>> entry : rootProjectsToModules.entrySet()) {
      ParametersList parameters = new ParametersList();
      if (clean) {
        parameters.add("clean");
      }
      parameters.add(goal);

      List<MavenProject> mavenProjects = entry.getValue();
      if (!includeDependentModules) {
        if (mavenProjects.size() > 1) {
          parameters.add("--projects");
          parameters.add(mavenProjects.stream()
                           .map(MavenProject::getMavenId)
                           .map(mavenId -> mavenId.getGroupId() + ":" + mavenId.getArtifactId())
                           .collect(joining(",")));
        }
        else {
          parameters.add("--non-recursive");
        }
      }

      VirtualFile pomFile = (mavenProjects.size() > 1 ? entry.getKey() : mavenProjects.get(0)).getFile();
      commands.add(new MavenRunnerParameters(true,
                                             pomFile.getParent().getPath(),
                                             pomFile.getName(),
                                             parameters.getList(),
                                             explicitProfiles.getEnabledProfiles(),
                                             explicitProfiles.getDisabledProfiles()));
    }

    runBatch(project, mavenRunner, "Maven Build", commands, context, callback);
  }

  private static @NotNull String getGoal(boolean buildOnlyResources, boolean compileOnly) {
    if (buildOnlyResources) {
      return "resources:resources";
    }
    return compileOnly ? "compile" : "install";
  }

  public static void runBatch(@NotNull Project project,
                              @NotNull MavenRunner mavenRunner,
                              @NotNull String title,
                              @NotNull List<MavenRunnerParameters> commands,
                              @NotNull ProjectTaskContext context,
                              @Nullable ProjectTaskNotification callback) {

    ApplicationManager.getApplication().invokeAndWait(() -> {


      FileDocumentManager.getInstance().saveAllDocuments();
      for (MavenRunnerParameters command : commands) {
        MavenRunConfigurationType.runConfiguration(project, command, null, null, descriptor -> {
          if(callback == null){
            return;
          }
          ProcessHandler handler = descriptor.getProcessHandler();
          if (handler != null) {
            handler.addProcessListener(new ProcessListener() {
              @Override
              public void processTerminated(@NotNull ProcessEvent event) {
                if (event.getExitCode() == 0) {
                  callback.finished(new ProjectTaskResult(false, 0, 0));
                }
                else {
                  callback.finished(new ProjectTaskResult(true, 0, 0));
                }
              }
            });
          }
        }, true);
      }
    });
  }

  private static void buildModuleFiles(@NotNull Project project,
                                       @NotNull ProjectTaskContext context,
                                       @Nullable ProjectTaskNotification callback,
                                       @NotNull Collection<? extends ModuleFilesBuildTask> moduleFilesBuildTasks) {
    buildModules(project, context, callback, moduleFilesBuildTasks);
  }

  private static void buildArtifacts(@NotNull Project project,
                                     @NotNull ProjectTaskContext context,
                                     @Nullable ProjectTaskNotification callback,
                                     List<? extends ProjectModelBuildTask> tasks) {
    for (ProjectModelBuildTask buildTask : tasks) {
      if (buildTask.getBuildableElement() instanceof Artifact) {
        for (MavenArtifactBuilder artifactBuilder : MavenArtifactBuilder.EP_NAME.getExtensions()) {
          if (artifactBuilder.isApplicable(buildTask)) {
            artifactBuilder.build(project, buildTask, context, callback);
          }
        }
      }
    }
  }

  private static final class ProjectTaskNotificationAdapter implements ProjectTaskNotification {
    private final @NotNull AsyncPromise<? super Result> myPromise;

    private ProjectTaskNotificationAdapter(@NotNull AsyncPromise<? super Result> promise) {
      myPromise = promise;
    }

    @Override
    public void finished(@SuppressWarnings("deprecation") @NotNull ProjectTaskResult taskResult) {
      myPromise.setResult(new Result() {
        @Override
        public boolean isAborted() {
          return taskResult.isAborted();
        }

        @Override
        public boolean hasErrors() {
          return taskResult.getErrors() > 0;
        }
      });
    }
  }
}
