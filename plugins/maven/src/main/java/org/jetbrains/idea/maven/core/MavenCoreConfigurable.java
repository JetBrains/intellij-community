/* ==========================================================================
 * Copyright 2006 Mevenide Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * =========================================================================
 */

package org.jetbrains.idea.maven.core;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.apache.maven.execution.MavenExecutionRequest;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.util.ComboBoxUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author Ralf Quebbemann (ralfq@codehaus.org)
 */
public class MavenCoreConfigurable implements Configurable {
  private JCheckBox checkboxWorkOffline;
  private JPanel panel;
  private JComboBox comboboxOutputLevel;
  private JCheckBox checkboxProduceExceptionErrorMessages;
  private JComboBox comboboxChecksumPolicy;
  private JComboBox comboboxMultiprojectBuildFailPolicy;
  private JComboBox comboboxPluginUpdatePolicy;
  private JCheckBox checkboxUsePluginRegistry;
  private JCheckBox checkboxNonRecursive;
  private TextFieldWithBrowseButton mavenSettingsFileComponent;
  private TextFieldWithBrowseButton localRepositoryComponent;
  private final DefaultComboBoxModel comboboxModelOutputLevel = new DefaultComboBoxModel();
  private final DefaultComboBoxModel comboboxModelChecksumPolicy = new DefaultComboBoxModel();
  private final DefaultComboBoxModel comboboxModelMultiprojectBuildFailPolicy = new DefaultComboBoxModel();
  private final DefaultComboBoxModel comboboxModelPluginUpdatePolicy = new DefaultComboBoxModel();

  private final MavenCore myMavenCore;

  public MavenCoreConfigurable(MavenCore mavenCore) {
    myMavenCore = mavenCore;

    fillComboboxOutputLevel();
    fillComboboxChecksumPolicy();
    fillComboboxFailureBehavior();
    fillComboboxPluginUpdatePolicy();

    mavenSettingsFileComponent.addBrowseFolderListener(CoreBundle.message("maven.select.maven.settings.file"), "", null,
                                                       new FileChooserDescriptor(true, false, false, false, false, false));

    localRepositoryComponent.addBrowseFolderListener(CoreBundle.message("maven.select.local.repository"), "", null,
                                                     new FileChooserDescriptor(false, true, false, false, false, false));
  }

  private void fillComboboxFailureBehavior() {
    ComboBoxUtil.addToModel(comboboxModelMultiprojectBuildFailPolicy, new Object[][]{
      {MavenExecutionRequest.REACTOR_FAIL_FAST, "Stop at first failure"}, {MavenExecutionRequest.REACTOR_FAIL_AT_END, "Fail at the end"},
      {MavenExecutionRequest.REACTOR_FAIL_NEVER, "Never fail"}});

    comboboxMultiprojectBuildFailPolicy.setModel(comboboxModelMultiprojectBuildFailPolicy);
  }

  private void fillComboboxPluginUpdatePolicy() {
    ComboBoxUtil.addToModel(comboboxModelPluginUpdatePolicy,
                            new Object[][]{{"null", "No Global Policy"}, {"true", "Check For Updates"}, {"false", "Supress Checking"}});

    comboboxPluginUpdatePolicy.setModel(comboboxModelPluginUpdatePolicy);
  }

  private void fillComboboxChecksumPolicy() {
    ComboBoxUtil.addToModel(comboboxModelChecksumPolicy, new Object[][]{{"", "No Global Policy"},
      {MavenExecutionRequest.CHECKSUM_POLICY_FAIL, "Strict (Fail)"}, {MavenExecutionRequest.CHECKSUM_POLICY_WARN, "Lax (Warn Only)"}});

    comboboxChecksumPolicy.setModel(comboboxModelChecksumPolicy);
  }

