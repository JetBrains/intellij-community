/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.PortField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnConfigurationState;
import org.jetbrains.idea.svn.commandLine.SshTunnelRuntimeModule;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * @author Konstantin Kolosovsky.
 */
public class SshSettingsPanel implements ConfigurableUi<SvnConfiguration> {
  private JBRadioButton myPasswordChoice;
  private JBRadioButton myPrivateKeyChoice;
  private JBRadioButton mySubversionConfigChoice;
  private JPanel myMainPanel;
  private JBTextField myUserNameField;
  private JBTextField mySshTunnelField;
  private JButton myUpdateTunnelButton;
  private JBTextField mySvnSshVariableField;
  private JPanel mySubversionConfigOptions;
  private JPanel myPrivateKeyOptions;
  private JPanel myCommonOptions;
  private JBLabel mySvnSshVariableLabel;
  private JBTextField myExecutablePathTextField;
  private TextFieldWithBrowseButton myExecutablePathField;
  private PortField myPortField;
  private TextFieldWithBrowseButton myPrivateKeyPathField;

  private String mySshTunnelFromConfig;
  private final SvnConfiguration mySvnConfiguration;

  public SshSettingsPanel(@NotNull Project project) {
    mySvnConfiguration = SvnConfiguration.getInstance(project);
    init();
  }

