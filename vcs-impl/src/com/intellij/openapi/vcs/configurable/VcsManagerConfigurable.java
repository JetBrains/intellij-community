package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsBundle;

import javax.swing.*;
import java.util.Collection;

public class VcsManagerConfigurable extends SearchableConfigurable.Parent.Abstract {
  public static final Icon ICON = IconLoader.getIcon("/general/configurableVcs.png");
  private final Project myProject;

  public VcsManagerConfigurable(Project project) {
    myProject = project;
  }

  public void moduleAdded() {

  }

  public String getDisplayName() {
    return VcsBundle.message("version.control.main.configurable.name");
  }

  public String getHelpTopic() {
    return "project.propVCSSupport";
  }

  public Icon getIcon() {
    return ICON;
  }

  public String getId() {
    return getHelpTopic();
  }

  protected Configurable[] buildConfigurables() {
    VcsDirectoryConfigurationPanel mappings = new VcsDirectoryConfigurationPanel(myProject);
    final VcsGeneralConfigurationPanel generalPanel = new VcsGeneralConfigurationPanel(myProject);
    generalPanel.updateAvailableOptions(mappings.getActiveVcses());
    mappings.addVcsListener(new ModuleVcsListener() {
      public void activeVcsSetChanged(Collection<AbstractVcs> activeVcses) {
        generalPanel.updateAvailableOptions(activeVcses);
      }
    });
    return new Configurable[]{
      mappings,
      generalPanel, 
      new VcsBackgroundOperationsConfigurationPanel(myProject),
      new IssueNavigationConfigurationPanel(myProject)
    };

  }

}
