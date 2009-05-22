package org.jetbrains.idea.maven.runner;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.Strings;
import org.jetbrains.idea.maven.utils.MavenConstants;

import javax.swing.*;

/**
 * @author Vladislav.Kaznacheev
 */
public abstract class MavenRunnerParametersConfigurable implements Configurable {
  private JPanel panel;
  protected LabeledComponent<TextFieldWithBrowseButton> workingDirComponent;
  protected LabeledComponent<JTextField> goalsComponent;
  private LabeledComponent<JTextField> profilesComponent;

  public MavenRunnerParametersConfigurable() {
    workingDirComponent.getComponent().addBrowseFolderListener(
      RunnerBundle.message("maven.select.maven.project.file"), "", null,
      new FileChooserDescriptor(false, true, false, false, false, false) {
        @Override
        public boolean isFileSelectable(VirtualFile file) {
          if (!super.isFileSelectable(file)) return false;
          return file.findChild(MavenConstants.POM_XML) != null;
        }
      });
  }

  public JComponent createComponent() {
    return panel;
  }

  public void disposeUIResources() {
  }

  public String getDisplayName() {
    return RunnerBundle.message("maven.runner.parameters.title");
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
    data.setWorkingDirPath(workingDirComponent.getComponent().getText());
    data.setGoals(Strings.tokenize(goalsComponent.getComponent().getText(), " "));
    data.setProfiles(Strings.tokenize(profilesComponent.getComponent().getText(), " "));
  }

  private void getData(final MavenRunnerParameters data) {
    workingDirComponent.getComponent().setText(data.getWorkingDirPath());
    goalsComponent.getComponent().setText(Strings.detokenize(data.getGoals(), ' '));
    profilesComponent.getComponent().setText(Strings.detokenize(data.getProfiles(), ' '));
  }

  protected abstract MavenRunnerParameters getParameters();
}
