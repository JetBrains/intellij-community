package com.intellij.openapi.options.ex;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author max
 */
public class ProjectConfigurablesGroup implements ConfigurableGroup {
  private Project myProject;

  public ProjectConfigurablesGroup(Project project) {
    myProject = project;
  }

  public String getDisplayName() {
    if (isDefault()) return OptionsBundle.message("template.project.settings.display.name");
    VirtualFile projectFile = myProject.getProjectFile();
    final String projectName = (projectFile != null ? projectFile.getNameWithoutExtension() : OptionsBundle.message("unknown.project.display.name"));
    return OptionsBundle.message("project.settings.display.name", projectName);
  }

  public String getShortName() {
    return isDefault() ? OptionsBundle.message("template.project.settings.short.name") : OptionsBundle
      .message("project.settings.short.name");
  }

  private boolean isDefault() {
    return myProject.isDefault();
  }

  public Configurable[] getConfigurables() {
    return myProject.getComponents(Configurable.class);
  }

  public int hashCode() {
    return 0;
  }

  public boolean equals(Object object) {
    return object instanceof ProjectConfigurablesGroup && ((ProjectConfigurablesGroup)object).myProject == myProject;
  }
}
