// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.CommonBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.idea.maven.execution.MavenExecutionOptions;

import javax.swing.JComponent;
import java.util.Objects;

import static com.intellij.openapi.util.text.StringUtil.nullize;

@ApiStatus.Internal
public class MavenGeneralPanel {
  private final MavenEnvironmentForm mavenPathsForm = new MavenEnvironmentForm();
  private final MavenGeneralUi ui = new MavenGeneralUi(mavenPathsForm);

  private boolean isShowAdvancedSettingsCheckBox = false;
  private MavenGeneralSettings myInitialSettings;

  public MavenGeneralPanel() {
  }

  public void showCheckBoxWithAdvancedSettings() {
    isShowAdvancedSettingsCheckBox = true;
  }

  public JComponent createComponent() {
    ui.showDialogWithAdvancedSettingsCheckBox.setVisible(isShowAdvancedSettingsCheckBox);

    return ui.panel;
  }

  protected void setData(MavenGeneralSettings data) {
    data.beginUpdate();
    data.setWorkOffline(ui.checkboxWorkOffline.isSelected());
    mavenPathsForm.setData(data);

    data.setPrintErrorStackTraces(ui.checkboxProduceExceptionErrorMessages.isSelected());
    data.setNonRecursive(!ui.checkboxRecursive.isSelected());

    data.setOutputLevel((MavenExecutionOptions.LoggingLevel)ui.outputLevelCombo.getSelectedItem());
    data.setChecksumPolicy((MavenExecutionOptions.ChecksumPolicy)ui.checksumPolicyCombo.getSelectedItem());
    data.setFailureBehavior((MavenExecutionOptions.FailureMode)ui.failPolicyCombo.getSelectedItem());
    data.setAlwaysUpdateSnapshots(ui.alwaysUpdateSnapshotsCheckBox.isSelected());
    data.setThreads(ui.threadsEditor.getText());

    data.setShowDialogWithAdvancedSettings(ui.showDialogWithAdvancedSettingsCheckBox.isSelected());
    data.setUseMavenConfig(ui.useMavenConfigCheckBox.isSelected());

    data.endUpdate();

    ui.setMavenConfigWarningVisible(ui.useMavenConfigCheckBox.isSelected() && isModifiedNotOverridableData(data));
  }

  @VisibleForTesting
  public void initializeFormData(MavenGeneralSettings data, Project project) {
    myInitialSettings = data;

    ui.checkboxWorkOffline.setSelected(data.isWorkOffline());

    mavenPathsForm.initializeFormData(data, project);

    ui.checkboxProduceExceptionErrorMessages.setSelected(data.isPrintErrorStackTraces());
    ui.checkboxRecursive.setSelected(!data.isNonRecursive());
    ui.alwaysUpdateSnapshotsCheckBox.setSelected(data.isAlwaysUpdateSnapshots());
    ui.threadsEditor.setText(StringUtil.notNullize(data.getThreads()));

    ui.outputLevelCombo.setSelectedItem(data.getOutputLevel());
    ui.checksumPolicyCombo.setSelectedItem(data.getChecksumPolicy());
    ui.failPolicyCombo.setSelectedItem(data.getFailureBehavior());

    ui.showDialogWithAdvancedSettingsCheckBox.setSelected(data.isShowDialogWithAdvancedSettings());
    ui.useMavenConfigCheckBox.setSelected(data.isUseMavenConfig());
  }

  public @Nls String getDisplayName() {
    return CommonBundle.message("tab.title.general");
  }

  @ApiStatus.Internal
  public void applyTargetEnvironmentConfiguration(@NotNull Project project, @Nullable String targetName) {
    mavenPathsForm.apply(project, targetName);
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
