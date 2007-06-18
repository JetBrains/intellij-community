package org.jetbrains.idea.maven.builder.execution;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.builder.BuilderBundle;
import org.jetbrains.idea.maven.builder.executor.MavenBuildParameters;
import org.jetbrains.idea.maven.core.MavenDataKeys;
import org.jetbrains.idea.maven.core.util.MavenEnv;

import javax.swing.*;
import java.util.List;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenRunConfigurationType implements LocatableConfigurationType {

  private final ConfigurationFactory myFactory;
  private static final Icon ICON = IconLoader.getIcon("/images/phase.png");

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

  private static MavenBuildParameters createBuildParameters (PsiElement element) {
    final PsiFile psiFile = element.getContainingFile();
    if (psiFile != null) {
      final VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile != null && MavenEnv.POM_FILE.equals(virtualFile.getName())) {
        final DataContext dataContext = DataManager.getInstance().getDataContext();
        if (dataContext != null) {
          final List<String> goals = MavenDataKeys.MAVEN_GOALS_KEY.getData(dataContext);
          if (goals != null) {
            return new MavenBuildParameters(virtualFile.getPath(), goals);
          }
        }
      }
    }
    return null;
  }

  public static String generateName(final MavenBuildParameters buildParameters) {
    return ExecutionUtil.shortenName(buildParameters.getGoals().toString(),0);
  }

  public RunnerAndConfigurationSettings createConfigurationByLocation(Location location) {
    final MavenBuildParameters buildParameters = createBuildParameters(location.getPsiElement());
    if (buildParameters == null){
      return null;
    }

    final RunnerAndConfigurationSettingsImpl settings =
      RunManagerEx.getInstanceEx(location.getProject()).createConfiguration(generateName(buildParameters), myFactory);
    MavenRunConfiguration runConfiguration = (MavenRunConfiguration)settings.getConfiguration();
    runConfiguration.setBuildParameters(buildParameters);
    return settings;
  }

  public boolean isConfigurationByElement(RunConfiguration configuration, Project project, PsiElement element) {
    return configuration instanceof MavenRunConfiguration &&
           ((MavenRunConfiguration)configuration).getBuildParameters().equals(createBuildParameters(element));
  }
}
