package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class VcsManagerConfigurable implements SearchableConfigurable {
  private VcsManagerConfigurablePanel myPanel;
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

  public JComponent createComponent() {
    myPanel = new VcsManagerConfigurablePanel(myProject);
    return myPanel.getPanel();
  }

  public void apply() throws ConfigurationException {
    myPanel.apply();
  }

  public void reset() {
    myPanel.reset();
  }

  public void disposeUIResources() {
    myPanel = null;
  }

  public boolean isModified() {
    return myPanel.isModified();
  }


  public String getId() {
    return getHelpTopic();
  }

  public boolean clearSearch() {
    return false;
  }

  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }
}
