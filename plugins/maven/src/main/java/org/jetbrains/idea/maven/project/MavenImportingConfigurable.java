/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenJdkComboBox;
import org.jetbrains.idea.maven.server.MavenServerManager;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class MavenImportingConfigurable implements SearchableConfigurable {
  private final MavenImportingSettings myImportingSettings;
  private final MavenImportingSettingsForm mySettingsForm = new MavenImportingSettingsForm(false, false);
  private final List<UnnamedConfigurable> myAdditionalConfigurables;

  private final JCheckBox myUseMaven3CheckBox;
  private final JTextField myEmbedderVMOptions;
  private final MavenJdkComboBox myEmbedderJdk;

  public MavenImportingConfigurable(Project project) {
    myImportingSettings = MavenProjectsManager.getInstance(project).getImportingSettings();

    myAdditionalConfigurables = new ArrayList<UnnamedConfigurable>();
    for (final AdditionalMavenImportingSettings additionalSettings : AdditionalMavenImportingSettings.EP_NAME.getExtensions()) {
      myAdditionalConfigurables.add(additionalSettings.createConfigurable(project));
    }

    myUseMaven3CheckBox = new JCheckBox("Use Maven3 to import project");
    myUseMaven3CheckBox.setToolTipText("If this option is disabled maven 2 will be used");

    myEmbedderVMOptions = new JTextField(30);
    myEmbedderJdk = new MavenJdkComboBox(null); // Embedder JDK is an application setting, not a project setting, so don't pass project
    assert myEmbedderJdk.getProject() == null;
  }

  public JComponent createComponent() {
    final JPanel panel = mySettingsForm.getAdditionalSettingsPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

    panel.add(Box.createVerticalStrut(5));

    JPanel useMaven3Panel = new JPanel(new BorderLayout());
    useMaven3Panel.add(myUseMaven3CheckBox, BorderLayout.WEST);

    panel.add(useMaven3Panel);

    JPanel embedderVMOptionPanel = new JPanel(new BorderLayout());
    JLabel vmOptionLabel = new JLabel("VM options for importer:");
    embedderVMOptionPanel.add(vmOptionLabel, BorderLayout.WEST);
    vmOptionLabel.setLabelFor(myEmbedderVMOptions);

    embedderVMOptionPanel.add(myEmbedderVMOptions);

    panel.add(Box.createVerticalStrut(3));
    panel.add(embedderVMOptionPanel);

    JPanel embedderJdkPanel = new JPanel(new BorderLayout());
    JLabel embedderJdkLabel = new JLabel("JDK for importer:");
    embedderJdkLabel.setLabelFor(myEmbedderJdk);
    embedderJdkPanel.add(embedderJdkLabel, BorderLayout.WEST);
    embedderJdkPanel.add(myEmbedderJdk);

    panel.add(Box.createVerticalStrut(3));
    panel.add(embedderJdkPanel);

    for (final UnnamedConfigurable additionalConfigurable : myAdditionalConfigurables) {
      panel.add(Box.createVerticalStrut(3));
      panel.add(additionalConfigurable.createComponent());
    }
    return mySettingsForm.createComponent();
  }

  public void disposeUIResources() {
    for (final UnnamedConfigurable additionalConfigurable : myAdditionalConfigurables) {
      additionalConfigurable.disposeUIResources();
    }
  }

  public boolean isModified() {
    for (final UnnamedConfigurable additionalConfigurable : myAdditionalConfigurables) {
      if (additionalConfigurable.isModified()) {
        return true;
      }
    }

    if ((!myUseMaven3CheckBox.isSelected()) != MavenServerManager.getInstance().isUseMaven2()) {
      return true;
    }

    if (!MavenServerManager.getInstance().getMavenEmbedderVMOptions().equals(myEmbedderVMOptions.getText())) {
      return true;
    }

    if (!MavenServerManager.getInstance().getEmbedderJdk().equals(myEmbedderJdk.getSelectedValue())) {
      return true;
    }

    return mySettingsForm.isModified(myImportingSettings);
  }

  public void apply() throws ConfigurationException {
    mySettingsForm.getData(myImportingSettings);

    MavenServerManager.getInstance().setUseMaven2(!myUseMaven3CheckBox.isSelected());
    MavenServerManager.getInstance().setMavenEmbedderVMOptions(myEmbedderVMOptions.getText());
    MavenServerManager.getInstance().setEmbedderJdk(myEmbedderJdk.getSelectedValue());

    for (final UnnamedConfigurable additionalConfigurable : myAdditionalConfigurables) {
      additionalConfigurable.apply();
    }
  }

  public void reset() {
    mySettingsForm.setData(myImportingSettings);

    myUseMaven3CheckBox.setSelected(!MavenServerManager.getInstance().isUseMaven2());
    myEmbedderVMOptions.setText(MavenServerManager.getInstance().getMavenEmbedderVMOptions());
    myEmbedderJdk.refreshData(MavenServerManager.getInstance().getEmbedderJdk());

    for (final UnnamedConfigurable additionalConfigurable : myAdditionalConfigurables) {
      additionalConfigurable.reset();
    }
  }

  @Nls
  public String getDisplayName() {
    return ProjectBundle.message("maven.tab.importing");
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "reference.settings.project.maven.importing";
  }

  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  public Runnable enableSearch(String option) {
    return null;
  }
}
