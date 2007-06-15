package org.jetbrains.idea.maven.builder.execution;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
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

  public static MavenRunConfigurationType getInstance() {
    return ApplicationManager.getApplication().getComponent(MavenRunConfigurationType.class);
  }

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

  public RunnerAndConfigurationSettings createConfigurationByLocation(Location location) {
    String path = "";
    List<String> goals = null;

    final PsiFile psiFile = location.getPsiElement().getContainingFile();
    if (psiFile != null) {
      final VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile != null) {
        if(!MavenEnv.POM_FILE.equals(virtualFile.getName())){
          return null;
        }
        path = virtualFile.getPath();
        final DataContext dataContext = DataManager.getInstance().getDataContext();
        if (dataContext!=null){
          goals = MavenDataKeys.MAVEN_GOALS_KEY.getData(dataContext);
        }
      }
    }

    if (goals == null) {
      return null;
    }

    final String name = ExecutionUtil.shortenName(goals.toString(),0);
    final RunnerAndConfigurationSettingsImpl settings =
      RunManagerEx.getInstanceEx(location.getProject()).createConfiguration(name, myFactory);
    final RunConfiguration configuration = settings.getConfiguration();
    if (configuration instanceof MavenRunConfiguration) {
      final MavenBuildParameters mavenBuildParameters = ((MavenRunConfiguration)configuration).getBuildParameters();
      mavenBuildParameters.setPomPath(path);
      mavenBuildParameters.getGoals().clear();
      mavenBuildParameters.getGoals().addAll(goals);
    }
    return settings;
  }

  public boolean isConfigurationByElement(RunConfiguration configuration, Project project, PsiElement element) {
    if (configuration instanceof MavenRunConfiguration) {
      MavenRunConfiguration mavenRunConfiguration = (MavenRunConfiguration)configuration;
      if (element instanceof PsiFile) {
        final VirtualFile virtualFile = ((PsiFile)element).getVirtualFile();
        if (virtualFile != null) {
          return mavenRunConfiguration.getBuildParameters().getPomPath().equalsIgnoreCase(virtualFile.getPath());
        }
      }
    }
    return false;
  }
}
