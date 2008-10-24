package org.jetbrains.idea.maven.project;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenImportingConfigurable implements Configurable {
  private MavenImportingSettings myImportingSettings;

  private JPanel panel;
  private MavenImportingSettingsForm mySettingsForm;

  public MavenImportingConfigurable(MavenImportingSettings importingSettings) {
    myImportingSettings = importingSettings;
  }

  @Nls
  public String getDisplayName() {
    return ProjectBundle.message("maven.tab.importing");
  }

  @Nullable
  public Icon getIcon() {
    return null;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "reference.settings.project.maven.importing";
  }

  public JComponent createComponent() {
    return panel;
  }

  public boolean isModified() {
    return mySettingsForm.isModified(myImportingSettings);
  }

  public void apply() throws ConfigurationException {
    mySettingsForm.getData(myImportingSettings);
  }

  public void reset() {
    mySettingsForm.setData(myImportingSettings);
  }

  public void disposeUIResources() {
  }
}