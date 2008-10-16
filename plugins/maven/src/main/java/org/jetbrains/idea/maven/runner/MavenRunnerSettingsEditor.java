package org.jetbrains.idea.maven.runner;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.core.MavenCoreConfigurable;
import org.jetbrains.idea.maven.core.MavenCoreSettings;
import org.jetbrains.idea.maven.core.action.CompositeConfigurable;

import javax.swing.*;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenRunnerSettingsEditor extends SettingsEditor<MavenRunConfiguration> {
  private MavenRunConfiguration configuration;

  Configurable myCompositeConfigurable;

  public MavenRunnerSettingsEditor(final Project p) {
    myCompositeConfigurable = new CompositeConfigurable(
       new MavenRunnerParametersConfigurable() {
      protected MavenRunnerParameters getParameters() {
        return configuration.getRunnerParameters();
      }
    }, new MavenCoreConfigurable() {
      protected MavenCoreSettings getState() {
        return configuration.getCoreSettings();
      }
    }, new MavenRunnerSettingsConfigurable(p, true) {
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
