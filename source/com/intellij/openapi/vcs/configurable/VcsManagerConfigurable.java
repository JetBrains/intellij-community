package com.intellij.openapi.vcs.configurable;

import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.GlassPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.VcsBundle;

import javax.swing.*;

public class VcsManagerConfigurable implements SearchableConfigurable, ProjectComponent {
  private VcsManagerConfigurablePanel myPanel;
  public static final Icon ICON = IconLoader.getIcon("/general/configurableVcs.png");
  private final Project myProject;
  private GlassPanel myGlassPanel;

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

  public void initComponent() {
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

  public String getComponentName() {
    return "VcsManagerConfigurable";
  }

  public JComponent createComponent() {
    myPanel = new VcsManagerConfigurablePanel(myProject);
    myGlassPanel = new GlassPanel(myPanel.getPanel());
    return myPanel.getPanel();
  }

  public void apply() throws ConfigurationException {
    myPanel.apply();
  }

  public void reset() {
    myPanel.getPanel().getRootPane().setGlassPane(myGlassPanel);
    myPanel.reset();
  }

  public void disposeUIResources() {
    myPanel = null;
  }

  public boolean isModified() {
    return myPanel.isModified();
  }

  public Runnable showOption(String option) {
    return SearchUtil.lightOptions(this, option, myPanel.getPanel(), myPanel.myTabs, myGlassPanel);
  }

  public String getId() {
    return getHelpTopic();
  }

  public void clearSearch() {
    myGlassPanel.clear();
  }
}
