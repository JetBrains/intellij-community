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
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import org.apache.maven.execution.MavenExecutionRequest;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.util.ComboBoxUtil;
import org.jetbrains.idea.maven.core.util.MavenEnv;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

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
  private LabeledComponent<TextFieldWithBrowseButton> mavenHomeComponent;
  private LabeledComponent<TextFieldWithBrowseButton> localRepositoryComponent;
  private LabeledComponent<TextFieldWithBrowseButton> mavenSettingsFileComponent;
  private JCheckBox mavenHomeOverrideCheckBox;
  private JCheckBox mavenSettingsFileOverrideCheckBox;
  private JCheckBox localRepositoryOverrideCheckBox;
  private Overrider mavenHomeOverrider;
  private Overrider mavenSettingsFileOverrider;
  private Overrider localRepositoryOverrider;
  private final DefaultComboBoxModel comboboxModelOutputLevel = new DefaultComboBoxModel();
  private final DefaultComboBoxModel comboboxModelChecksumPolicy = new DefaultComboBoxModel();
  private final DefaultComboBoxModel comboboxModelMultiprojectBuildFailPolicy = new DefaultComboBoxModel();
  private final DefaultComboBoxModel comboboxModelPluginUpdatePolicy = new DefaultComboBoxModel();

  private final MavenCore myMavenCore;

  public MavenCoreConfigurable(MavenCore mavenCore) {
    myMavenCore = mavenCore;
  }

  private void initPathEditors() {
    mavenHomeComponent.getComponent().addBrowseFolderListener(CoreBundle.message("maven.select.maven.home.directory"), "", null,
                                                              new FileChooserDescriptor(false, true, false, false, false, false));
    mavenHomeOverrider = new Overrider(mavenHomeComponent, mavenHomeOverrideCheckBox, new Overrider.DefaultFileProvider() {
      @Nullable
      protected File getFile() {
        return MavenEnv.resolveMavenHomeDirectory("");
      }
    });

    mavenSettingsFileComponent.getComponent().addBrowseFolderListener(CoreBundle.message("maven.select.maven.settings.file"), "", null,
                                                                      new FileChooserDescriptor(true, false, false, false, false, false));
    mavenSettingsFileOverrider =
      new Overrider(mavenSettingsFileComponent, mavenSettingsFileOverrideCheckBox, new Overrider.DefaultFileProvider() {
        @Nullable
        protected File getFile() {
          return MavenEnv.resolveUserSettingsFile("");
        }
      });

    localRepositoryComponent.getComponent().addBrowseFolderListener(CoreBundle.message("maven.select.local.repository"), "", null,
                                                                    new FileChooserDescriptor(false, true, false, false, false, false));
    localRepositoryOverrider =
      new Overrider(localRepositoryComponent, localRepositoryOverrideCheckBox, new Overrider.DefaultFileProvider() {
        @Nullable
        protected File getFile() {
          return MavenEnv.resolveLocalRepository(mavenHomeOverrider.getText(), mavenSettingsFileOverrider.getText(), "");
        }
      });
  }

  private void fillComboboxFailureBehavior() {
    ComboBoxUtil.setModel(comboboxMultiprojectBuildFailPolicy, comboboxModelMultiprojectBuildFailPolicy, new Object[][]{
      {MavenExecutionRequest.REACTOR_FAIL_FAST, "Stop at first failure"}, {MavenExecutionRequest.REACTOR_FAIL_AT_END, "Fail at the end"},
      {MavenExecutionRequest.REACTOR_FAIL_NEVER, "Never fail"}});
  }

  private void fillComboboxPluginUpdatePolicy() {
    ComboBoxUtil.setModel(comboboxPluginUpdatePolicy, comboboxModelPluginUpdatePolicy,
                          new Object[][]{{"true", "Check For Updates"}, {"false", "Supress Checking"}});
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

    initPathEditors();
    return panel;
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

    data.setMavenHome(mavenHomeOverrider.getText());
    data.setMavenSettingsFile(mavenSettingsFileOverrider.getText());
    data.setLocalRepository(localRepositoryOverrider.getText());

    data.setProduceExceptionErrorMessages(checkboxProduceExceptionErrorMessages.isSelected());
    data.setUsePluginRegistry(checkboxUsePluginRegistry.isSelected());
    data.setNonRecursive(checkboxNonRecursive.isSelected());

    data.setOutputLevelString(ComboBoxUtil.getSelectedString(comboboxModelOutputLevel));
    data.setChecksumPolicy(ComboBoxUtil.getSelectedString(comboboxModelChecksumPolicy));
    data.setFailureBehavior(ComboBoxUtil.getSelectedString(comboboxModelMultiprojectBuildFailPolicy));
    data.setPluginUpdatePolicy(Boolean.valueOf(ComboBoxUtil.getSelectedString(comboboxModelPluginUpdatePolicy)).booleanValue());
  }

  private void getData(MavenCoreState data) {
    checkboxWorkOffline.setSelected(data.isWorkOffline());

    mavenHomeOverrider.setText(data.getMavenHome());
    mavenSettingsFileOverrider.setText(data.getMavenSettingsFile());
    localRepositoryOverrider.setText(data.getLocalRepository());

    checkboxProduceExceptionErrorMessages.setSelected(data.isProduceExceptionErrorMessages());
    checkboxUsePluginRegistry.setSelected(data.isUsePluginRegistry());
    checkboxNonRecursive.setSelected(data.isNonRecursive());

    ComboBoxUtil.select(comboboxModelOutputLevel, data.getOutputLevelString());
    ComboBoxUtil.select(comboboxModelChecksumPolicy, data.getChecksumPolicy());
    ComboBoxUtil.select(comboboxModelMultiprojectBuildFailPolicy, data.getFailureBehavior());
    ComboBoxUtil.select(comboboxModelPluginUpdatePolicy, Boolean.toString(data.getPluginUpdatePolicy()));
  }

  public void disposeUIResources() {
    mavenHomeOverrider.dispose();
    mavenSettingsFileOverrider.dispose();
    localRepositoryOverrider.dispose();
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

  public static class Overrider {
    interface DefaultTextProvider {
      String getText();
    }

    static abstract class DefaultFileProvider implements DefaultTextProvider {

      public String getText() {
        final File file = getFile();
        return file != null ? file.getPath() : "";
      }

      @Nullable
      abstract protected File getFile();
    }

    private final TextFieldWithBrowseButton component;
    private final JCheckBox checkBox;
    private final DefaultTextProvider defaultTextProvider;

    private String overrideText;
    private ActionListener listener = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        update();
      }
    };

    public Overrider(final LabeledComponent<TextFieldWithBrowseButton> component,
                     final JCheckBox checkBox,
                     DefaultTextProvider defaultTextProvider) {
      this.component = component.getComponent();
      this.checkBox = checkBox;
      this.defaultTextProvider = defaultTextProvider;
      checkBox.addActionListener(listener);
    }

    public void dispose() {
      checkBox.removeActionListener(listener);
    }

    private void update() {
      final boolean override = checkBox.isSelected();

      component.getTextField().setEditable(override);
      component.getButton().setEnabled(override);
      if (override) {
        component.setText(overrideText);
      }
      else {
        overrideText = component.getText();
        component.setText(defaultTextProvider.getText());
      }
    }

    public void setText(String text) {
      overrideText = text;
      checkBox.setSelected(!StringUtil.isEmptyOrSpaces(text));
      update();
    }

    public String getText() {
      return checkBox.isSelected() ? component.getText().trim() : "";
    }
  }
}