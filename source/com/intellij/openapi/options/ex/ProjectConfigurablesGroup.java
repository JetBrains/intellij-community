package com.intellij.openapi.options.ex;

import com.intellij.ide.util.scopeChooser.ScopeChooserConfigurable;
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
    Configurable[] components = myProject.getComponents(Configurable.class);
    Configurable[] configurables = new Configurable[components.length - (isDefault() ? 1 : 0)];
    int j = 0;
    for (Configurable component : components) {
      if (component instanceof ScopeChooserConfigurable && isDefault()) continue; //can't configgure scopes without project
      configurables[j++] = component;
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
