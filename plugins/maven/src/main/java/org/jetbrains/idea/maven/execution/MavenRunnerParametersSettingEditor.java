package org.jetbrains.idea.maven.execution;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Sergey Evdokimov
 */
public class MavenRunnerParametersSettingEditor extends SettingsEditor<MavenRunConfiguration> {

  private final MavenRunnerParametersPanel myPanel;

  public MavenRunnerParametersSettingEditor(@NotNull Project project) {
    myPanel = new MavenRunnerParametersPanel(project);
  }

  @Override
  protected void resetEditorFrom(MavenRunConfiguration runConfiguration) {
    myPanel.getData(runConfiguration.getRunnerParameters());
  }

  @Override
  protected void applyEditorTo(MavenRunConfiguration runConfiguration) throws ConfigurationException {
    myPanel.setData(runConfiguration.getRunnerParameters());
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    return myPanel.createComponent();
  }

  @Override
  protected void disposeEditor() {
    myPanel.disposeUIResources();
  }
}
