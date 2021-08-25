package de.plushnikov.intellij.plugin.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import de.plushnikov.intellij.plugin.LombokBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static de.plushnikov.intellij.plugin.settings.ProjectSettings.isEnabled;
import static de.plushnikov.intellij.plugin.settings.ProjectSettings.setEnabled;

public class ProjectSettingsPage implements SearchableConfigurable, Configurable.NoScroll {

  private JPanel myGeneralPanel;

  private JCheckBox myEnableLombokVersionWarning;
  private JCheckBox myEnableJSPFix;
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
    myEnableLombokVersionWarning.setSelected(isEnabled(myProject, ProjectSettings.IS_LOMBOK_VERSION_CHECK_ENABLED, false));
    myEnableJSPFix.setSelected(isEnabled(myProject, ProjectSettings.IS_LOMBOK_JPS_FIX_ENABLED));
  }

  @Override
  public boolean isModified() {
    return
      myEnableLombokVersionWarning.isSelected() != isEnabled(myProject, ProjectSettings.IS_LOMBOK_VERSION_CHECK_ENABLED, false) ||
      myEnableJSPFix.isSelected() != isEnabled(myProject, ProjectSettings.IS_LOMBOK_JPS_FIX_ENABLED);
  }

  @Override
  public void apply() {
    setEnabled(myProject, ProjectSettings.IS_LOMBOK_VERSION_CHECK_ENABLED, myEnableLombokVersionWarning.isSelected());
    setEnabled(myProject, ProjectSettings.IS_LOMBOK_JPS_FIX_ENABLED, myEnableJSPFix.isSelected());
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
