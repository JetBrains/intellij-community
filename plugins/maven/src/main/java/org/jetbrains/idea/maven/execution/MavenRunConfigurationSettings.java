package org.jetbrains.idea.maven.execution;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenGeneralConfigurable;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;

import javax.swing.*;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenRunConfigurationSettings extends SettingsEditor<MavenRunConfiguration> {
  private MavenRunConfiguration configuration;
  Configurable myCompositeConfigurable;

  public MavenRunConfigurationSettings(final Project p) {
    myCompositeConfigurable = new CompositeConfigurable(
       new MavenRunnerParametersConfigurable() {
      protected MavenRunnerParameters getParameters() {
        return configuration.getRunnerParameters();
      }
    }, new MavenGeneralConfigurable() {
      protected MavenGeneralSettings getState() {
        return configuration.getGeneralSettings();
      }
    }, new MavenRunnerConfigurable(p, true) {
      protected MavenRunnerSettings getState() {
        return configuration.getRunnerSettings();
      }
    });
  }

  protected void resetEditorFrom(MavenRunConfiguration configuration) {
    this.configuration = configuration;
    myCompositeConfigurable.reset();
  }

  protected void applyEditorTo(MavenRunConfiguration configuration) throws ConfigurationException {
    this.configuration = configuration;
    myCompositeConfigurable.apply();
  }

  @NotNull
  protected JComponent createEditor() {
    return myCompositeConfigurable.createComponent();
  }

  protected void disposeEditor() {
    myCompositeConfigurable.disposeUIResources();
  }
}
