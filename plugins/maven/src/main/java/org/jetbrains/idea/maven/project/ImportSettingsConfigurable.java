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
public class ImportSettingsConfigurable implements Configurable {
  private MavenImportSettings myImporterSettings;

  private JPanel panel;
  private MavenImportSettingsForm mySettingsForm;

  public ImportSettingsConfigurable(MavenImportSettings importerSettings) {
    myImporterSettings = importerSettings;
  }

  @Nls
  public String getDisplayName() {
    return ProjectBundle.message("maven.import");
  }

  @Nullable
  public Icon getIcon() {
    return null;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    return panel;
  }

  public boolean isModified() {
    return mySettingsForm.isModified(myImporterSettings);
  }

  public void apply() throws ConfigurationException {
    mySettingsForm.getData(myImporterSettings);
  }

  public void reset() {
    mySettingsForm.setData(myImporterSettings);
  }

  public void disposeUIResources() {
  }
}