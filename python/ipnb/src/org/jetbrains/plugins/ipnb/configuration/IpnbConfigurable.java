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
  private JBTextField myArgumentsField;
  @NotNull private final Project myProject;

  public IpnbConfigurable(@NotNull Project project) {
    myProject = project;
    final FileChooserDescriptor fileChooserDescriptor = FileChooserDescriptorFactory
      .createSingleFolderDescriptor();
    myWorkingDirField.addBrowseFolderListener("Select Working Directory", null, myProject, fileChooserDescriptor);
    myFieldUrl.setText(IpnbSettings.getInstance(myProject).getURL());
    myWorkingDirField.setText(IpnbSettings.getInstance(myProject).getWorkingDirectory());
    myArgumentsField.setText(IpnbSettings.getInstance(myProject).getArguments());
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
    final String oldWorkingDirectory = StringUtil.notNullize(IpnbSettings.getInstance(myProject).getWorkingDirectory());
    final String oldArguments = StringUtil.notNullize(IpnbSettings.getInstance(myProject).getArguments());

    final String url = StringUtil.trimEnd(StringUtil.notNullize(myFieldUrl.getText()), "/");
    final String workingDirectory = StringUtil.notNullize(myWorkingDirField.getText());
    final String arguments = StringUtil.notNullize(myArgumentsField.getText());

    return !url.equals(oldUrl) || !workingDirectory.equals(oldWorkingDirectory) || !arguments.equals(oldArguments);
  }

  @Override
  public void apply() throws ConfigurationException {
    String url = StringUtil.notNullize(myFieldUrl.getText());
    url = StringUtil.trimEnd(url, "/");
    IpnbSettings.getInstance(myProject).setURL(url);
    IpnbSettings.getInstance(myProject).setWorkingDirectory(myWorkingDirField.getText());
    IpnbSettings.getInstance(myProject).setArguments(myArgumentsField.getText());
  }

  @Override
  public void reset() {
    myFieldUrl.setText(IpnbSettings.getInstance(myProject).getURL());
    myWorkingDirField.setText(IpnbSettings.getInstance(myProject).getWorkingDirectory());
    myArgumentsField.setText(IpnbSettings.getInstance(myProject).getArguments());
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

