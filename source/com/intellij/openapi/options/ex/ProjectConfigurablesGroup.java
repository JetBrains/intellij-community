package com.intellij.openapi.options.ex;

import com.intellij.ide.util.scopeChooser.ScopeChooserConfigurable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.project.Project;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

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
    return OptionsBundle.message("project.settings.display.name", myProject.getName());
  }

  public String getShortName() {
    return isDefault() ? OptionsBundle.message("template.project.settings.short.name") : OptionsBundle
      .message("project.settings.short.name");
  }

  private boolean isDefault() {
    return myProject.isDefault();
  }

  public Configurable[] getConfigurables() {
    final Configurable[] extensions = myProject.getExtensions(Configurable.PROJECT_CONFIGURABLES);
    Configurable[] components = myProject.getComponents(Configurable.class);

    List<Configurable> result = new ArrayList<Configurable>();
    result.addAll(Arrays.asList(extensions));
    result.addAll(Arrays.asList(components));

    if (isDefault()) {
      final Iterator<Configurable> iterator = result.iterator();
      while (iterator.hasNext()) {
        Configurable configurable = iterator.next();
        if (configurable instanceof ScopeChooserConfigurable) {
          iterator.remove();
        }
      }
    }

    IdeConfigurablesGroup.removeAssistants(result);

    return result.toArray(new Configurable[result.size()]);
  }

  public int hashCode() {
    return 0;
  }

  public boolean equals(Object object) {
    return object instanceof ProjectConfigurablesGroup && ((ProjectConfigurablesGroup)object).myProject == myProject;
  }
}
