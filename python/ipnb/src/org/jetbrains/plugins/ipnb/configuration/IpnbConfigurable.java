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
  private JPasswordField myPasswordField;
  private JBTextField myUsernameField;
  @NotNull private final Project myProject;

  public IpnbConfigurable(@NotNull Project project) {
    myProject = project;
    final FileChooserDescriptor fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
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
    final String oldWorkingDirectory = IpnbSettings.getInstance(myProject).getWorkingDirectory();
    final String oldArguments = StringUtil.notNullize(IpnbSettings.getInstance(myProject).getArguments());

    final String url = StringUtil.trimEnd(StringUtil.notNullize(myFieldUrl.getText()), "/");
    final String workingDirectory = StringUtil.notNullize(myWorkingDirField.getText());
    final String arguments = StringUtil.notNullize(myArgumentsField.getText());

    return !url.equals(oldUrl) || !workingDirectory.equals(oldWorkingDirectory) || !arguments.equals(oldArguments)
           || isCredentialsModified();
  }

  public boolean isCredentialsModified() {
    final String oldUsername = StringUtil.notNullize(IpnbSettings.getInstance(myProject).getUsername());
    final String oldPassword = IpnbSettings.getInstance(myProject).getPassword();

    final String username = StringUtil.notNullize(myUsernameField.getText());
    final String password = StringUtil.notNullize(String.valueOf(myPasswordField.getPassword()));

    return !oldUsername.equals(username) || !oldPassword.equals(password);
  }

  @Override
  public void apply() throws ConfigurationException {
    IpnbSettings.getInstance(myProject).setWorkingDirectory(myWorkingDirField.getText());
    IpnbSettings.getInstance(myProject).setArguments(myArgumentsField.getText());
    
    String url = StringUtil.notNullize(myFieldUrl.getText());
    url = StringUtil.trimEnd(url, "/");
    final boolean urlModified = !url.equals(IpnbSettings.getInstance(myProject).getURL());
    if (urlModified) {
      IpnbSettings.getInstance(myProject).setURL(url);
      IpnbConnectionManager.getInstance(myProject).shutdownKernels();
    }

    if (isCredentialsModified()) {
      IpnbSettings.getInstance(myProject).setUsername(myUsernameField.getText());
      IpnbSettings.getInstance(myProject).setPassword(String.valueOf(myPasswordField.getPassword()));
      IpnbConnectionManager.getInstance(myProject).shutdownKernels();
    }
  }

  @Override
  public void reset() {
    myFieldUrl.setText(IpnbSettings.getInstance(myProject).getURL());
    myWorkingDirField.setText(IpnbSettings.getInstance(myProject).getWorkingDirectory());
    myArgumentsField.setText(IpnbSettings.getInstance(myProject).getArguments());
    myUsernameField.setText(IpnbSettings.getInstance(myProject).getUsername());
    myPasswordField.setText(IpnbSettings.getInstance(myProject).getPassword());
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

