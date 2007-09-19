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

import com.intellij.openapi.options.Configurable;
import org.apache.maven.execution.MavenExecutionRequest;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.util.ComboBoxUtil;

import javax.swing.*;

/**
 * @author Ralf Quebbemann (ralfq@codehaus.org)
 */
public abstract class MavenCoreConfigurable implements Configurable {
  private JCheckBox checkboxWorkOffline;
  private JPanel panel;
  private JComboBox comboboxOutputLevel;
  private JCheckBox checkboxProduceExceptionErrorMessages;
  private JComboBox comboboxChecksumPolicy;
  private JComboBox comboboxMultiprojectBuildFailPolicy;
  private JComboBox comboboxPluginUpdatePolicy;
  private JCheckBox checkboxUsePluginRegistry;
  private JCheckBox checkboxNonRecursive;
  private MavenPathsForm mavenPathsForm;
  private final DefaultComboBoxModel comboboxModelOutputLevel = new DefaultComboBoxModel();
  private final DefaultComboBoxModel comboboxModelChecksumPolicy = new DefaultComboBoxModel();
  private final DefaultComboBoxModel comboboxModelMultiprojectBuildFailPolicy = new DefaultComboBoxModel();
  private final DefaultComboBoxModel comboboxModelPluginUpdatePolicy = new DefaultComboBoxModel();

  protected abstract MavenCoreState getState();

  private void fillComboboxFailureBehavior() {
    ComboBoxUtil.setModel(comboboxMultiprojectBuildFailPolicy, comboboxModelMultiprojectBuildFailPolicy, new Object[][]{
      {MavenExecutionRequest.REACTOR_FAIL_FAST, "Stop at first failure"}, {MavenExecutionRequest.REACTOR_FAIL_AT_END, "Fail at the end"},
      {MavenExecutionRequest.REACTOR_FAIL_NEVER, "Never fail"}});
  }

  private void fillComboboxPluginUpdatePolicy() {
    ComboBoxUtil.setModel(comboboxPluginUpdatePolicy, comboboxModelPluginUpdatePolicy,
                          new Object[][]{{true, "Check For Updates"}, {false, "Supress Checking"}});
  }

  private void fillComboboxChecksumPolicy() {
    ComboBoxUtil.setModel(comboboxChecksumPolicy, comboboxModelChecksumPolicy, new Object[][]{{"", "No Global Policy"},
      {MavenExecutionRequest.CHECKSUM_POLICY_FAIL, "Strict (Fail)"}, {MavenExecutionRequest.CHECKSUM_POLICY_WARN, "Lax (Warn Only)"}});
  }

  private void fillComboboxOutputLevel() {
    ComboBoxUtil.setModel(comboboxOutputLevel, comboboxModelOutputLevel, new Object[][]{
      {MavenExecutionRequest.LOGGING_LEVEL_DEBUG, "Debug"}, {MavenExecutionRequest.LOGGING_LEVEL_INFO, "Info"},
      {MavenExecutionRequest.LOGGING_LEVEL_WARN, "Warn"}, {MavenExecutionRequest.LOGGING_LEVEL_ERROR, "Error"},
      {MavenExecutionRequest.LOGGING_LEVEL_FATAL, "Fatal"}, {MavenExecutionRequest.LOGGING_LEVEL_DISABLED, "Disabled"}});
  }

  public JComponent createComponent() {
    fillComboboxOutputLevel();
    fillComboboxChecksumPolicy();
    fillComboboxFailureBehavior();
    fillComboboxPluginUpdatePolicy();

    return panel;
  }

  public boolean isModified() {
    MavenCoreState formData = new MavenCoreState();
    setData(formData);
    return !formData.equals(getState());
  }

  public void apply() {
    setData(getState());
  }

  public void reset() {
    getData(getState());
  }

  private void setData(MavenCoreState data) {
    data.setWorkOffline(checkboxWorkOffline.isSelected());
    mavenPathsForm.setData(data);

    data.setProduceExceptionErrorMessages(checkboxProduceExceptionErrorMessages.isSelected());
    data.setUsePluginRegistry(checkboxUsePluginRegistry.isSelected());
    data.setNonRecursive(checkboxNonRecursive.isSelected());

    Integer level = (Integer)ComboBoxUtil.getSelectedValue(comboboxModelOutputLevel);
    if(level!=null){
      data.setOutputLevel(level);
    }
    data.setChecksumPolicy(ComboBoxUtil.getSelectedString(comboboxModelChecksumPolicy));
    data.setFailureBehavior(ComboBoxUtil.getSelectedString(comboboxModelMultiprojectBuildFailPolicy));
    Boolean policy = (Boolean)ComboBoxUtil.getSelectedValue(comboboxModelPluginUpdatePolicy);
    if(policy!=null){
      data.setPluginUpdatePolicy(policy);
    }
  }

  private void getData(MavenCoreState data) {
    checkboxWorkOffline.setSelected(data.isWorkOffline());

    mavenPathsForm.getData(data);

    checkboxProduceExceptionErrorMessages.setSelected(data.isProduceExceptionErrorMessages());
    checkboxUsePluginRegistry.setSelected(data.isUsePluginRegistry());
    checkboxNonRecursive.setSelected(data.isNonRecursive());

    ComboBoxUtil.select(comboboxModelOutputLevel, data.getOutputLevel());
    ComboBoxUtil.select(comboboxModelChecksumPolicy, data.getChecksumPolicy());
    ComboBoxUtil.select(comboboxModelMultiprojectBuildFailPolicy, data.getFailureBehavior());
    ComboBoxUtil.select(comboboxModelPluginUpdatePolicy, data.getPluginUpdatePolicy());
  }

  public void disposeUIResources() {
    mavenPathsForm.disposeUIResources();
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
}