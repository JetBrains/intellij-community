package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.committed.CacheSettingsPanel;
import com.intellij.openapi.vcs.changes.ui.IgnoredSettingsPanel;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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

    List<Configurable> result = new ArrayList<Configurable>();

    result.add(mappings);
    result.add(generalPanel);
    result.add(new VcsBackgroundOperationsConfigurationPanel(myProject));
    result.add(new IgnoredSettingsPanel(myProject));
    if (!myProject.isDefault()) {
      result.add(new CacheSettingsPanel(myProject));
    }
    result.add(new IssueNavigationConfigurationPanel(myProject));

    AbstractVcs[] vcses = ProjectLevelVcsManager.getInstance(myProject).getAllVcss();

    if (vcses.length > 0) {
      result.add(createVcsComposeConfigurable(vcses));
    }

    return result.toArray(new Configurable[result.size()]);

  }

  private Configurable createVcsComposeConfigurable(final AbstractVcs[] vcses) {
    return new SearchableConfigurable.Parent.Abstract(){
      protected Configurable[] buildConfigurables() {
        List<Configurable> result = new ArrayList<Configurable>();
        for (AbstractVcs vcs : vcses) {
          result.add(createVcsConfigurableWrapper(vcs));
        }
        return result.toArray(new Configurable[result.size()]);
      }

      public String getId() {
        return "project.propVCSSupport.vcses";
      }

      @Nls
      public String getDisplayName() {
        return "VCSs";
      }

      public Icon getIcon() {
        return null;
      }

      public String getHelpTopic() {
        return null;
      }
    };
  }

  private Configurable createVcsConfigurableWrapper(final AbstractVcs vcs) {
    final Configurable delegate = vcs.getConfigurable();
    return new Configurable(){
      @Nls
      public String getDisplayName() {
        return vcs.getDisplayName();
      }

      public Icon getIcon() {
        return null;
      }

      public String getHelpTopic() {
        return delegate.getHelpTopic();
      }

      public JComponent createComponent() {
        return delegate.createComponent();
      }

      public boolean isModified() {
        return delegate.isModified();
      }

      public void apply() throws ConfigurationException {
        delegate.apply();
      }

      public void reset() {
        delegate.reset();
      }

      public void disposeUIResources() {
        delegate.disposeUIResources();
      }
    };
  }

}
