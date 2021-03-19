package de.plushnikov.intellij.plugin.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import de.plushnikov.intellij.plugin.LombokBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ProjectSettingsPage implements SearchableConfigurable, Configurable.NoScroll {

  private JPanel myGeneralPanel;

  private JCheckBox myEnableLombokVersionWarning;

  private final Project myProject;

  public ProjectSettingsPage(Project project) {
    myProject = project;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return LombokBundle.message("plugin.settings.title");
  }

  @Override
  public JComponent createComponent() {
    initFromSettings();
    return myGeneralPanel;
  }

  private void initFromSettings() {
    myEnableLombokVersionWarning.setSelected(ProjectSettings.isEnabled(myProject, ProjectSettings.IS_LOMBOK_VERSION_CHECK_ENABLED, false));
  }

  @Override
  public boolean isModified() {
    return
      myEnableLombokVersionWarning.isSelected() !=
      ProjectSettings.isEnabled(myProject, ProjectSettings.IS_LOMBOK_VERSION_CHECK_ENABLED, false);
  }

  @Override
  public void apply() {
    ProjectSettings.setEnabled(myProject, ProjectSettings.IS_LOMBOK_VERSION_CHECK_ENABLED, myEnableLombokVersionWarning.isSelected());
  }

  @Override
  public void reset() {
    initFromSettings();
  }

  @NotNull
  @Override
  public String getId() {
    return getDisplayName();
  }
}
