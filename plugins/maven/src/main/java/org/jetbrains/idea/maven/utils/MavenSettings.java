package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.Nls;
import org.jetbrains.idea.maven.indices.MavenIndicesConfigurable;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.runner.MavenRunnerConfigurable;
import org.jetbrains.idea.maven.runner.MavenRunnerSettings;
import org.jetbrains.idea.maven.runner.MavenRunner;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class MavenSettings extends SearchableConfigurable.Parent.Abstract {
  private Project myProject;

  public MavenSettings(Project project) {
    myProject = project;
  }

  protected Configurable[] buildConfigurables() {
    List<Configurable> result = new ArrayList<Configurable>();

    result.add(new MavenGeneralConfigurable() {
      protected MavenGeneralSettings getState() {
        return MavenProjectsManager.getInstance(myProject).getGeneralSettings();
      }
    });
    result.add(new MavenImportingConfigurable(MavenProjectsManager.getInstance(myProject).getImportingSettings()));
    result.add(new MavenIgnoreConfigurable(MavenProjectsManager.getInstance(myProject)));
    result.add(new MavenDownloadingConfigurable(MavenProjectsManager.getInstance(myProject).getArtifactSettings()));

    result.add(new MavenRunnerConfigurable(myProject, false) {
      protected MavenRunnerSettings getState() {
        return MavenRunner.getInstance(myProject).getState();
      }
    });

    if (!myProject.isDefault()) {
      result.add(new MavenIndicesConfigurable(myProject));
    }

    return result.toArray(new Configurable[result.size()]);
  }

  public String getId() {
    return MavenSettings.class.getSimpleName();
  }

  @Nls
  public String getDisplayName() {
    return "Maven";
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableEditor.png");
  }

  public String getHelpTopic() {
    return null;
  }
}
