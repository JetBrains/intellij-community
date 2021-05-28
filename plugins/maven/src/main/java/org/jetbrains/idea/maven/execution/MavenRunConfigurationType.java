// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.compiler.options.CompileStepBeforeRunNoErrorCheck;
import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.DefaultJavaProgramRunner;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.build.DelegateBuildRunner;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent;
import org.jetbrains.idea.maven.utils.MavenUtil;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

import static icons.OpenapiIcons.RepositoryLibraryLogo;

/**
 * @author Vladislav.Kaznacheev
 */
public final class MavenRunConfigurationType implements ConfigurationType {
  private static final Key<Boolean> IS_DELEGATE_BUILD = new Key<>("IS_DELEGATE_BUILD");
  private final ConfigurationFactory myFactory;
  private static final int MAX_NAME_LENGTH = 40;

  public static MavenRunConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(MavenRunConfigurationType.class);
  }

  /**
   * reflection
   */
  MavenRunConfigurationType() {
    myFactory = new MavenRunConfigurationFactory(this);
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return RunnerBundle.message("maven.run.configuration.name");
  }

  @Override
  public String getConfigurationTypeDescription() {
    return RunnerBundle.message("maven.run.configuration.description");
  }

  @Override
  public Icon getIcon() {
    return RepositoryLibraryLogo;
  }

  @Override
  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myFactory};
  }

  @Override
  public String getHelpTopic() {
    return "reference.dialogs.rundebug.MavenRunConfiguration";
  }

  @Override
  @NonNls
  @NotNull
  public String getId() {
    return "MavenRunConfiguration";
  }

  public static @NlsSafe String generateName(Project project, MavenRunnerParameters runnerParameters) {
    StringBuilder stringBuilder = new StringBuilder();

    final String name = getMavenProjectName(project, runnerParameters);
    if (!StringUtil.isEmptyOrSpaces(name)) {
      stringBuilder.append(name);
    }

    List<String> goals = runnerParameters.getGoals();
    if (!goals.isEmpty()) {
      stringBuilder.append(" [");
      listGoals(stringBuilder, goals);
      stringBuilder.append("]");
    }
    return stringBuilder.toString();
  }

  private static void listGoals(final StringBuilder stringBuilder, final List<String> goals) {
    int index = 0;
    for (String goal : goals) {
      if (index != 0) {
        if (stringBuilder.length() + goal.length() < MAX_NAME_LENGTH) {
          stringBuilder.append(",");
        }
        else {
          stringBuilder.append("...");
          break;
        }
      }
      stringBuilder.append(goal);
      index++;
    }
  }

  @Nullable
  private static String getMavenProjectName(final Project project, final MavenRunnerParameters runnerParameters) {
    final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(runnerParameters.getWorkingDirPath() + "/pom.xml");
    if (virtualFile != null) {
      MavenProject mavenProject = MavenProjectsManager.getInstance(project).findProject(virtualFile);
      if (mavenProject != null) {
        if (!StringUtil.isEmptyOrSpaces(mavenProject.getMavenId().getArtifactId())) {
          return mavenProject.getMavenId().getArtifactId();
        }
      }
    }
    return null;
  }

  public static boolean isDelegate(ExecutionEnvironment environment) {
    Boolean res = IS_DELEGATE_BUILD.get(environment);
    return res != null && res;
  }


  public static void runConfiguration(Project project,
                                      MavenRunnerParameters params,
                                      @Nullable ProgramRunner.Callback callback) {
    runConfiguration(project, params, null, null, callback);
  }


  public static void runConfiguration(Project project,
                                      @NotNull MavenRunnerParameters params,
                                      @Nullable MavenGeneralSettings settings,
                                      @Nullable MavenRunnerSettings runnerSettings,
                                      @Nullable ProgramRunner.Callback callback) {
    runConfiguration(project, params, settings, runnerSettings, callback, false);
  }

  public static void runConfiguration(Project project,
                                      @NotNull MavenRunnerParameters params,
                                      @Nullable MavenGeneralSettings settings,
                                      @Nullable MavenRunnerSettings runnerSettings,
                                      @Nullable ProgramRunner.Callback callback,
                                      boolean isDelegateBuild) {

    if (!ExternalSystemUtil.confirmLoadingUntrustedProject(project, MavenUtil.SYSTEM_ID)) {
      MavenUtil.showError(project,
                          RunnerBundle.message("notification.title.failed.to.execute.maven.goal"),
                          RunnerBundle.message("notification.project.is.untrusted"));
      return;
    }


    RunnerAndConfigurationSettings configSettings = createRunnerAndConfigurationSettings(settings,
                                                                                         runnerSettings,
                                                                                         params,
                                                                                         project,
                                                                                         generateName(project, params),
                                                                                         isDelegateBuild);

    ProgramRunner runner = isDelegateBuild ? DelegateBuildRunner.getDelegateRunner() : DefaultJavaProgramRunner.getInstance();
    Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    ExecutionEnvironment environment = new ExecutionEnvironment(executor, runner, configSettings, project);
    environment.putUserData(IS_DELEGATE_BUILD, isDelegateBuild);
    environment.setCallback(callback);
    try {
      runner.execute(environment);
    }
    catch (ExecutionException e) {
      MavenUtil.showError(project, RunnerBundle.message("notification.title.failed.to.execute.maven.goal"), e);
    }
  }

  @NotNull

  public static RunnerAndConfigurationSettings createRunnerAndConfigurationSettings(@Nullable MavenGeneralSettings generalSettings,
                                                                                    @Nullable MavenRunnerSettings runnerSettings,
                                                                                    @NotNull MavenRunnerParameters params,
                                                                                    @NotNull Project project) {
    return createRunnerAndConfigurationSettings(generalSettings, runnerSettings, params, project, generateName(project, params), false);
  }


  @NotNull
  public static RunnerAndConfigurationSettings createRunnerAndConfigurationSettings(@Nullable MavenGeneralSettings generalSettings,
                                                                                    @Nullable MavenRunnerSettings runnerSettings,
                                                                                    @NotNull MavenRunnerParameters params,
                                                                                    @NotNull Project project,
                                                                                    @NotNull String name,
                                                                                    boolean isDelegate) {
    MavenRunConfigurationType type = ConfigurationTypeUtil.findConfigurationType(MavenRunConfigurationType.class);

    RunnerAndConfigurationSettings settings = RunManager.getInstance(project).createConfiguration(name, type.myFactory);
    MavenRunConfiguration runConfiguration = (MavenRunConfiguration)settings.getConfiguration();
    if (isDelegate) {
      runConfiguration.setBeforeRunTasks(Collections.emptyList());
    }
    MavenGeneralSettings generalSettingsToRun =
      generalSettings != null ? generalSettings : MavenWorkspaceSettingsComponent.getInstance(project).getSettings().generalSettings;
    runConfiguration.setRunnerParameters(params);
    runConfiguration.setGeneralSettings(generalSettingsToRun);
    MavenRunnerSettings runnerSettingsToRun =
      runnerSettings != null ? runnerSettings : MavenRunner.getInstance(project).getState();
    runConfiguration.setRunnerSettings(runnerSettingsToRun);
    return settings;
  }

  public static class MavenRunConfigurationFactory extends ConfigurationFactory {
    public MavenRunConfigurationFactory(ConfigurationType type) {super(type);}

    @NotNull
    @Override
    public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
      return new MavenRunConfiguration(project, this, "");
    }

    @NotNull
    @Override
    public RunConfiguration createTemplateConfiguration(@NotNull Project project, @NotNull RunManager runManager) {
      return new MavenRunConfiguration(project, this, "");
    }

    @Override
    public @NotNull String getId() {
      return "Maven";
    }

    @NotNull
    @Override
    public RunConfiguration createConfiguration(@Nullable String name, @NotNull RunConfiguration template) {
      MavenRunConfiguration cfg = (MavenRunConfiguration)super.createConfiguration(name, template);

      if (!StringUtil.isEmptyOrSpaces(cfg.getRunnerParameters().getWorkingDirPath())) return cfg;

      Project project = cfg.getProject();
      MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);

      List<MavenProject> projects = projectsManager.getProjects();
      if (projects.size() != 1) {
        return cfg;
      }

      VirtualFile directory = projects.get(0).getDirectoryFile();

      cfg.getRunnerParameters().setWorkingDirPath(directory.getPath());

      return cfg;
    }

    @Override
    public void configureBeforeRunTaskDefaults(Key<? extends BeforeRunTask> providerID, BeforeRunTask task) {
      if (providerID == CompileStepBeforeRun.ID || providerID == CompileStepBeforeRunNoErrorCheck.ID) {
        task.setEnabled(false);
      }
    }

    @Override
    public boolean isEditableInDumbMode() {
      return true;
    }
  }
}
