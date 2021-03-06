// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.execution.target.LanguageRuntimeConfiguration;
import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.execution.target.TargetEnvironmentsManager;
import com.intellij.execution.target.java.JavaLanguageRuntimeConfiguration;
import com.intellij.openapi.externalSystem.service.ui.ExternalSystemJdkComboBox;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.UserActivityWatcher;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenConfigurableBundle;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MavenRunnerPanel implements MavenSettingsObservable {
  protected final Project myProject;
  private final boolean myRunConfigurationMode;

  private JCheckBox myDelegateToMavenCheckbox;
  private JCheckBox myRunInBackgroundCheckbox;
  private RawCommandLineEditor myVMParametersEditor;
  private EnvironmentVariablesComponent myEnvVariablesComponent;
  private JLabel myJdkLabel;
  private ExternalSystemJdkComboBox myJdkCombo;
  private ComboBox<String> myTargetJdkCombo;

  private JCheckBox mySkipTestsCheckBox;
  private MavenPropertiesPanel myPropertiesPanel;

  private Map<String, String> myProperties;
  private String myTargetName;

  public MavenRunnerPanel(@NotNull Project p, boolean isRunConfiguration) {
    myProject = p;
    myRunConfigurationMode = isRunConfiguration;
  }

  public JComponent createComponent() {
    JPanel panel = new JPanel(new GridBagLayout());

    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.HORIZONTAL;
    c.anchor = GridBagConstraints.WEST;
    c.insets.bottom = 5;

    myDelegateToMavenCheckbox = new JCheckBox(MavenConfigurableBundle.message("maven.settings.runner.delegate"));
    myDelegateToMavenCheckbox.setMnemonic('d');

    myRunInBackgroundCheckbox = new JCheckBox(MavenConfigurableBundle.message("maven.settings.runner.run.in.background"));
    myRunInBackgroundCheckbox.setMnemonic('b');
    if (!myRunConfigurationMode) {
      c.gridx = 0;
      c.gridy++;
      c.weightx = 1;
      c.gridwidth = GridBagConstraints.REMAINDER;

      panel.add(myDelegateToMavenCheckbox, c);

      c.gridy++;
      panel.add(myRunInBackgroundCheckbox, c);
    }
    c.gridwidth = 1;

    JLabel labelVMParameters = new JLabel(MavenConfigurableBundle.message("maven.settings.runner.vm.options"));
    labelVMParameters.setDisplayedMnemonic('v');
    labelVMParameters.setLabelFor(myVMParametersEditor = new RawCommandLineEditor());
    myVMParametersEditor.setDialogCaption(labelVMParameters.getText());

    c.gridx = 0;
    c.gridy++;
    c.weightx = 0;
    panel.add(labelVMParameters, c);

    c.gridx = 1;
    c.weightx = 1;
    c.insets.left = 10;
    panel.add(myVMParametersEditor, c);
    c.insets.left = 0;

    myJdkLabel = new JLabel(MavenConfigurableBundle.message("maven.settings.runner.jre"));
    myJdkLabel.setDisplayedMnemonic('j');
    myJdkLabel.setLabelFor(myJdkCombo = new ExternalSystemJdkComboBox(myProject));
    c.gridx = 0;
    c.gridy++;
    c.weightx = 0;
    panel.add(myJdkLabel, c);
    c.gridx = 1;
    c.weightx = 1;
    c.insets.left = 10;
    c.fill = GridBagConstraints.HORIZONTAL;
    panel.add(myJdkCombo, c);
    myTargetJdkCombo = new ComboBox<>();
    ComponentUtil.putClientProperty(myTargetJdkCombo, UserActivityWatcher.DO_NOT_WATCH, true);
    myTargetJdkCombo.setVisible(false);
    panel.add(myTargetJdkCombo, c);
    c.insets.left = 0;

    myEnvVariablesComponent = new EnvironmentVariablesComponent();
    myEnvVariablesComponent.setPassParentEnvs(true);
    myEnvVariablesComponent.setLabelLocation(BorderLayout.WEST);
    c.gridx = 0;
    c.gridy++;
    c.weightx = 1;
    c.gridwidth = 2;
    panel.add(myEnvVariablesComponent, c);
    c.gridwidth = 1;

    JPanel propertiesPanel = new JPanel(new BorderLayout());
    propertiesPanel.setBorder(IdeBorderFactory.createTitledBorder(MavenConfigurableBundle.message("maven.settings.runner.properties"), false));

    propertiesPanel.add(mySkipTestsCheckBox = new JCheckBox(MavenConfigurableBundle.message("maven.settings.runner.skip.tests")), BorderLayout.NORTH);
    mySkipTestsCheckBox.setMnemonic('t');

    collectProperties();
    propertiesPanel.add(myPropertiesPanel = new MavenPropertiesPanel(myProperties), BorderLayout.CENTER);
    myPropertiesPanel.getTable().setShowGrid(false);
    myPropertiesPanel.getEmptyText().setText(MavenConfigurableBundle.message("maven.settings.runner.properties.not.defined"));

    c.gridx = 0;
    c.gridy++;
    c.weightx = c.weighty = 1;
    c.gridwidth = c.gridheight = GridBagConstraints.REMAINDER;
    c.fill = GridBagConstraints.BOTH;
    panel.add(propertiesPanel, c);

    return panel;
  }

  private void collectProperties() {
    MavenProjectsManager s = MavenProjectsManager.getInstance(myProject);
    Map<String, String> result = new LinkedHashMap<>();

    for (MavenProject each : s.getProjects()) {
      Properties properties = each.getProperties();
      result.putAll((Map)properties);
    }

    myProperties = result;
  }

  protected void getData(MavenRunnerSettings data) {
    myDelegateToMavenCheckbox.setSelected(data.isDelegateBuildToMaven());
    myRunInBackgroundCheckbox.setSelected(data.isRunMavenInBackground());
    myVMParametersEditor.setText(data.getVmOptions());
    mySkipTestsCheckBox.setSelected(data.isSkipTests());

    myJdkCombo.refreshData(data.getJreName());
    myTargetJdkCombo.setSelectedItem(data.getJreName());

    myPropertiesPanel.setDataFromMap(data.getMavenProperties());

    myEnvVariablesComponent.setEnvs(data.getEnvironmentProperties());
    myEnvVariablesComponent.setPassParentEnvs(data.isPassParentEnv());
  }


  protected void setData(MavenRunnerSettings data) {
    data.setDelegateBuildToMaven(myDelegateToMavenCheckbox.isSelected());
    data.setRunMavenInBackground(myRunInBackgroundCheckbox.isSelected());
    data.setVmOptions(myVMParametersEditor.getText().trim());
    data.setSkipTests(mySkipTestsCheckBox.isSelected());
    if (myTargetName == null) {
      data.setJreName(myJdkCombo.getSelectedValue());
    } else {
      data.setJreName(StringUtil.notNullize(myTargetJdkCombo.getItem(), MavenRunnerSettings.USE_PROJECT_JDK));
    }
    data.setMavenProperties(myPropertiesPanel.getDataAsMap());
    data.setEnvironmentProperties(myEnvVariablesComponent.getEnvs());
    data.setPassParentEnv(myEnvVariablesComponent.isPassParentEnvs());
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
        myJdkCombo.refreshData(null);
      }
    }
    else if (!localTarget) {
      updateJdkComponents(targetName);
    }
  }

  private void updateJdkComponents(@Nullable String targetName) {
    boolean localTarget = targetName == null;
    myTargetJdkCombo.setVisible(!localTarget);
    myJdkCombo.setVisible(localTarget);
    if (!localTarget) {
      List<String> items = IntStream.range(0, myTargetJdkCombo.getItemCount())
        .mapToObj(i -> myTargetJdkCombo.getItemAt(i))
        .collect(Collectors.toList());

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
        myTargetJdkCombo.removeAllItems();
        targetItems.forEach(myTargetJdkCombo::addItem);
      }
      myJdkLabel.setLabelFor(myTargetJdkCombo);
    } else {
      myJdkLabel.setLabelFor(myJdkCombo);
    }
  }

  @Override
  public void registerSettingsWatcher(@NotNull MavenRCSettingsWatcher watcher) {
    watcher.registerComponent("delegateToMaven", myDelegateToMavenCheckbox);
    watcher.registerComponent("runInBackground", myRunInBackgroundCheckbox);
    watcher.registerComponent("vmParameters", myVMParametersEditor);
    watcher.registerComponent("envVariables", myEnvVariablesComponent);
    watcher.registerComponent("jdk", myJdkCombo);
    watcher.registerComponent("targetJdk", myTargetJdkCombo);
    watcher.registerComponent("skipTests", mySkipTestsCheckBox);
    watcher.registerComponent("properties", myPropertiesPanel);
  }
}
