// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution.build;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.artifacts.ArtifactPropertiesProvider;
import com.intellij.task.*;
import com.intellij.task.impl.JpsProjectTaskRunner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.project.MavenConsole;
import org.jetbrains.idea.maven.project.MavenConsoleImpl;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.tasks.TasksBundle;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;
import static org.jetbrains.idea.maven.utils.MavenUtil.isMavenModule;

/**
 * @author ibessonov
 */
public class MavenProjectTaskRunner extends ProjectTaskRunner {

  private static final Pattern ERRORS_NUMBER_PATTERN = Pattern.compile("^\\[INFO] (\\d+) errors\\s*$");
  private static final Pattern WARNING_PATTERN = Pattern.compile("^\\[WARNING]");

  @Override
  public void run(@NotNull Project project,
                  @NotNull ProjectTaskContext context,
                  @Nullable ProjectTaskNotification callback,
                  @NotNull Collection<? extends ProjectTask> tasks) {
    Map<Class<? extends ProjectTask>, List<ProjectTask>> taskMap = JpsProjectTaskRunner.groupBy(tasks);

    buildModuleFiles(project, callback, getFromGroupedMap(taskMap, ModuleFilesBuildTask.class, emptyList()));
    buildModules(project, callback, getFromGroupedMap(taskMap, ModuleResourcesBuildTask.class, emptyList()));
    buildModules(project, callback, getFromGroupedMap(taskMap, ModuleBuildTask.class, emptyList()));

    buildArtifacts(project, callback, getFromGroupedMap(taskMap, ProjectModelBuildTask.class, emptyList()));
  }

  @Override
  public boolean canRun(@NotNull ProjectTask projectTask) {
    throw new UnsupportedOperationException("MavenProjectTaskRunner#canRun(ProjectTask)");
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

    if (projectTask instanceof ProjectModelBuildTask) {
      ProjectModelBuildTask buildTask = (ProjectModelBuildTask)projectTask;
      if (buildTask.getBuildableElement() instanceof Artifact) {
        Artifact artifact = (Artifact)buildTask.getBuildableElement();
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

    if (projectTask instanceof ExecuteRunConfigurationTask) {
      ExecuteRunConfigurationTask task = (ExecuteRunConfigurationTask)projectTask;
      RunProfile runProfile = task.getRunProfile();
      if (runProfile instanceof ModuleBasedConfiguration) {
        RunConfigurationModule module = ((ModuleBasedConfiguration)runProfile).getConfigurationModule();
        if (!isMavenModule(module.getModule())) {
          return false;
        }
        for (MavenExecutionEnvironmentProvider environmentProvider: MavenExecutionEnvironmentProvider.EP_NAME.getExtensions()) {
          if (environmentProvider.isApplicable(task)) {
            return true;
          }
        }
      }
    }

    return false;
  }

  @Nullable
  @Override
  public ExecutionEnvironment createExecutionEnvironment(@NotNull Project project,
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
    boolean includeDependentModules = moduleBuildTasks.stream().anyMatch(ModuleBuildTask::isIncludeDependentModules);
    String goal = buildOnlyResources ? "resources:resources" : "install";
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

    runBatch(project, mavenRunner, "Maven Build", commands, callback);
  }

  public static void runBatch(@NotNull Project project, @NotNull MavenRunner mavenRunner, @NotNull String title,
                              @NotNull List<MavenRunnerParameters> commands, @Nullable ProjectTaskNotification callback) {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      AtomicInteger errors = new AtomicInteger();
      AtomicInteger warnings = new AtomicInteger();
      MavenConsole console = new MavenConsoleImpl(title, project) {

        @Override
        public void attachToProcess(ProcessHandler processHandler) {
          super.attachToProcess(processHandler);
          processHandler.addProcessListener(new ProcessAdapter() {
            @Override
            public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
              String line = event.getText();

              Matcher errorsMatcher = ERRORS_NUMBER_PATTERN.matcher(line);
              if (errorsMatcher.matches()) {
                try {
                  errors.addAndGet(Integer.parseInt(errorsMatcher.group(1)));
                }
                catch (NumberFormatException ignore) {
                }
              }

              Matcher warningMatcher = WARNING_PATTERN.matcher(line);
              if (warningMatcher.find()) {
                warnings.incrementAndGet();
              }
            }
          });
        }
      };
      FileDocumentManager.getInstance().saveAllDocuments();

      new Task.Backgroundable(project, TasksBundle.message("maven.tasks.executing"), true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          mavenRunner.runBatch(commands, null, null, TasksBundle.message("maven.tasks.executing"), indicator, console);
        }

        @Override
        public boolean shouldStartInBackground() {
          return mavenRunner.getSettings().isRunMavenInBackground();
        }

        @Override
        public void processSentToBackground() {
          mavenRunner.getSettings().setRunMavenInBackground(true);
        }

        @Override
        public void onCancel() {
          if (callback != null) {
            callback.finished(new ProjectTaskResult(true, errors.get(), warnings.get()));
          }
        }

        @Override
        public void onSuccess() {
          if (callback != null) {
            callback.finished(new ProjectTaskResult(false, errors.get(), warnings.get()));
          }
        }

        @Override
        public void onThrowable(@NotNull Throwable error) {
          if (callback != null) {
            callback.finished(new ProjectTaskResult(false, errors.get(), warnings.get()));
          }
        }
      }.queue();
    });
  }

  private static void buildModuleFiles(@NotNull Project project,
                                       @Nullable ProjectTaskNotification callback,
                                       @NotNull Collection<? extends ModuleFilesBuildTask> moduleFilesBuildTasks) {
    buildModules(project, callback, moduleFilesBuildTasks);
  }

  private static void buildArtifacts(Project project, ProjectTaskNotification callback, List<? extends ProjectModelBuildTask> tasks) {
    for (ProjectModelBuildTask buildTask : tasks) {
      if (buildTask.getBuildableElement() instanceof Artifact) {
        for (MavenArtifactBuilder artifactBuilder : MavenArtifactBuilder.EP_NAME.getExtensions()) {
          if (artifactBuilder.isApplicable(buildTask)) {
            artifactBuilder.build(project, buildTask, callback);
          }
        }
      }
    }
  }
}
