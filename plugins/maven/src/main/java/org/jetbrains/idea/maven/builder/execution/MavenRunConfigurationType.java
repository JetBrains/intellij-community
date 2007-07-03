package org.jetbrains.idea.maven.builder.execution;

import com.intellij.execution.LocatableConfigurationType;
import com.intellij.execution.Location;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.builder.BuilderBundle;
import org.jetbrains.idea.maven.builder.executor.MavenBuildParameters;
import org.jetbrains.idea.maven.state.MavenProjectsState;

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
    return BuilderBundle.message("maven.run.configuration.name");
  }

  public String getConfigurationTypeDescription() {
    return BuilderBundle.message("maven.run.configuration.description");
  }

  public Icon getIcon() {
    return ICON;
  }

  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myFactory};
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "MavenRunConfiguration";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  private static MavenBuildParameters createBuildParameters() {
    final DataContext dataContext = DataManager.getInstance().getDataContext();
    return dataContext != null ? MavenBuildParameters.createBuildParameters(dataContext) : null;
  }

  public static String generateName(final Project project, final MavenBuildParameters buildParameters) {
    StringBuilder stringBuilder = new StringBuilder();

    final String name = getMavenProjectName(project, buildParameters);
    if (!StringUtil.isEmptyOrSpaces(name)) {
      stringBuilder.append(name);
      stringBuilder.append(" ");
    }

    stringBuilder.append("[");
    listGoals(stringBuilder, buildParameters.getGoals());
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

  private static String getMavenProjectName(final Project project, final MavenBuildParameters buildParameters) {
    LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
    final VirtualFile virtualFile = localFileSystem.findFileByPath(buildParameters.getPomPath());
    if (virtualFile != null) {
      MavenProject mavenProject = project.getComponent(MavenProjectsState.class).getMavenProject(virtualFile);
      if (mavenProject != null) {
        if (!StringUtil.isEmptyOrSpaces(mavenProject.getArtifactId())) {
          return mavenProject.getArtifactId();
        }
      }
    }
    return null;
  }

  public RunnerAndConfigurationSettings createConfigurationByLocation(Location location) {
    final MavenBuildParameters buildParameters = createBuildParameters();
    if (buildParameters == null) {
      return null;
    }

    final RunnerAndConfigurationSettingsImpl settings = RunManagerEx.getInstanceEx(location.getProject())
      .createConfiguration(generateName(location.getProject(), buildParameters), myFactory);
    MavenRunConfiguration runConfiguration = (MavenRunConfiguration)settings.getConfiguration();
    runConfiguration.setBuildParameters(buildParameters);
    return settings;
  }

  public boolean isConfigurationByElement(RunConfiguration configuration, Project project, PsiElement element) {
    return configuration instanceof MavenRunConfiguration &&
           ((MavenRunConfiguration)configuration).getBuildParameters().equals(createBuildParameters());
  }
}
