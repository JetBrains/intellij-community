// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.tasks;

import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.Executor;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.DefaultJavaProgramRunner;
import com.intellij.execution.impl.RunConfigurationBeforeRunProvider;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.execution.ParametersListUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenEditGoalDialog;
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenLog;

import javax.swing.*;
import java.util.List;

import static com.intellij.openapi.util.Pair.pair;
import static icons.OpenapiIcons.RepositoryLibraryLogo;

public final class MavenBeforeRunTasksProvider extends BeforeRunTaskProvider<MavenBeforeRunTask> {
  public static final Key<MavenBeforeRunTask> ID = Key.create("Maven.BeforeRunTask");
  private final Project myProject;

  public MavenBeforeRunTasksProvider(Project project) {
    myProject = project;
  }

  @Override
  public Key<MavenBeforeRunTask> getId() {
    return ID;
  }

  @Override
  public String getName() {
    return TasksBundle.message("maven.tasks.before.run.empty");
  }

  @Override
  public Icon getIcon() {
    return RepositoryLibraryLogo;
  }

  @Override
  public @Nullable Icon getTaskIcon(MavenBeforeRunTask task) {
    return RepositoryLibraryLogo;
  }

  @Override
  public String getDescription(MavenBeforeRunTask task) {
    MavenProject mavenProject = getMavenProject(task);
    if (mavenProject == null) {
      return TasksBundle.message("maven.tasks.before.run.empty");
    }

    String desc = mavenProject.getDisplayName() + ": " + StringUtil.notNullize(task.getGoal()).trim();
    return TasksBundle.message("maven.tasks.before.run", desc);
  }

  private @Nullable MavenProject getMavenProject(MavenBeforeRunTask task) {
    String pomXmlPath = task.getProjectPath();
    if (StringUtil.isEmpty(pomXmlPath)) return null;

    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(pomXmlPath);
    if (file == null) return null;

    return MavenProjectsManager.getInstance(myProject).findProject(file);
  }

  @Override
  public boolean isConfigurable() {
    return true;
  }

  @Override
  public MavenBeforeRunTask createTask(@NotNull RunConfiguration runConfiguration) {
    return new MavenBeforeRunTask();
  }

  @Override
  public boolean configureTask(@NotNull RunConfiguration runConfiguration, @NotNull MavenBeforeRunTask task) {
    MavenEditGoalDialog dialog = new MavenEditGoalDialog(myProject);

    dialog.setTitle(TasksBundle.message("maven.tasks.select.goal.title"));

    if (task.getGoal() == null) {
      // just created empty task.
      MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(myProject);
      List<MavenProject> rootProjects = projectsManager.getRootProjects();
      if (!rootProjects.isEmpty()) {
        dialog.setSelectedMavenProject(rootProjects.get(0));
      }
      else {
        dialog.setSelectedMavenProject(null);
      }
    }
    else {
      String goals = splitToGoalsAndPomFileName(task.getGoal()).first;

      MavenProject mavenProject = getMavenProject(task);
      if (mavenProject != null) {
        String pomFileName = mavenProject.getFile().getName();
        if (FileUtil.namesEqual(pomFileName, MavenConstants.POM_XML)) {
          dialog.setGoals(goals);
        }
        else {
          dialog.setGoals(goals + " -f " + ParametersListUtil.join(pomFileName));
        }
        dialog.setSelectedMavenProject(mavenProject);
      }
      else {
        dialog.setGoals(goals);
        dialog.setSelectedMavenProject(null);
      }
    }

    if (!dialog.showAndGet()) {
      return false;
    }

    Pair<String, String> goalsAndPomFileName = splitToGoalsAndPomFileName(dialog.getGoals());
    task.setProjectPath(dialog.getWorkDirectory() + "/" + goalsAndPomFileName.second);
    task.setGoal(goalsAndPomFileName.first);
    return true;
  }

  @Override
  public boolean canExecuteTask(@NotNull RunConfiguration configuration, @NotNull MavenBeforeRunTask task) {
    return task.getGoal() != null && task.getProjectPath() != null;
  }

  @Override
  public boolean executeTask(final @NotNull DataContext context,
                             @NotNull RunConfiguration configuration,
                             @NotNull ExecutionEnvironment env,
                             final @NotNull MavenBeforeRunTask task) {
    ApplicationManager.getApplication().invokeAndWait(() -> FileDocumentManager.getInstance().saveAllDocuments());

    final Project project = CommonDataKeys.PROJECT.getData(context);

    if (ReadAction.compute(() -> project == null || project.isDisposed())) return false;

    return doRunMavenTask(project, task, env);
  }

  private boolean doRunMavenTask(Project project, MavenBeforeRunTask task, ExecutionEnvironment env) {
    final MavenProject mavenProject = getMavenProject(task);
    if (mavenProject == null) return false;
    final MavenExplicitProfiles explicitProfiles = MavenProjectsManager.getInstance(project).getExplicitProfiles();

    MavenRunnerParameters params = new MavenRunnerParameters(
      true,
      mavenProject.getDirectory(),
      mavenProject.getFile().getName(),
      ParametersListUtil.parse(task.getGoal()),
      explicitProfiles.getEnabledProfiles(),
      explicitProfiles.getDisabledProfiles());

    RunnerAndConfigurationSettings configuration =
      MavenRunConfigurationType.createRunnerAndConfigurationSettings(null, null, params, myProject);
    ProgramRunner runner = DefaultJavaProgramRunner.getInstance();
    Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    ExecutionEnvironment environment = new ExecutionEnvironment(executor, runner, configuration, project);
    environment.setExecutionId(env.getExecutionId());

    if (!environment.getRunner().canRun(executor.getId(), environment.getRunProfile())) {
      MavenLog.LOG.warn("Can't run " + task.getGoal() + " on runner=" + runner.getRunnerId() + ", executorId=" + executor.getId());
      return false;
    }

    return RunConfigurationBeforeRunProvider.doRunTask(executor.getId(), environment, runner);
  }

  private static @NotNull Pair<String, String> splitToGoalsAndPomFileName(@Nullable String goals) {
    if (goals == null) {
      return pair(null, MavenConstants.POM_XML);
    }
    List<String> commandLine = ParametersListUtil.parse(goals);
    int pomFileNameIndex = 1 + commandLine.indexOf("-f");
    if (pomFileNameIndex != 0 && pomFileNameIndex < commandLine.size()) {
      String pomFileName = commandLine.remove(pomFileNameIndex);
      commandLine.remove(pomFileNameIndex - 1);
      return pair(ParametersListUtil.join(commandLine), pomFileName);
    }
    return pair(goals, MavenConstants.POM_XML);
  }
}
