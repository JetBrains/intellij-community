// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenExecutionOptions;
import org.jetbrains.idea.maven.execution.MavenRCSettingsWatcher;
import org.jetbrains.idea.maven.execution.MavenSettingsObservable;
import org.jetbrains.idea.maven.utils.ComboBoxUtil;

import javax.swing.*;
import java.util.Arrays;
import java.util.Objects;

import static com.intellij.openapi.util.text.StringUtil.nullize;

public class MavenGeneralPanel implements PanelWithAnchor, MavenSettingsObservable {
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
  private JCheckBox tychoProjectCheckBox;
  private JTextField threadsEditor;
  private final DefaultComboBoxModel outputLevelComboModel = new DefaultComboBoxModel();
  private final DefaultComboBoxModel checksumPolicyComboModel = new DefaultComboBoxModel();
  private final DefaultComboBoxModel failPolicyComboModel = new DefaultComboBoxModel();
  private final DefaultComboBoxModel pluginUpdatePolicyComboModel = new DefaultComboBoxModel();
  private JComponent anchor;

  private JCheckBox showDialogWithAdvancedSettingsCheckBox;
  private JCheckBox useMavenConfigCheckBox;
  private JBLabel mavenConfigWarningLabel;
  private boolean isShowAdvancedSettingsCheckBox = false;
  private MavenGeneralSettings myInitialSettings;

  public MavenGeneralPanel() {
    fillOutputLevelCombobox();
    fillChecksumPolicyCombobox();
    fillFailureBehaviorCombobox();
    fillUseMavenConfigGroup();
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

  private void fillUseMavenConfigGroup() {
    mavenConfigWarningLabel.setIcon(AllIcons.General.BalloonWarning12);
    mavenConfigWarningLabel.setComponentStyle(UIUtil.ComponentStyle.SMALL);
    mavenConfigWarningLabel.setVerticalTextPosition(SwingConstants.TOP);
    mavenConfigWarningLabel.setVisible(false);
  }

  public void showCheckBoxWithAdvancedSettings() {
    isShowAdvancedSettingsCheckBox = true;
  }

  public JComponent createComponent() {
    showDialogWithAdvancedSettingsCheckBox.setVisible(isShowAdvancedSettingsCheckBox);

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
    data.setTychoProject(tychoProjectCheckBox.isSelected());
    data.setThreads(threadsEditor.getText());

    data.setShowDialogWithAdvancedSettings(showDialogWithAdvancedSettingsCheckBox.isSelected());
    data.setUseMavenConfig(useMavenConfigCheckBox.isSelected());

    data.endUpdate();

    mavenConfigWarningLabel.setVisible(useMavenConfigCheckBox.isSelected() && isModifiedNotOverridableData(data));
  }

  protected void initializeFormData(MavenGeneralSettings data, Project project) {
    myInitialSettings = data;

    checkboxWorkOffline.setSelected(data.isWorkOffline());

    mavenPathsForm.initializeFormData(data, project);

    checkboxProduceExceptionErrorMessages.setSelected(data.isPrintErrorStackTraces());
    checkboxUsePluginRegistry.setSelected(data.isUsePluginRegistry());
    checkboxRecursive.setSelected(!data.isNonRecursive());
    alwaysUpdateSnapshotsCheckBox.setSelected(data.isAlwaysUpdateSnapshots());
    tychoProjectCheckBox.setSelected(data.isTychoProject());
    threadsEditor.setText(StringUtil.notNullize(data.getThreads()));

    ComboBoxUtil.select(outputLevelComboModel, data.getOutputLevel());
    ComboBoxUtil.select(checksumPolicyComboModel, data.getChecksumPolicy());
    ComboBoxUtil.select(failPolicyComboModel, data.getFailureBehavior());
    ComboBoxUtil.select(pluginUpdatePolicyComboModel, data.getPluginUpdatePolicy());

    showDialogWithAdvancedSettingsCheckBox.setSelected(data.isShowDialogWithAdvancedSettings());
    useMavenConfigCheckBox.setSelected(data.isUseMavenConfig());
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
  public void applyTargetEnvironmentConfiguration(@NotNull Project project, @Nullable String targetName) {
    mavenPathsForm.apply(project, targetName);
  }

  @Override
  public void registerSettingsWatcher(@NotNull MavenRCSettingsWatcher watcher) {
    watcher.registerComponent("workOffline", checkboxWorkOffline);
    mavenPathsForm.registerSettingsWatcher(watcher);
    watcher.registerComponent("produceExceptionErrorMessages", checkboxProduceExceptionErrorMessages);
    watcher.registerComponent("usePluginRegistry", checkboxUsePluginRegistry);
    watcher.registerComponent("recursive", checkboxRecursive);
    watcher.registerComponent("alwaysUpdateSnapshots", alwaysUpdateSnapshotsCheckBox);
    watcher.registerComponent("threadsEditor", threadsEditor);
    watcher.registerComponent("outputLevel", outputLevelCombo);
    watcher.registerComponent("checksumPolicy", checksumPolicyCombo);
    watcher.registerComponent("failPolicy", failPolicyCombo);
    watcher.registerComponent("showDialogWithAdvancedSettings", showDialogWithAdvancedSettingsCheckBox);
    watcher.registerComponent("useMavenConfigCheckBox", useMavenConfigCheckBox);
  }

  private boolean isModifiedNotOverridableData(MavenGeneralSettings data) {
    return !Objects.equals(nullize(myInitialSettings.getThreads()), nullize(data.getThreads()))
           || !Objects.equals(myInitialSettings.getChecksumPolicy(), data.getChecksumPolicy())
           || !Objects.equals(myInitialSettings.getFailureBehavior(), data.getFailureBehavior())
           || !Objects.equals(myInitialSettings.getOutputLevel(), data.getOutputLevel())
           || !Objects.equals(myInitialSettings.isWorkOffline(), data.isWorkOffline())
           || !Objects.equals(myInitialSettings.isPrintErrorStackTraces(), data.isPrintErrorStackTraces())
           || !Objects.equals(myInitialSettings.isAlwaysUpdateSnapshots(), data.isAlwaysUpdateSnapshots())
           || !Objects.equals(myInitialSettings.isNonRecursive(), data.isNonRecursive());
  }
}
