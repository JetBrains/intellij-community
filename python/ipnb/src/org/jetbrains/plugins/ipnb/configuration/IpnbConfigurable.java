package org.jetbrains.plugins.ipnb.configuration;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class IpnbConfigurable implements SearchableConfigurable {
  private JPanel myMainPanel;
  private JBTextField myFieldUrl;
  private TextFieldWithBrowseButton myWorkingDirField;
  @NotNull private final Project myProject;

  public IpnbConfigurable(@NotNull Project project) {
    myProject = project;
    final FileChooserDescriptor fileChooserDescriptor = FileChooserDescriptorFactory
      .createSingleFolderDescriptor();
    myWorkingDirField.addBrowseFolderListener("Select Working Directory", null, myProject, fileChooserDescriptor);
    myFieldUrl.setText(IpnbSettings.getInstance(myProject).getURL());
    myWorkingDirField.setText(IpnbSettings.getInstance(myProject).getWorkingDirectory());
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Jupyter Notebook";
  }

  @Override
  public String getHelpTopic() {
    return "reference-ipnb";
  }

  @Override
  public JComponent createComponent() {
    return myMainPanel;
  }

  @Override
  public boolean isModified() {
    final String oldUrl = IpnbSettings.getInstance(myProject).getURL();
    final String url = StringUtil.trimEnd(StringUtil.notNullize(myFieldUrl.getText()), "/");

    final String oldWorkingDirectory = StringUtil.notNullize(IpnbSettings.getInstance(myProject).getWorkingDirectory());
    final String workingDirectory = StringUtil.notNullize(myWorkingDirField.getText());
    return !url.equals(oldUrl) || !workingDirectory.equals(oldWorkingDirectory);
  }

  @Override
  public void apply() throws ConfigurationException {
    String url = StringUtil.notNullize(myFieldUrl.getText());
    url = StringUtil.trimEnd(url, "/");
    IpnbSettings.getInstance(myProject).setURL(url);
    IpnbSettings.getInstance(myProject).setWorkingDirectory(myWorkingDirField.getText());
  }

  @Override
  public void reset() {
    myFieldUrl.setText(IpnbSettings.getInstance(myProject).getURL());
    myWorkingDirField.setText(IpnbSettings.getInstance(myProject).getWorkingDirectory());
  }

  @Override
  public void disposeUIResources() {
  }

  @NotNull
  @Override
  public String getId() {
    return "IpnbConfigurable";
  }
}