  private void fillComboboxOutputLevel() {
    ComboBoxUtil.addToModel(comboboxModelOutputLevel, new Object[][]{{MavenExecutionRequest.LOGGING_LEVEL_DEBUG, "Debug"},
      {MavenExecutionRequest.LOGGING_LEVEL_INFO, "Info"}, {MavenExecutionRequest.LOGGING_LEVEL_WARN, "Warn"},
      {MavenExecutionRequest.LOGGING_LEVEL_ERROR, "Error"}, {MavenExecutionRequest.LOGGING_LEVEL_FATAL, "Fatal"},
      {MavenExecutionRequest.LOGGING_LEVEL_DISABLED, "Disabled"}});

    comboboxOutputLevel.setModel(comboboxModelOutputLevel);
  }

  JComponent getRootComponent() {
    return panel;
  }

  public JComponent createComponent() {
    return getRootComponent();
  }

  public boolean isModified() {
    MavenCoreState formData = new MavenCoreState();
    setData(formData);
    return !formData.equals(myMavenCore.getState());
  }

  public void apply() {
    setData(myMavenCore.getState());
  }

  public void reset() {
    getData(myMavenCore.getState());
  }

  private void setData(MavenCoreState data) {
    data.setWorkOffline(checkboxWorkOffline.isSelected());
    data.setMavenSettingsFile(mavenSettingsFileComponent.getText().trim());
    data.setLocalRepository(localRepositoryComponent.getText());
    data.setProduceExceptionErrorMessages(checkboxProduceExceptionErrorMessages.isSelected());
    data.setUsePluginRegistry(checkboxUsePluginRegistry.isSelected());
    data.setNonRecursive(checkboxNonRecursive.isSelected());

    data.setOutputLevelString(ComboBoxUtil.getSelectedString(comboboxModelOutputLevel));
    data.setChecksumPolicy(ComboBoxUtil.getSelectedString(comboboxModelChecksumPolicy));
    data.setFailureBehavior(ComboBoxUtil.getSelectedString(comboboxModelMultiprojectBuildFailPolicy));
    data.setPluginUpdatePolicyString(ComboBoxUtil.getSelectedString(comboboxModelPluginUpdatePolicy));
  }

  private void getData(MavenCoreState data) {
    checkboxWorkOffline.setSelected(data.isWorkOffline());
    mavenSettingsFileComponent.setText(data.getMavenSettingsFile());
    localRepositoryComponent.setText(data.getLocalRepository());
    checkboxProduceExceptionErrorMessages.setSelected(data.isProduceExceptionErrorMessages());
    checkboxUsePluginRegistry.setSelected(data.isUsePluginRegistry());
    checkboxNonRecursive.setSelected(data.isNonRecursive());

    ComboBoxUtil.select(comboboxModelOutputLevel, data.getOutputLevelString());
    ComboBoxUtil.select(comboboxModelChecksumPolicy, data.getChecksumPolicy());
    ComboBoxUtil.select(comboboxModelMultiprojectBuildFailPolicy, data.getFailureBehavior());
    ComboBoxUtil.select(comboboxModelPluginUpdatePolicy, data.getPluginUpdatePolicyString());
  }

  public void disposeUIResources() {
  }

