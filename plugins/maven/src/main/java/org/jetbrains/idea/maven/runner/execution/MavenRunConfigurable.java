package org.jetbrains.idea.maven.runner.execution;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.util.Strings;
import org.jetbrains.idea.maven.runner.RunnerBundle;
import org.jetbrains.idea.maven.runner.executor.MavenRunnerParameters;

import javax.swing.*;

/**
 * @author Vladislav.Kaznacheev
 */
public abstract class MavenRunConfigurable implements Configurable {
  private JPanel panel;
  protected LabeledComponent<TextFieldWithBrowseButton> pomComponent;
  protected LabeledComponent<JTextField> goalsComponent;
  private LabeledComponent<JTextField> profilesComponent;

  public MavenRunConfigurable() {
    pomComponent.getComponent().addBrowseFolderListener(RunnerBundle.message("maven.select.maven.project.file"), "", null,
                                                        new FileChooserDescriptor(true, false, false, false, false, false));
  }

  public JComponent createComponent() {
    return panel;
  }

  public void disposeUIResources() {
  }

  public String getDisplayName() {
    return RunnerBundle.message("maven.tab.project");
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

  public boolean isModified() {
    MavenRunnerParameters formParameters = new MavenRunnerParameters();
    setData(formParameters);
    return !formParameters.equals(getParameters());
  }

  public void apply() throws ConfigurationException {
    setData(getParameters());
  }

  public void reset() {
    getData(getParameters());
  }

  private void setData(final MavenRunnerParameters data) {
    data.setPomPath(pomComponent.getComponent().getText());
    data.setGoals(Strings.tokenize(goalsComponent.getComponent().getText(), " "));
    data.setProfiles(Strings.tokenize(profilesComponent.getComponent().getText(), " "));
  }

  private void getData(final MavenRunnerParameters data) {
    pomComponent.getComponent().setText(data.getPomPath());
    goalsComponent.getComponent().setText(Strings.detokenize(data.getGoals(), ' '));
    profilesComponent.getComponent().setText(Strings.detokenize(data.getProfiles(), ' '));
  }

  protected abstract MavenRunnerParameters getParameters();
}
