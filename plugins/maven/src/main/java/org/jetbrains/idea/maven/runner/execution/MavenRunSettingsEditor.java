package org.jetbrains.idea.maven.runner.execution;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.runner.MavenRunnerConfigurable;
import org.jetbrains.idea.maven.runner.MavenRunnerState;
import org.jetbrains.idea.maven.runner.executor.MavenRunnerParameters;
import org.jetbrains.idea.maven.core.MavenCoreConfigurable;
import org.jetbrains.idea.maven.core.MavenCoreState;
import org.jetbrains.idea.maven.core.action.CompositeConfigurable;

import javax.swing.*;

/**
 * @author Vladislav.Kaznacheev
 */
class MavenRunSettingsEditor extends SettingsEditor<MavenRunConfiguration> {
  private MavenRunConfiguration configuration;

  Configurable myCompositeConfigurable;

  public MavenRunSettingsEditor(final Project p) {
    myCompositeConfigurable = new CompositeConfigurable(
       new MavenRunConfigurable() {
      protected MavenRunnerParameters getParameters() {
        return configuration.getRunnerParameters();
      }
    }, new MavenCoreConfigurable() {
      protected MavenCoreState getState() {
        return configuration.getCoreState();
      }
    }, new MavenRunnerConfigurable(p, true) {
      protected MavenRunnerState getState() {
        return configuration.getRunnerState();
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