  @Nls
  public String getDisplayName() {
    return CoreBundle.message("maven.tab.general");
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

  {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
    $$$setupUI$$$();
  }

  /**
   * Method generated by IntelliJ IDEA GUI Designer
   * >>> IMPORTANT!! <<<
   * DO NOT edit this method OR call it in your code!
   *
   * @noinspection ALL
   */
  private void $$$setupUI$$$() {
    panel = new JPanel();
    panel.setLayout(new GridLayoutManager(13, 2, new Insets(8, 8, 8, 8), -1, -1));
    final JLabel label1 = new JLabel();
    label1.setText("Output Level");
    label1.setDisplayedMnemonic('L');
    label1.setDisplayedMnemonicIndex(7);
    panel.add(label1, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                          GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(66, 23),
                                          null, 0, false));
    comboboxOutputLevel = new JComboBox();
    panel.add(comboboxOutputLevel, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                       GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                       new Dimension(34, 23), null, 0, false));
    final JLabel label2 = new JLabel();
    label2.setText("Checksum Policy");
    label2.setDisplayedMnemonic('C');
    label2.setDisplayedMnemonicIndex(0);
    panel.add(label2, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                          GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    comboboxChecksumPolicy = new JComboBox();
    panel.add(comboboxChecksumPolicy, new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                          GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                          null, 0, false));
    final JLabel label3 = new JLabel();
    label3.setText("Multiproject Build Fail Policy");
    label3.setDisplayedMnemonic('F');
    label3.setDisplayedMnemonicIndex(19);
    panel.add(label3, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                          GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    comboboxMultiprojectBuildFailPolicy = new JComboBox();
    panel.add(comboboxMultiprojectBuildFailPolicy, new GridConstraints(6, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                       GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JLabel label4 = new JLabel();
    label4.setText("Plugin Update Policy");
    label4.setDisplayedMnemonic('U');
    label4.setDisplayedMnemonicIndex(7);
    panel.add(label4, new GridConstraints(7, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                          GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    comboboxPluginUpdatePolicy = new JComboBox();
    panel.add(comboboxPluginUpdatePolicy, new GridConstraints(7, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                              GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                              null, null, 0, false));
    checkboxWorkOffline = new JCheckBox();
    checkboxWorkOffline.setText("Work Offline");
    checkboxWorkOffline.setMnemonic('O');
    checkboxWorkOffline.setDisplayedMnemonicIndex(5);
    panel.add(checkboxWorkOffline, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    checkboxUsePluginRegistry = new JCheckBox();
    checkboxUsePluginRegistry.setText("Use Plugin Registry");
    checkboxUsePluginRegistry.setMnemonic('P');
    checkboxUsePluginRegistry.setDisplayedMnemonicIndex(4);
    panel.add(checkboxUsePluginRegistry, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    checkboxNonRecursive = new JCheckBox();
    checkboxNonRecursive.setText("Non Recursive");
    checkboxNonRecursive.setMnemonic('N');
    checkboxNonRecursive.setDisplayedMnemonicIndex(0);
    panel.add(checkboxNonRecursive, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    checkboxProduceExceptionErrorMessages = new JCheckBox();
    checkboxProduceExceptionErrorMessages.setText("Produce Exception Error Messages");
    checkboxProduceExceptionErrorMessages.setMnemonic('E');
    checkboxProduceExceptionErrorMessages.setDisplayedMnemonicIndex(8);
    panel.add(checkboxProduceExceptionErrorMessages, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints
                                                                           .SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                                                                 null, null, 0, false));
    final JLabel label5 = new JLabel();
    label5.setText("Maven settings file");
    label5.setDisplayedMnemonic('S');
    label5.setDisplayedMnemonicIndex(6);
    panel.add(label5, new GridConstraints(8, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                          GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    mavenSettingsFileComponent = new TextFieldWithBrowseButton();
    panel.add(mavenSettingsFileComponent, new GridConstraints(9, 0, 1, 2, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_HORIZONTAL,
                                                              GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                              null, null, 0, false));
    final JLabel label6 = new JLabel();
    label6.setText("Local Repository");
    label6.setDisplayedMnemonic('R');
    label6.setDisplayedMnemonicIndex(6);
    panel.add(label6, new GridConstraints(10, 0, 1, 1, GridConstraints.ANCHOR_NORTHWEST, GridConstraints.FILL_NONE,
                                          GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    localRepositoryComponent = new TextFieldWithBrowseButton();
    panel.add(localRepositoryComponent, new GridConstraints(11, 0, 1, 2, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_HORIZONTAL,
                                                            GridConstraints.SIZEPOLICY_FIXED,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            null, null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    panel.add(spacer1, new GridConstraints(12, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                           GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    label1.setLabelFor(comboboxOutputLevel);
    label2.setLabelFor(comboboxChecksumPolicy);
    label3.setLabelFor(comboboxMultiprojectBuildFailPolicy);
    label4.setLabelFor(comboboxPluginUpdatePolicy);
  }

  /**
   * @noinspection ALL
   */
  public JComponent $$$getRootComponent$$$() {
    return panel;
  }
}
