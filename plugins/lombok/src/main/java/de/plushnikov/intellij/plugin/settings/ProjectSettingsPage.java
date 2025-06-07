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

public final class ProjectSettingsPage implements SearchableConfigurable, Configurable.NoScroll {

  private JPanel myGeneralPanel;

  private JCheckBox myEnableJSPFix;
  private final Project myProject;

  public ProjectSettingsPage(Project project) {
    myProject = project;
  }

  @Override
  public @Nls String getDisplayName() {
    return LombokBundle.message("plugin.settings.title");
  }

  @Override
  public JComponent createComponent() {
    initFromSettings();
    return myGeneralPanel;
  }

  private void initFromSettings() {
    myEnableJSPFix.setSelected(isEnabled(myProject, ProjectSettings.IS_LOMBOK_JPS_FIX_ENABLED));
  }

  @Override
  public boolean isModified() {
    return
      myEnableJSPFix.isSelected() != isEnabled(myProject, ProjectSettings.IS_LOMBOK_JPS_FIX_ENABLED);
  }

  @Override
  public void apply() {
    setEnabled(myProject, ProjectSettings.IS_LOMBOK_JPS_FIX_ENABLED, myEnableJSPFix.isSelected());
  }

  @Override
  public void reset() {
    initFromSettings();
  }

  @Override
  public @NotNull String getId() {
    return getDisplayName();
  }
}
