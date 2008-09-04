package org.jetbrains.idea.maven.runner.execution;

import com.intellij.execution.LocatableConfigurationType;
import com.intellij.execution.Location;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProjectModel;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.runner.RunnerBundle;
import org.jetbrains.idea.maven.runner.executor.MavenRunnerParameters;

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
        return new MavenRunConfiguration(project, this, "");
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
    final VirtualFile virtualFile = localFileSystem.findFileByPath(runnerParameters.getPomPath());
    if (virtualFile != null) {
      MavenProjectModel mavenProject = MavenProjectsManager.getInstance(project).findProject(virtualFile);
      if (mavenProject != null) {
        if (!StringUtil.isEmptyOrSpaces(mavenProject.getMavenId().artifactId)) {
          return mavenProject.getMavenId().artifactId;
        }
      }
    }
    return null;
  }

  public RunnerAndConfigurationSettings createConfigurationByLocation(Location l) {
    final MavenRunnerParameters runnerParameters = createBuildParameters(l);
    if (runnerParameters == null) return null;

    final RunnerAndConfigurationSettingsImpl settings = RunManagerEx.getInstanceEx(l.getProject())
      .createConfiguration(generateName(l.getProject(), runnerParameters), myFactory);
    MavenRunConfiguration runConfiguration = (MavenRunConfiguration)settings.getConfiguration();
    runConfiguration.setRunnerParameters(runnerParameters);
    return settings;
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

    return new MavenRunnerParameters(f.getPath(), goals, profiles);
  }

}
