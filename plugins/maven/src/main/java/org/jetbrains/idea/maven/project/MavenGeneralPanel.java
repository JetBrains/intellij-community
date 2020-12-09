// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project;

import com.intellij.CommonBundle;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenExecutionOptions;
import org.jetbrains.idea.maven.utils.ComboBoxUtil;

import javax.swing.*;
import java.util.Arrays;

public class MavenGeneralPanel implements PanelWithAnchor {
  private JCheckBox checkboxWorkOffline;
  private JPanel panel;
  private JComboBox outputLevelCombo;
  private JCheckBox checkboxProduceExceptionErrorMessages;
  private JComboBox checksumPolicyCombo;
  private JComboBox failPolicyCombo;
  private JCheckBox checkboxUsePluginRegistry;
  private JCheckBox checkboxRecursive;
  private MavenEnvironmentForm mavenPathsForm;
  private JBLabel myMultiProjectBuildFailPolicyLabel;
  private JCheckBox alwaysUpdateSnapshotsCheckBox;
  private JTextField threadsEditor;
  private final DefaultComboBoxModel outputLevelComboModel = new DefaultComboBoxModel();
  private final DefaultComboBoxModel checksumPolicyComboModel = new DefaultComboBoxModel();
  private final DefaultComboBoxModel failPolicyComboModel = new DefaultComboBoxModel();
  private final DefaultComboBoxModel pluginUpdatePolicyComboModel = new DefaultComboBoxModel();
  private JComponent anchor;

  public MavenGeneralPanel() {
    fillOutputLevelCombobox();
    fillChecksumPolicyCombobox();
    fillFailureBehaviorCombobox();
    setAnchor(myMultiProjectBuildFailPolicyLabel);
  }

  private void fillOutputLevelCombobox() {
    ComboBoxUtil.setModel(outputLevelCombo, outputLevelComboModel,
                          Arrays.asList(MavenExecutionOptions.LoggingLevel.values()),
                          each -> Pair.create(each.getDisplayString(), each));
  }

  private void fillFailureBehaviorCombobox() {
    ComboBoxUtil.setModel(failPolicyCombo, failPolicyComboModel,
                          Arrays.asList(MavenExecutionOptions.FailureMode.values()),
                          each -> Pair.create(each.getDisplayString(), each));
  }

  private void fillChecksumPolicyCombobox() {
    ComboBoxUtil.setModel(checksumPolicyCombo, checksumPolicyComboModel,
                          Arrays.asList(MavenExecutionOptions.ChecksumPolicy.values()),
                          each -> Pair.create(each.getDisplayString(), each));
  }

  public JComponent createComponent() {
    mavenPathsForm.createComponent(); // have to initialize all listeners
    return panel;
  }

  protected void setData(MavenGeneralSettings data) {
    data.beginUpdate();

    data.setWorkOffline(checkboxWorkOffline.isSelected());
    mavenPathsForm.setData(data);

    data.setPrintErrorStackTraces(checkboxProduceExceptionErrorMessages.isSelected());
    data.setUsePluginRegistry(checkboxUsePluginRegistry.isSelected());
    data.setNonRecursive(!checkboxRecursive.isSelected());

    data.setOutputLevel((MavenExecutionOptions.LoggingLevel)ComboBoxUtil.getSelectedValue(outputLevelComboModel));
    data.setChecksumPolicy((MavenExecutionOptions.ChecksumPolicy)ComboBoxUtil.getSelectedValue(checksumPolicyComboModel));
    data.setFailureBehavior((MavenExecutionOptions.FailureMode)ComboBoxUtil.getSelectedValue(failPolicyComboModel));
    data.setPluginUpdatePolicy((MavenExecutionOptions.PluginUpdatePolicy)ComboBoxUtil.getSelectedValue(pluginUpdatePolicyComboModel));
    data.setAlwaysUpdateSnapshots(alwaysUpdateSnapshotsCheckBox.isSelected());
    data.setThreads(threadsEditor.getText());

    data.endUpdate();
  }

  protected void getData(MavenGeneralSettings data) {
    checkboxWorkOffline.setSelected(data.isWorkOffline());

    mavenPathsForm.getData(data);

    checkboxProduceExceptionErrorMessages.setSelected(data.isPrintErrorStackTraces());
    checkboxUsePluginRegistry.setSelected(data.isUsePluginRegistry());
    checkboxRecursive.setSelected(!data.isNonRecursive());
    alwaysUpdateSnapshotsCheckBox.setSelected(data.isAlwaysUpdateSnapshots());
    threadsEditor.setText(StringUtil.notNullize(data.getThreads()));

    ComboBoxUtil.select(outputLevelComboModel, data.getOutputLevel());
    ComboBoxUtil.select(checksumPolicyComboModel, data.getChecksumPolicy());
    ComboBoxUtil.select(failPolicyComboModel, data.getFailureBehavior());
    ComboBoxUtil.select(pluginUpdatePolicyComboModel, data.getPluginUpdatePolicy());
  }

  @Nls
  public String getDisplayName() {
    return CommonBundle.message("tab.title.general");
  }

  @Override
  public JComponent getAnchor() {
    return anchor;
  }

  @Override
  public void setAnchor(JComponent anchor) {
    this.anchor = anchor;
    myMultiProjectBuildFailPolicyLabel.setAnchor(anchor);
    mavenPathsForm.setAnchor(anchor);
  }

  @ApiStatus.Internal
  public void applyTargetEnvironmentConfiguration(@Nullable String targetName) {
    mavenPathsForm.apply(targetName);
  }
}
