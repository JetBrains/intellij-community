// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Sergey Evdokimov
 */
public class MavenTestRunningConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private JPanel myMainPanel;

  private JBCheckBox myPassArgLineCB;
  private JBCheckBox myPassSystemPropertiesCB;
  private JBCheckBox myPassEnvironmentVariablesCB;

  private final Project myProject;

  public MavenTestRunningConfigurable(Project project) {
    myProject = project;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return ProjectBundle.message("maven.testRunning");
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return "reference.settings.project.maven.testRunning";
  }

  private void getSettingsFromUI(MavenTestRunningSettings settings) {
    settings.setPassArgLine(myPassArgLineCB.isSelected());
    settings.setPassSystemProperties(myPassSystemPropertiesCB.isSelected());
    settings.setPassEnvironmentVariables(myPassEnvironmentVariablesCB.isSelected());
  }

  @Override
  public void apply() throws ConfigurationException {
    getSettingsFromUI(MavenProjectSettings.getInstance(myProject).getTestRunningSettings());
  }

  @Override
  public void reset() {
    MavenTestRunningSettings settings = MavenProjectSettings.getInstance(myProject).getTestRunningSettings();

    myPassArgLineCB.setSelected(settings.isPassArgLine());
    myPassSystemPropertiesCB.setSelected(settings.isPassSystemProperties());
    myPassEnvironmentVariablesCB.setSelected(settings.isPassEnvironmentVariables());
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myMainPanel;
  }

  @Override
  public boolean isModified() {
    MavenTestRunningSettings uiSettings = new MavenTestRunningSettings();
    getSettingsFromUI(uiSettings);

    MavenTestRunningSettings projectSettings = MavenProjectSettings.getInstance(myProject).getTestRunningSettings();

    return !projectSettings.equals(uiSettings);
  }

  @Override
  public void disposeUIResources() {

  }

  @NotNull
  @Override
  public String getId() {
    return "reference.settings.project.maven.testRunning";
  }
}
