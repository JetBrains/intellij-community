package de.plushnikov.intellij.plugin.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ProjectSettingsPage implements SearchableConfigurable, Configurable.NoScroll {

  private JPanel myPanel;
  private JCheckBox myEnableLombokInProject;

  private Project myProject;

  public ProjectSettingsPage(Project project) {
    myProject = project;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Lombok plugin";
  }

  @Nullable
  public Icon getIcon() {
    return null;
  }

  @Override
  public String getHelpTopic() {
    return null;
  }

  @Override
  public JComponent createComponent() {
    myEnableLombokInProject.setSelected(ProjectSettings.isEnabledInProject(myProject));
    return myPanel;
  }

  @Override
  public boolean isModified() {
    return myEnableLombokInProject.isSelected() != ProjectSettings.isEnabledInProject(myProject);
  }

  @Override
  public void apply() throws ConfigurationException {
    ProjectSettings.setEnabledInProject(myProject, myEnableLombokInProject.isSelected());
  }

  @Override
  public void reset() {
    myEnableLombokInProject.setSelected(ProjectSettings.isEnabledInProject(myProject));
  }

  @Override
  public void disposeUIResources() {

  }

  @NotNull
  @Override
  public String getId() {
    return getDisplayName();
  }

  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

}
