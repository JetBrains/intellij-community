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

package org.jetbrains.idea.maven.project;

import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Function;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenExecutionOptions;
import org.jetbrains.idea.maven.utils.ComboBoxUtil;

import javax.swing.*;
import java.util.Arrays;

/**
 * @author Ralf Quebbemann (ralfq@codehaus.org)
 */
public abstract class MavenGeneralConfigurable implements SearchableConfigurable {
  private JCheckBox checkboxWorkOffline;
  private JPanel panel;
  private JComboBox outputLevelCombo;
  private JCheckBox checkboxProduceExceptionErrorMessages;
  private JComboBox checksumPolicyCombo;
  private JComboBox failPolicyCombo;
  private JComboBox pluginUpdatePolicyCombo;
  private JCheckBox checkboxUsePluginRegistry;
  private JCheckBox checkboxRecursive;
  private MavenEnvironmentForm mavenPathsForm;
  private JComboBox snapshotUpdatePolicyCombo;
  private final DefaultComboBoxModel outputLevelComboModel = new DefaultComboBoxModel();
  private final DefaultComboBoxModel checksumPolicyComboModel = new DefaultComboBoxModel();
  private final DefaultComboBoxModel failPolicyComboModel = new DefaultComboBoxModel();
  private final DefaultComboBoxModel snapshotUpdatePolicyComboModel = new DefaultComboBoxModel();
  private final DefaultComboBoxModel pluginUpdatePolicyComboModel = new DefaultComboBoxModel();

  protected abstract MavenGeneralSettings getState();

  protected MavenGeneralConfigurable() {
    fillOutputLevelCombobox();
    fillChecksumPolicyCombobox();
    fillFailureBehaviorCombobox();
    fillPluginUpdatePolicyCombobox();
    fillSnapshotUpdatePolicyCombobox();
  }

  private void fillOutputLevelCombobox() {
    ComboBoxUtil.setModel(outputLevelCombo, outputLevelComboModel,
                          Arrays.asList(MavenExecutionOptions.LoggingLevel.values()),
                          new Function<MavenExecutionOptions.LoggingLevel, Pair<String, ?>>() {
                            public Pair<String, MavenExecutionOptions.LoggingLevel> fun(MavenExecutionOptions.LoggingLevel each) {
                              return Pair.create(each.getDisplayString(), each);
                            }
                          });
  }

  private void fillFailureBehaviorCombobox() {
    ComboBoxUtil.setModel(failPolicyCombo, failPolicyComboModel,
                          Arrays.asList(MavenExecutionOptions.FailureMode.values()),
                          new Function<MavenExecutionOptions.FailureMode, Pair<String, ?>>() {
                            public Pair<String, MavenExecutionOptions.FailureMode> fun(MavenExecutionOptions.FailureMode each) {
                              return Pair.create(each.getDisplayString(), each);
                            }
                          });
  }

  private void fillChecksumPolicyCombobox() {
    ComboBoxUtil.setModel(checksumPolicyCombo, checksumPolicyComboModel,
                          Arrays.asList(MavenExecutionOptions.ChecksumPolicy.values()),
                          new Function<MavenExecutionOptions.ChecksumPolicy, Pair<String, ?>>() {
                            public Pair<String, MavenExecutionOptions.ChecksumPolicy> fun(MavenExecutionOptions.ChecksumPolicy each) {
                              return Pair.create(each.getDisplayString(), each);
                            }
                          });
  }

  private void fillPluginUpdatePolicyCombobox() {
    ComboBoxUtil.setModel(pluginUpdatePolicyCombo, pluginUpdatePolicyComboModel,
                          Arrays.asList(MavenExecutionOptions.PluginUpdatePolicy.values()),
                          new Function<MavenExecutionOptions.PluginUpdatePolicy, Pair<String, ?>>() {
                            public Pair<String, MavenExecutionOptions.PluginUpdatePolicy> fun(MavenExecutionOptions.PluginUpdatePolicy each) {
                              return Pair.create(each.getDisplayString(), each);
                            }
                          });
  }

  private void fillSnapshotUpdatePolicyCombobox() {
    ComboBoxUtil.setModel(snapshotUpdatePolicyCombo, snapshotUpdatePolicyComboModel,
                          Arrays.asList(MavenExecutionOptions.SnapshotUpdatePolicy.values()),
                          new Function<MavenExecutionOptions.SnapshotUpdatePolicy, Pair<String, ?>>() {
                            public Pair<String, MavenExecutionOptions.SnapshotUpdatePolicy> fun(MavenExecutionOptions.SnapshotUpdatePolicy each) {
                              return Pair.create(each.getDisplayString(), each);
                            }
                          });
  }

  public JComponent createComponent() {
    mavenPathsForm.createComponent(); // have to initialize all listeners
    return panel;
  }

  public void disposeUIResources() {
  }

  public boolean isModified() {
    MavenGeneralSettings formData = new MavenGeneralSettings();
    setData(formData);
    return !formData.equals(getState());
  }

  public void apply() {
    setData(getState());
  }

  public void reset() {
    getData(getState());
  }

  private void setData(MavenGeneralSettings data) {
    data.setWorkOffline(checkboxWorkOffline.isSelected());
    mavenPathsForm.setData(data);

    data.setPrintErrorStackTraces(checkboxProduceExceptionErrorMessages.isSelected());
    data.setUsePluginRegistry(checkboxUsePluginRegistry.isSelected());
    data.setNonRecursive(!checkboxRecursive.isSelected());

    data.setOutputLevel((MavenExecutionOptions.LoggingLevel)ComboBoxUtil.getSelectedValue(outputLevelComboModel));
    data.setChecksumPolicy((MavenExecutionOptions.ChecksumPolicy)ComboBoxUtil.getSelectedValue(checksumPolicyComboModel));
    data.setFailureBehavior((MavenExecutionOptions.FailureMode)ComboBoxUtil.getSelectedValue(failPolicyComboModel));
    data.setPluginUpdatePolicy((MavenExecutionOptions.PluginUpdatePolicy)ComboBoxUtil.getSelectedValue(pluginUpdatePolicyComboModel));
    data.setSnapshotUpdatePolicy((MavenExecutionOptions.SnapshotUpdatePolicy)ComboBoxUtil.getSelectedValue(snapshotUpdatePolicyComboModel));
  }

  private void getData(MavenGeneralSettings data) {
    checkboxWorkOffline.setSelected(data.isWorkOffline());

    mavenPathsForm.getData(data);

    checkboxProduceExceptionErrorMessages.setSelected(data.isPrintErrorStackTraces());
    checkboxUsePluginRegistry.setSelected(data.isUsePluginRegistry());
    checkboxRecursive.setSelected(!data.isNonRecursive());

    ComboBoxUtil.select(outputLevelComboModel, data.getLoggingLevel());
    ComboBoxUtil.select(checksumPolicyComboModel, data.getChecksumPolicy());
    ComboBoxUtil.select(failPolicyComboModel, data.getFailureBehavior());
    ComboBoxUtil.select(pluginUpdatePolicyComboModel, data.getPluginUpdatePolicy());
    ComboBoxUtil.select(snapshotUpdatePolicyComboModel, data.getSnapshotUpdatePolicy());
  }

  @Nls
  public String getDisplayName() {
    return ProjectBundle.message("maven.tab.general");
  }

  @Nullable
  public Icon getIcon() {
    return null;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "reference.settings.dialog.project.maven";
  }

  public String getId() {
    return getHelpTopic();
  }

  public Runnable enableSearch(String option) {
    return null;
  }
}