// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.ui.MultiLineTooltipUI;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.auth.SvnAuthenticationNotifier;

import javax.swing.*;

import static org.jetbrains.idea.svn.SvnUtil.USER_CONFIGURATION_PATH;

public class GeneralSettingsPanel implements ConfigurableUi<SvnConfiguration> {

  @NotNull private final Project myProject;

  private JPanel myMainPanel;

  private JCheckBox myUseCustomConfigurationDirectory;
  private TextFieldWithBrowseButton myConfigurationDirectoryText;
  private JButton myClearAuthButton;
  private JCheckBox myLockOnDemand;
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
    myCommandLineClient.addBrowseFolderListener("Subversion", "Select path to Subversion executable (1.7+)", project,
                                                FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
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
    if (configuration.isUseDefaultConfiguation() || path == null) {
      path = USER_CONFIGURATION_PATH.getValue().toString();
    }
    myConfigurationDirectoryText.setText(path);
    myUseCustomConfigurationDirectory.setSelected(!configuration.isUseDefaultConfiguation());

    boolean enabled = myUseCustomConfigurationDirectory.isSelected();
    myConfigurationDirectoryText.setEnabled(enabled);
    myConfigurationDirectoryText.setEditable(enabled);
    myLockOnDemand.setSelected(configuration.isUpdateLockOnDemand());

    myRunUnderTerminal.setSelected(configuration.isRunUnderTerminal());
    final SvnApplicationSettings applicationSettings17 = SvnApplicationSettings.getInstance();
    myCommandLineClient.setText(applicationSettings17.getCommandLinePath());
  }

  @Override
  public boolean isModified(@NotNull SvnConfiguration configuration) {
    if (configuration.isUseDefaultConfiguation() == myUseCustomConfigurationDirectory.isSelected()) {
      return true;
    }
    if (configuration.isUpdateLockOnDemand() != myLockOnDemand.isSelected()) {
      return true;
    }
    if (configuration.isRunUnderTerminal() != myRunUnderTerminal.isSelected()) return true;
    final SvnApplicationSettings applicationSettings17 = SvnApplicationSettings.getInstance();
    if (!Comparing.equal(applicationSettings17.getCommandLinePath(), myCommandLineClient.getText().trim())) return true;
    if (!configuration.getConfigurationDirectory().equals(myConfigurationDirectoryText.getText().trim())) return true;

    return false;
  }

  @Override
  public void apply(@NotNull SvnConfiguration configuration) {
    configuration.setConfigurationDirParameters(!myUseCustomConfigurationDirectory.isSelected(), myConfigurationDirectoryText.getText());

    final SvnVcs vcs17 = SvnVcs.getInstance(myProject);
    configuration.setUpdateLockOnDemand(myLockOnDemand.isSelected());

    final SvnApplicationSettings applicationSettings17 = SvnApplicationSettings.getInstance();
    boolean reloadWorkingCopies = !StringUtil.equals(applicationSettings17.getCommandLinePath(), myCommandLineClient.getText().trim());
    configuration.setRunUnderTerminal(myRunUnderTerminal.isSelected());

    applicationSettings17.setCommandLinePath(myCommandLineClient.getText().trim());
    boolean isClientValid = vcs17.checkCommandLineVersion();
    if (isClientValid && reloadWorkingCopies) {
      vcs17.invokeRefreshSvnRoots();
      VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    }
  }

  private void createUIComponents() {
    myLockOnDemand = new JCheckBox() {
      @Override
      public JToolTip createToolTip() {
        JToolTip toolTip = new JToolTip() {{
          setUI(new MultiLineTooltipUI());
        }};
        toolTip.setComponent(this);
        return toolTip;
      }
    };
  }
}
