/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.ui.MultiLineTooltipUI;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.auth.SvnAuthenticationNotifier;

import javax.swing.*;

public class GeneralSettingsPanel implements ConfigurableUi<SvnConfiguration> {

  @NotNull private final Project myProject;

  private JPanel myMainPanel;

  private JCheckBox myUseCustomConfigurationDirectory;
  private TextFieldWithBrowseButton myConfigurationDirectoryText;
  private JButton myClearAuthButton;
  private JCheckBox myLockOnDemand;
  private JBCheckBox myWithCommandLineClient;
  private JBCheckBox myRunUnderTerminal;
  private TextFieldWithBrowseButton myCommandLineClient;
  private JPanel myCommandLineClientOptions;

  public GeneralSettingsPanel(@NotNull Project project) {
    myProject = project;

    myWithCommandLineClient.addItemListener(e -> enableCommandLineClientOptions());
    enableCommandLineClientOptions();
    myUseCustomConfigurationDirectory.addActionListener(e -> {
      boolean enabled = myUseCustomConfigurationDirectory.isSelected();
      myConfigurationDirectoryText.setEnabled(enabled);
      myConfigurationDirectoryText.setEditable(enabled);
      SvnConfiguration configuration = SvnConfiguration.getInstance(myProject);
      String path = configuration.getConfigurationDirectory();
      if (!enabled || path == null) {
        myConfigurationDirectoryText.setText(IdeaSubversionConfigurationDirectory.getPath());
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
      path = IdeaSubversionConfigurationDirectory.getPath();
    }
    myConfigurationDirectoryText.setText(path);
    myUseCustomConfigurationDirectory.setSelected(!configuration.isUseDefaultConfiguation());

    boolean enabled = myUseCustomConfigurationDirectory.isSelected();
    myConfigurationDirectoryText.setEnabled(enabled);
    myConfigurationDirectoryText.setEditable(enabled);
    myLockOnDemand.setSelected(configuration.isUpdateLockOnDemand());

    myWithCommandLineClient.setSelected(configuration.isCommandLine());
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
    if (!configuration.getUseAcceleration().equals(acceleration())) return true;
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
    boolean reloadWorkingCopies = !acceleration().equals(configuration.getUseAcceleration()) ||
                                  !StringUtil.equals(applicationSettings17.getCommandLinePath(), myCommandLineClient.getText().trim());
    configuration.setUseAcceleration(acceleration());
    configuration.setRunUnderTerminal(myRunUnderTerminal.isSelected());

    applicationSettings17.setCommandLinePath(myCommandLineClient.getText().trim());
    boolean isClientValid = vcs17.checkCommandLineVersion();
    if (isClientValid && reloadWorkingCopies) {
      vcs17.invokeRefreshSvnRoots();
      VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    }
  }

  public void enableCommandLineClientOptions() {
    UIUtil.setEnabled(myCommandLineClientOptions, myWithCommandLineClient.isSelected(), true);
  }

  private SvnConfiguration.UseAcceleration acceleration() {
    if (myWithCommandLineClient.isSelected()) return SvnConfiguration.UseAcceleration.commandLine;
    return SvnConfiguration.UseAcceleration.nothing;
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
