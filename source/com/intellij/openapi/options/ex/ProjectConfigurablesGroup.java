package com.intellij.openapi.options.ex;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurable;
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
    if (isDefault()) return "Template Project Settings";
    VirtualFile projectFile = myProject.getProjectFile();
    return "Project Settings [" + (projectFile != null ? projectFile.getNameWithoutExtension() : "unknown") + "]";
  }

  public String getShortName() {
    return isDefault() ? "Template Project" : "Project";
  }

  private boolean isDefault() {
    return myProject.isDefault();
  }

  public Configurable[] getConfigurables() {
    Configurable[] components = myProject.getComponents(Configurable.class);
    Configurable[] configurables = new Configurable[components.length - (isDefault() ? 1 : 0)];
    int j = 0;
    for (int i = 0; i < components.length; i++) {
      if (components[i] instanceof ModulesConfigurable && isDefault()) continue;
      configurables[j++] = components[i];
    }
    return configurables;
  }

  public int hashCode() {
    return 0;
  }

  public boolean equals(Object object) {
    return object instanceof ProjectConfigurablesGroup && ((ProjectConfigurablesGroup)object).myProject == myProject;
  }
}
