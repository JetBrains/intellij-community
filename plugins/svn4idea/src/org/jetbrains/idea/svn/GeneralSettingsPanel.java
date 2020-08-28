// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.auth.SvnAuthenticationNotifier;

import javax.swing.*;
import java.util.Objects;

import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.SvnUtil.USER_CONFIGURATION_PATH;

public class GeneralSettingsPanel implements ConfigurableUi<SvnConfiguration>, Disposable {

  @NotNull private final Project myProject;

  private JPanel myMainPanel;

  private JCheckBox myUseCustomConfigurationDirectory;
  private TextFieldWithBrowseButton myConfigurationDirectoryText;
  private JButton myClearAuthButton;
  private JBCheckBox myRunUnderTerminal;
  private TextFieldWithBrowseButton myCommandLineClient;

  public GeneralSettingsPanel(@NotNull Project project) {
    myProject = project;

    myUseCustomConfigurationDirectory.addActionListener(e -> {
      boolean enabled = myUseCustomConfigurationDirectory.isSelected();
      myConfigurationDirectoryText.setEnabled(enabled);
      myConfigurationDirectoryText.setEditable(enabled);
      SvnConfiguration configuration = SvnConfiguration.getInstance(myProject);
      String path = configuration.getConfigurationDirectory();
      if (!enabled || path == null) {
        myConfigurationDirectoryText.setText(USER_CONFIGURATION_PATH.getValue().toString());
      }
      else {
        myConfigurationDirectoryText.setText(path);
      }
    });
    myCommandLineClient.addBrowseFolderListener(
      message("dialog.title.select.path.to.subversion.executable"),
      message("label.select.path.to.subversion.executable"),
      project,
      FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
    );
    myClearAuthButton.addActionListener(
      e -> SvnAuthenticationNotifier.clearAuthenticationCache(myProject, myMainPanel, myConfigurationDirectoryText.getText()));
    myConfigurationDirectoryText.addActionListener(e -> {
      @NonNls String path = myConfigurationDirectoryText.getText().trim();
      SvnConfigurable.selectConfigurationDirectory(path, s -> myConfigurationDirectoryText.setText(s), myProject, myMainPanel);
    });
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myMainPanel;
  }

  @Override
  public void reset(@NotNull SvnConfiguration configuration) {
    String path = configuration.getConfigurationDirectory();
    if (configuration.isUseDefaultConfiguration() || path == null) {
      path = USER_CONFIGURATION_PATH.getValue().toString();
    }
    myConfigurationDirectoryText.setText(path);
    myUseCustomConfigurationDirectory.setSelected(!configuration.isUseDefaultConfiguration());

    boolean enabled = myUseCustomConfigurationDirectory.isSelected();
    myConfigurationDirectoryText.setEnabled(enabled);
    myConfigurationDirectoryText.setEditable(enabled);

    myRunUnderTerminal.setSelected(configuration.isRunUnderTerminal());
    final SvnApplicationSettings applicationSettings17 = SvnApplicationSettings.getInstance();
    myCommandLineClient.setText(applicationSettings17.getCommandLinePath());
  }

  @Override
  public boolean isModified(@NotNull SvnConfiguration configuration) {
    if (configuration.isUseDefaultConfiguration() == myUseCustomConfigurationDirectory.isSelected()) {
      return true;
    }
    if (configuration.isRunUnderTerminal() != myRunUnderTerminal.isSelected()) return true;
    final SvnApplicationSettings applicationSettings17 = SvnApplicationSettings.getInstance();
    if (!Objects.equals(applicationSettings17.getCommandLinePath(), myCommandLineClient.getText().trim())) return true;
    if (!configuration.getConfigurationDirectory().equals(myConfigurationDirectoryText.getText().trim())) return true;

    return false;
  }

  @Override
  public void apply(@NotNull SvnConfiguration configuration) {
    configuration.setConfigurationDirParameters(!myUseCustomConfigurationDirectory.isSelected(), myConfigurationDirectoryText.getText());

    final SvnVcs vcs17 = SvnVcs.getInstance(myProject);

    final SvnApplicationSettings applicationSettings17 = SvnApplicationSettings.getInstance();
    boolean reloadWorkingCopies = !StringUtil.equals(applicationSettings17.getCommandLinePath(), myCommandLineClient.getText().trim());
    configuration.setRunUnderTerminal(myRunUnderTerminal.isSelected());

    applicationSettings17.setCommandLinePath(myCommandLineClient.getText().trim());
    boolean isClientValid = vcs17.checkCommandLineVersion();
    if (!myProject.isDefault() && isClientValid && reloadWorkingCopies) {
      vcs17.invokeRefreshSvnRoots();
      VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    }
  }

  @Override
  public void dispose() {
  }

  private void createUIComponents() {
    myCommandLineClient = new TextFieldWithBrowseButton(null, this);
    myConfigurationDirectoryText = new TextFieldWithBrowseButton(null, this);
  }
}
