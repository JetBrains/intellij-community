package org.jetbrains.idea.maven.runner;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.compiler.options.CompileStepBeforeRun;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.swing.*;
import java.util.List;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenRunConfigurationType implements LocatableConfigurationType {
  private final ConfigurationFactory myFactory;
  private static final Icon ICON = IconLoader.getIcon("/images/phase.png");
  private static final int MAX_NAME_LENGTH = 40;

  /**
   * reflection
   */
  MavenRunConfigurationType() {
    myFactory = new ConfigurationFactory(this) {
      public RunConfiguration createTemplateConfiguration(Project project) {
        throw new UnsupportedOperationException();
      }

      public RunConfiguration createTemplateConfiguration(Project project, RunManager runManager) {
        return new MavenRunConfiguration(project, this, "");
      }

      @Override
      public void configureBeforeRunTaskDefaults(Key<? extends BeforeRunTask> providerID, BeforeRunTask task) {
        if (providerID == CompileStepBeforeRun.ID) {
          task.setEnabled(false);
        }
      }
    };
  }

  public String getDisplayName() {
    return RunnerBundle.message("maven.run.configuration.name");
  }

  public String getConfigurationTypeDescription() {
    return RunnerBundle.message("maven.run.configuration.description");
  }

  public Icon getIcon() {
    return ICON;
  }

  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myFactory};
  }

  @NonNls
  @NotNull
  public String getId() {
    return "MavenRunConfiguration";
  }

  public static String generateName(Project project, MavenRunnerParameters runnerParameters) {
    StringBuilder stringBuilder = new StringBuilder();

    final String name = getMavenProjectName(project, runnerParameters);
    if (!StringUtil.isEmptyOrSpaces(name)) {
      stringBuilder.append(name);
      stringBuilder.append(" ");
    }

    stringBuilder.append("[");
    listGoals(stringBuilder, runnerParameters.getGoals());
    stringBuilder.append("]");

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

  private static String getMavenProjectName(final Project project, final MavenRunnerParameters runnerParameters) {
    LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
    final VirtualFile virtualFile = localFileSystem.findFileByPath(runnerParameters.getPomFilePath());
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

  public RunnerAndConfigurationSettings createConfigurationByLocation(Location l) {
    final MavenRunnerParameters params = createBuildParameters(l);
    if (params == null) return null;
    return createRunnerAndConfigurationSettings(null, null, params, l.getProject(), false);
  }

  public boolean isConfigurationByLocation(RunConfiguration configuration, Location location) {
    return configuration instanceof MavenRunConfiguration &&
           ((MavenRunConfiguration)configuration).getRunnerParameters().equals(createBuildParameters(location));
  }

  private static MavenRunnerParameters createBuildParameters(Location l) {
    if (!(l instanceof MavenGoalLocation)) return null;

    VirtualFile f = ((PsiFile)l.getPsiElement()).getVirtualFile();
    List<String> goals = ((MavenGoalLocation)l).getGoals();
    List<String> profiles = MavenProjectsManager.getInstance(l.getProject()).getActiveProfiles();

    return new MavenRunnerParameters(true, f.getParent().getPath(), goals, profiles);
  }

  public static void runConfiguration(Project project, MavenRunnerParameters params, DataContext dataContext) throws ExecutionException {
    doRunConfiguration(dataContext, createRunnerAndConfigurationSettings(MavenProjectsManager.getInstance(project).getGeneralSettings(),
                                                                         MavenRunner.getInstance(project).getState(),
                                                                         params,
                                                                         project,
                                                                         true));
  }

  private static void doRunConfiguration(DataContext dataContext, RunnerAndConfigurationSettings settings) throws ExecutionException {
    ProgramRunner runner = RunnerRegistry.getInstance().findRunnerById(DefaultRunExecutor.EXECUTOR_ID);
    runner.execute(DefaultRunExecutor.getRunExecutorInstance(), new ExecutionEnvironment(runner, settings, dataContext));
  }

  private static RunnerAndConfigurationSettings createRunnerAndConfigurationSettings(MavenGeneralSettings generalSettings,
                                                                                     MavenRunnerSettings runnerSettings,
                                                                                     MavenRunnerParameters params,
                                                                                     Project project,
                                                                                     boolean diableMakeBeforeRun) {
    MavenRunConfigurationType type = ConfigurationTypeUtil.findConfigurationType(MavenRunConfigurationType.class);

    final RunnerAndConfigurationSettingsImpl settings = RunManagerEx.getInstanceEx(project)
      .createConfiguration(generateName(project, params), type.myFactory);
    MavenRunConfiguration runConfiguration = (MavenRunConfiguration)settings.getConfiguration();
    runConfiguration.setRunnerParameters(params);
    if (generalSettings != null) runConfiguration.setGeneralSettings(generalSettings);
    if (runnerSettings != null) runConfiguration.setRunnerSettings(runnerSettings);

    if (diableMakeBeforeRun) disableMakeBeforeRun(RunManager.getInstance(project), runConfiguration);

    return settings;
  }

  private static void disableMakeBeforeRun(RunManager runManager, MavenRunConfiguration runConfiguration) {
    //((RunManagerEx)runManager).getBeforeRunTask(runConfiguration, CompileStepBeforeRun.ID).setEnabled(false);
  }
}
