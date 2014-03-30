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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import icons.MavenIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;

import javax.swing.*;
import java.util.List;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenRunConfigurationType implements ConfigurationType {
  private final ConfigurationFactory myFactory;
  private static final int MAX_NAME_LENGTH = 40;

  public static MavenRunConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(MavenRunConfigurationType.class);
  }

  /**
   * reflection
   */
  MavenRunConfigurationType() {
    myFactory = new ConfigurationFactory(this) {
      public RunConfiguration createTemplateConfiguration(Project project) {
        return new MavenRunConfiguration(project, this, "");
      }

      public RunConfiguration createTemplateConfiguration(Project project, RunManager runManager) {
        return new MavenRunConfiguration(project, this, "");
      }

      @Override
      public RunConfiguration createConfiguration(String name, RunConfiguration template) {
        MavenRunConfiguration cfg = (MavenRunConfiguration)super.createConfiguration(name, template);

        if (!StringUtil.isEmptyOrSpaces(cfg.getRunnerParameters().getWorkingDirPath())) return cfg;

        Project project = cfg.getProject();
        if (project == null) return cfg;

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
    };
  }

  public String getDisplayName() {
    return RunnerBundle.message("maven.run.configuration.name");
  }

  public String getConfigurationTypeDescription() {
    return RunnerBundle.message("maven.run.configuration.description");
  }

  public Icon getIcon() {
    return MavenIcons.Phase;
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


  public static void runConfiguration(Project project,
                                      MavenRunnerParameters params,
                                      @Nullable ProgramRunner.Callback callback) {
    runConfiguration(project, params, null, null, callback);
  }

  public static void runConfiguration(Project project,
                                      @NotNull MavenRunnerParameters params,
                                      @Nullable MavenGeneralSettings settings,
                                      @Nullable  MavenRunnerSettings runnerSettings,
                                      @Nullable ProgramRunner.Callback callback) {
    RunnerAndConfigurationSettings configSettings = createRunnerAndConfigurationSettings(settings,
                                                                                         runnerSettings,
                                                                                         params,
                                                                                         project);

    ProgramRunner runner = DefaultJavaProgramRunner.getInstance();
    Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    ExecutionEnvironment env = new ExecutionEnvironment(executor, runner, configSettings, project);

    try {
      runner.execute(env, callback);
    }
    catch (ExecutionException e) {
      MavenUtil.showError(project, "Failed to execute Maven goal", e);
    }
  }

  public static RunnerAndConfigurationSettings createRunnerAndConfigurationSettings(@Nullable MavenGeneralSettings generalSettings,
                                                                             @Nullable MavenRunnerSettings runnerSettings,
                                                                             MavenRunnerParameters params,
                                                                             Project project) {
    MavenRunConfigurationType type = ConfigurationTypeUtil.findConfigurationType(MavenRunConfigurationType.class);

    final RunnerAndConfigurationSettings settings = RunManagerEx.getInstanceEx(project)
      .createConfiguration(generateName(project, params), type.myFactory);
    MavenRunConfiguration runConfiguration = (MavenRunConfiguration)settings.getConfiguration();
    runConfiguration.setRunnerParameters(params);
    runConfiguration.setGeneralSettings(generalSettings);
    runConfiguration.setRunnerSettings(runnerSettings);

    return settings;
  }
}