  private void init() {
    register(myPasswordChoice, SvnConfiguration.SshConnectionType.PASSWORD);
    register(myPrivateKeyChoice, SvnConfiguration.SshConnectionType.PRIVATE_KEY);
    register(mySubversionConfigChoice, SvnConfiguration.SshConnectionType.SUBVERSION_CONFIG);

    ItemListener connectionTypeChangedListener = e -> {
      if (e.getStateChange() == ItemEvent.SELECTED) {
        enableOptions(e.getSource());
      }
    };
    myPasswordChoice.addItemListener(connectionTypeChangedListener);
    myPrivateKeyChoice.addItemListener(connectionTypeChangedListener);
    mySubversionConfigChoice.addItemListener(connectionTypeChangedListener);

    enableOptions(mySubversionConfigChoice);

    registerBrowseDialog(myExecutablePathField, SvnBundle.message("ssh.settings.browse.executable.dialog.title"));
    registerBrowseDialog(myPrivateKeyPathField, SvnBundle.message("ssh.settings.browse.private.key.dialog.title"));

    mySshTunnelField.getEmptyText().setText(SshTunnelRuntimeModule.DEFAULT_SSH_TUNNEL_VALUE);
    mySshTunnelField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        updateSshTunnelDependentValues(mySshTunnelField.getText());
      }
    });

    myUpdateTunnelButton.addActionListener(e -> {
      String tunnel = mySshTunnelField.getText();

      // remove tunnel from config in case it is null or empty
      mySvnConfiguration.setSshTunnelSetting(StringUtil.nullize(tunnel));
      setSshTunnelSetting(mySvnConfiguration.getSshTunnelSetting());
    });
  }

  private void registerBrowseDialog(@NotNull TextFieldWithBrowseButton component, @NotNull String dialogTitle) {
    component.addBrowseFolderListener(dialogTitle, null, mySvnConfiguration.getProject(),
                                      FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
  }

  private void enableOptions(Object source) {
    UIUtil.setEnabled(myCommonOptions, !mySubversionConfigChoice.equals(source), true);
    UIUtil.setEnabled(myPrivateKeyOptions, myPrivateKeyChoice.equals(source), true);
    UIUtil.setEnabled(mySubversionConfigOptions, mySubversionConfigChoice.equals(source), true);

    mySvnSshVariableField.setEditable(false);
    setUpdateTunnelButtonEnabled(mySshTunnelField.getText());
  }

  @Override
  public void reset(@NotNull SvnConfiguration settings) {
    SvnConfigurationState state = settings.getState();

    setConnectionChoice(state.sshConnectionType);
    myExecutablePathField.setText(state.sshExecutablePath);
    myUserNameField.setText(state.sshUserName);
    myPortField.setNumber(state.sshPort);
    myPrivateKeyPathField.setText(state.sshPrivateKeyPath);

    setSshTunnelSetting(settings.getSshTunnelSetting());
  }

  @Override
  public boolean isModified(@NotNull SvnConfiguration settings) {
    SvnConfigurationState state = settings.getState();

    return !state.sshConnectionType.equals(getConnectionChoice()) ||
           !StringUtil.equals(state.sshExecutablePath, myExecutablePathField.getText()) ||
           !StringUtil.equals(state.sshUserName, myUserNameField.getText()) ||
           state.sshPort != myPortField.getNumber() ||
           !StringUtil.equals(state.sshPrivateKeyPath, myPrivateKeyPathField.getText());
  }

  @Override
  public void apply(@NotNull SvnConfiguration settings) {
    SvnConfigurationState state = settings.getState();

    state.sshConnectionType = getConnectionChoice();
    state.sshExecutablePath = myExecutablePathField.getText();
    state.sshUserName = myUserNameField.getText();
    state.sshPort = myPortField.getNumber();
    state.sshPrivateKeyPath = myPrivateKeyPathField.getText();
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myMainPanel;
  }

  private void setConnectionChoice(@NotNull SvnConfiguration.SshConnectionType value) {
    setSelected(myPasswordChoice, value);
    setSelected(myPrivateKeyChoice, value);
    setSelected(mySubversionConfigChoice, value);
  }

  @NotNull
  private SvnConfiguration.SshConnectionType getConnectionChoice() {
    JBRadioButton selected = myPasswordChoice.isSelected()
                             ? myPasswordChoice
                             : myPrivateKeyChoice.isSelected()
                               ? myPrivateKeyChoice
                               : mySubversionConfigChoice.isSelected() ? mySubversionConfigChoice : null;

    assert selected != null;

    //noinspection ConstantConditions
    return (SvnConfiguration.SshConnectionType)selected.getClientProperty("value");
  }

  private void setSshTunnelSetting(@NotNull String tunnelSetting) {
    mySshTunnelFromConfig = tunnelSetting;
    mySshTunnelField.setText(tunnelSetting);

    updateSshTunnelDependentValues(tunnelSetting);
  }

  private void updateSshTunnelDependentValues(@Nullable String tunnelSetting) {
    String svnSshVariableName = SshTunnelRuntimeModule.getSvnSshVariableName(
      !StringUtil.isEmpty(tunnelSetting) ? tunnelSetting : SshTunnelRuntimeModule.DEFAULT_SSH_TUNNEL_VALUE);
    String svnSshVariableValue = StringUtil.notNullize(EnvironmentUtil.getValue(svnSshVariableName));

    mySvnSshVariableLabel.setText(svnSshVariableName + ":");
    mySvnSshVariableField.setText(svnSshVariableValue);

    boolean isSvnSshVariableNameInTunnel = !StringUtil.isEmpty(svnSshVariableName);
    mySvnSshVariableLabel.setVisible(isSvnSshVariableNameInTunnel);
    mySvnSshVariableField.setVisible(isSvnSshVariableNameInTunnel);

    myExecutablePathTextField.getEmptyText().setText(SshTunnelRuntimeModule.getExecutablePath(tunnelSetting));

    setUpdateTunnelButtonEnabled(tunnelSetting);
  }

  private void setUpdateTunnelButtonEnabled(@Nullable String tunnelSetting) {
    myUpdateTunnelButton.setEnabled(!StringUtil.equals(tunnelSetting, mySshTunnelFromConfig));
  }

  private static void register(@NotNull JBRadioButton choice, @NotNull SvnConfiguration.SshConnectionType value) {
    choice.putClientProperty("value", value);
  }

  private static void setSelected(@NotNull JBRadioButton choice, @NotNull SvnConfiguration.SshConnectionType value) {
    choice.setSelected(value.equals(choice.getClientProperty("value")));
  }

  private void createUIComponents() {
    myExecutablePathTextField = new JBTextField();
    myExecutablePathField = new TextFieldWithBrowseButton(myExecutablePathTextField);
  }
}
