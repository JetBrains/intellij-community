// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import com.intellij.execution.target.LanguageRuntimeConfiguration;
import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.execution.target.TargetEnvironmentsManager;
import com.intellij.execution.target.java.JavaLanguageRuntimeConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.IntStream;

public class MavenRunnerPanel {
  protected final Project myProject;
  private final boolean myRunConfigurationMode;

  private MavenRunnerUi ui;

  private Map<String, String> myProperties;
  private String myTargetName;

  public MavenRunnerPanel(@NotNull Project p, boolean isRunConfiguration) {
    myProject = p;
    myRunConfigurationMode = isRunConfiguration;
  }

  public JComponent createComponent() {
    collectProperties();

    ui = new MavenRunnerUi(myProject, myRunConfigurationMode, myProperties);

    return ui.panel;
  }

  private void collectProperties() {
    MavenProjectsManager manager = MavenProjectsManager.getInstance(myProject);
    Map<String, String> result = new LinkedHashMap<>();

    if (manager.isMavenizedProject()) {
      for (MavenProject each : manager.getProjects()) {
        Properties properties = each.getProperties();
        result.putAll((Map)properties);
      }
    }

    myProperties = result;
  }

  protected void getData(MavenRunnerSettings data) {
    ui.delegateToMavenCheckbox.setSelected(data.isDelegateBuildToMaven());
    ui.vmParametersEditor.setText(data.getVmOptions());
    ui.skipTestsCheckBox.setSelected(data.isSkipTests());

    ui.jdkCombo.refreshData(data.getJreName());
    ui.targetJdkCombo.setSelectedItem(data.getJreName());

    ui.propertiesPanel.setDataFromMap(data.getMavenProperties());

    ui.envVariablesComponent.setEnvs(data.getEnvironmentProperties());
    ui.envVariablesComponent.setPassParentEnvs(data.isPassParentEnv());
  }


  protected void setData(MavenRunnerSettings data) {
    data.setDelegateBuildToMaven(ui.delegateToMavenCheckbox.isSelected());
    data.setVmOptions(ui.vmParametersEditor.getText().trim());
    data.setSkipTests(ui.skipTestsCheckBox.isSelected());
    if (myTargetName == null) {
      data.setJreName(ui.jdkCombo.getSelectedValue());
    }
    else {
      data.setJreName(StringUtil.notNullize(ui.targetJdkCombo.getItem(), MavenRunnerSettings.USE_PROJECT_JDK));
    }
    data.setMavenProperties(ui.propertiesPanel.getDataAsMap());
    data.setEnvironmentProperties(ui.envVariablesComponent.getEnvs());
    data.setPassParentEnv(ui.envVariablesComponent.isPassParentEnvs());
  }

  public Project getProject() {
    return myProject;
  }

  @ApiStatus.Internal
  void applyTargetEnvironmentConfiguration(@Nullable String targetName) {
    boolean localTarget = targetName == null;
    boolean targetChanged = !Objects.equals(myTargetName, targetName);
    if (targetChanged) {
      myTargetName = targetName;
      updateJdkComponents(targetName);
      if (localTarget) {
        ui.jdkCombo.refreshData(null);
      }
    }
    else if (!localTarget) {
      updateJdkComponents(targetName);
    }
  }

  private void updateJdkComponents(@Nullable String targetName) {
    boolean localTarget = targetName == null;
    ui.setState(localTarget);
    if (!localTarget) {
      List<String> items = IntStream.range(0, ui.targetJdkCombo.getItemCount())
        .mapToObj(i -> ui.targetJdkCombo.getItemAt(i))
        .toList();

      List<String> targetItems = new ArrayList<>();
      TargetEnvironmentConfiguration targetEnvironmentConfiguration = TargetEnvironmentsManager.getInstance(myProject)
        .getTargets().findByName(targetName);
      if (targetEnvironmentConfiguration != null) {
        for (LanguageRuntimeConfiguration runtimeConfiguration : targetEnvironmentConfiguration.getRuntimes().resolvedConfigs()) {
          if (runtimeConfiguration instanceof JavaLanguageRuntimeConfiguration) {
            String homePath = ((JavaLanguageRuntimeConfiguration)runtimeConfiguration).getHomePath();
            targetItems.add(homePath);
          }
        }
      }

      if (!items.equals(targetItems)) {
        ui.targetJdkCombo.removeAllItems();
        targetItems.forEach(ui.targetJdkCombo::addItem);
      }
    }
  }
}
