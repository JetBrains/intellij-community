package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.configurable.VcsManagerConfigurablePanel;

import javax.swing.*;

public class VcsManagerConfigurable implements ProjectComponent, Configurable {

  private VcsManagerConfigurablePanel myPanel;
  public static final Icon ICON = IconLoader.getIcon("/general/configurableVcs.png");
  private final Project myProject;

  public VcsManagerConfigurable(Project project) {
    myProject = project;
  }

  public void moduleAdded() {

  }

  public void projectClosed() {
  }

  public void projectOpened() {
  }

  public void disposeComponent() {
  }

  public void initComponent() { }


  public String getDisplayName() {
    return "Version Control";
  }

  public String getHelpTopic() {
    return "project.propVCSSupport";
  }

  public Icon getIcon() {
    return ICON;
  }

  public String getComponentName() {
    return "VcsManagerConfigurable";
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
}
