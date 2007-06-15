package org.jetbrains.idea.maven.builder.execution;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.builder.MavenBuilderConfigurable;
import org.jetbrains.idea.maven.builder.MavenBuilderState;
import org.jetbrains.idea.maven.builder.executor.MavenBuildParameters;
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

  public MavenRunSettingsEditor() {
    myCompositeConfigurable = new CompositeConfigurable(
       new MavenRunConfigurable() {
      protected MavenBuildParameters getParameters() {
        return configuration.getBuildParameters();
      }
    }, new MavenCoreConfigurable() {
      protected MavenCoreState getState() {
        return configuration.getCoreState();
      }
    }, new MavenBuilderConfigurable(false) {
      protected MavenBuilderState getState() {
        return configuration.getBuilderState();
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
