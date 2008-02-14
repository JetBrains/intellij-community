package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElement;

/**
 * @author yole
 */
public class IdeaProjectSettingsService extends ProjectSettingsService {
  private Project myProject;

  public IdeaProjectSettingsService(final Project project) {
    myProject = project;
  }

  public void openModuleSettings(final Module module) {
    ModulesConfigurator.showDialog(myProject, module.getName(), null, false);
  }

  public void openModuleLibrarySettings(final Module module) {
    ModulesConfigurator.showDialog(myProject, module.getName(), ClasspathEditor.NAME, false);
  }

  public void openContentEntriesSettings(final Module module) {
    ModulesConfigurator.showDialog(myProject, module.getName(), ContentEntriesEditor.NAME, false);
  }

  public void openProjectLibrarySettings(final NamedLibraryElement element) {
    final ProjectStructureConfigurable config = ProjectStructureConfigurable.getInstance(myProject);
    ShowSettingsUtil.getInstance().editConfigurable(myProject, config, new Runnable() {
      public void run() {
        final OrderEntry orderEntry = element.getOrderEntry();
        if (orderEntry instanceof JdkOrderEntry) {
          config.select(((JdkOrderEntry)orderEntry).getJdk(), true);
        } else {
          config.select((LibraryOrderEntry)orderEntry, true);
        }
      }
    });
  }
}
