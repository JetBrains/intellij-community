package org.jetbrains.idea.maven.project;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class MavenImportingConfigurable extends MavenImportingSettingsForm implements Configurable {
  private final MavenImportingSettings myImportingSettings;
  private final MavenImportingSettingsForm mySettingsForm = new MavenImportingSettingsForm(false);

  public MavenImportingConfigurable(MavenImportingSettings importingSettings) {
    super(false);
    myImportingSettings = importingSettings;
  }

  public void disposeUIResources() {
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

}
