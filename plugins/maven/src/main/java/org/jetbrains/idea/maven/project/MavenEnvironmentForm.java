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

import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.TextFieldWithHistory;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Alarm;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.jetbrains.idea.maven.utils.MavenUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;

public class MavenEnvironmentForm implements PanelWithAnchor {
  private JPanel panel;
  private LabeledComponent<ComponentWithBrowseButton<TextFieldWithHistory>> mavenHomeComponent;
  private TextFieldWithHistory mavenHomeField;
  private LabeledComponent<JBLabel> mavenVersionLabelComponent;
  private LabeledComponent<TextFieldWithBrowseButton> settingsFileComponent;
  private LabeledComponent<TextFieldWithBrowseButton> localRepositoryComponent;
  private JCheckBox settingsOverrideCheckBox;
  private JCheckBox localRepositoryOverrideCheckBox;
  private JComponent anchor;

  private final PathOverrider userSettingsFileOverrider;
  private final PathOverrider localRepositoryOverrider;

  private boolean isUpdating = false;
  private final Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  public MavenEnvironmentForm() {
    DocumentAdapter listener = new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        UIUtil.invokeLaterIfNeeded(() -> {
          if (isUpdating) return;
          if (!panel.isShowing()) return;

          myUpdateAlarm.cancelAllRequests();
          myUpdateAlarm.addRequest(() -> {
            isUpdating = true;
            userSettingsFileOverrider.updateDefault();
            localRepositoryOverrider.updateDefault();
            isUpdating = false;
          }, 100);
        });
      }
    };

    userSettingsFileOverrider =
      new PathOverrider(settingsFileComponent, settingsOverrideCheckBox, listener, new PathProvider() {
        @Nullable
        protected File getFile() {
          return MavenUtil.resolveUserSettingsFile("");
        }
      });

    localRepositoryOverrider =
      new PathOverrider(localRepositoryComponent, localRepositoryOverrideCheckBox, listener, new PathProvider() {
        @Nullable
        protected File getFile() {
          return MavenUtil.resolveLocalRepository("",
                                                  FileUtil.toSystemIndependentName(
                                                    mavenHomeField.getText().trim()),
                                                  settingsFileComponent.getComponent().getText());
        }
      });

    mavenHomeField.addDocumentListener(listener);

    setAnchor(mavenHomeComponent.getLabel());
  }

  private void createUIComponents() {
    mavenHomeField = new TextFieldWithHistory();
    mavenHomeField.setHistorySize(-1);
    final ArrayList<String> foundMavenHomes = new ArrayList<>();
    foundMavenHomes.add(MavenServerManager.BUNDLED_MAVEN_2);
    foundMavenHomes.add(MavenServerManager.BUNDLED_MAVEN_3);
    final File mavenHomeDirectory = MavenUtil.resolveMavenHomeDirectory(null);
    if (mavenHomeDirectory != null) {
      foundMavenHomes.add(FileUtil.toSystemIndependentName(mavenHomeDirectory.getPath()));
    }
    mavenHomeField.setHistory(foundMavenHomes);
    mavenHomeComponent = LabeledComponent.create(
      new ComponentWithBrowseButton<>(mavenHomeField, null), "Maven &amp;home directory");

    final JBLabel versionLabel = new JBLabel();
    versionLabel.setOpaque(true);
    versionLabel.setVerticalAlignment(SwingConstants.TOP);
    versionLabel.setVerticalTextPosition(SwingConstants.TOP);
    mavenVersionLabelComponent = LabeledComponent.create(versionLabel, "");
  }

  public boolean isModified(MavenGeneralSettings data) {
    MavenGeneralSettings formData = new MavenGeneralSettings();
    setData(formData);
    return !formData.equals(data);
  }

  public void setData(MavenGeneralSettings data) {
    data.setMavenHome(FileUtil.toSystemIndependentName(mavenHomeField.getText().trim()));
    data.setUserSettingsFile(userSettingsFileOverrider.getResult());
    data.setLocalRepository(localRepositoryOverrider.getResult());
  }

  public void getData(MavenGeneralSettings data) {
    final String resolvedMavenHome = resolveMavenHome(data.getMavenHome());
    final String mavenHome = ObjectUtils.chooseNotNull(resolvedMavenHome, data.getMavenHome());
    mavenHomeField.setText(mavenHome != null ? FileUtil.toSystemIndependentName(mavenHome): null);
    mavenHomeField.addCurrentTextToHistory();
    updateMavenVersionLabel();
    userSettingsFileOverrider.reset(data.getUserSettingsFile());
    localRepositoryOverrider.reset(data.getLocalRepository());
  }

  @Nullable
  private static String resolveMavenHome(@Nullable String mavenHome) {
    if (mavenHome != null && (StringUtil.equals(MavenServerManager.BUNDLED_MAVEN_2, mavenHome) ||
                              StringUtil.equals(MavenServerManager.BUNDLED_MAVEN_3, mavenHome))) {
      return mavenHome;
    }
    final File mavenHomeDirectory = MavenUtil.resolveMavenHomeDirectory(mavenHome);
    return mavenHomeDirectory != null ? mavenHomeDirectory.getPath() : null;
  }

  private void updateMavenVersionLabel() {
    String version = MavenServerManager.getInstance().getMavenVersion(getMavenHome());
    String versionText = version == null ? "Invalid Maven home directory" : String.format("(Version: %s)", version);
    mavenVersionLabelComponent.getComponent().setText(versionText);
  }

  @Nullable
  public String getMavenHome() {
    String mavenHome = FileUtil.toSystemIndependentName(mavenHomeField.getText().trim());
    final File mavenHomeFile = MavenServerManager.getMavenHomeFile(mavenHome);
    return mavenHomeFile != null ? mavenHomeFile.getPath() : null;
  }

  public JComponent createComponent() {
    // all listeners will be removed when dialog is closed
    mavenHomeComponent.getComponent().addBrowseFolderListener(ProjectBundle.message("maven.select.maven.home.directory"),
                                                              "",
                                                              null, BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR,
                                                              TextComponentAccessor.TEXT_FIELD_WITH_HISTORY_WHOLE_TEXT);
    mavenHomeField.addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        updateMavenVersionLabel();
      }
    });

    settingsFileComponent.getComponent().addBrowseFolderListener(ProjectBundle.message("maven.select.maven.settings.file"), "", null,
                                                                 FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
    localRepositoryComponent.getComponent().addBrowseFolderListener(ProjectBundle.message("maven.select.local.repository"), "", null,
                                                                    FileChooserDescriptorFactory.createSingleFolderDescriptor());
    return panel;
  }

  @Override
  public JComponent getAnchor() {
    return anchor;
  }

  @Override
  public void setAnchor(JComponent anchor) {
    this.anchor = anchor;
    mavenHomeComponent.setAnchor(anchor);
    mavenVersionLabelComponent.setAnchor(anchor);
    settingsFileComponent.setAnchor(anchor);
    localRepositoryComponent.setAnchor(anchor);
  }

  private static abstract class PathProvider {
    public String getPath() {
      final File file = getFile();
      return file == null ? "" : file.getPath();
    }

    @Nullable
    abstract protected File getFile();
  }

  private static class PathOverrider {
    private final TextFieldWithBrowseButton component;
    private final JCheckBox checkBox;
    private final PathProvider pathProvider;

    private Boolean isOverridden;
    private String overrideText;

    public PathOverrider(final LabeledComponent<TextFieldWithBrowseButton> component,
                         final JCheckBox checkBox,
                         DocumentListener docListener,
                         PathProvider pathProvider) {
      this.component = component.getComponent();
      this.component.getTextField().getDocument().addDocumentListener(docListener);
      this.checkBox = checkBox;
      this.pathProvider = pathProvider;
      checkBox.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          update();
        }
      });
    }

    private void update() {
      final boolean override = checkBox.isSelected();
      if (Comparing.equal(isOverridden, override)) return;

      isOverridden = override;

      component.setEditable(override);
      component.setEnabled(override && checkBox.isEnabled());

      if (override) {
        if (overrideText != null) component.setText(overrideText);
      }
      else {
        if (!StringUtil.isEmptyOrSpaces(component.getText())) overrideText = component.getText();
        component.setText(pathProvider.getPath());
      }
    }

    private void updateDefault() {
      if (!checkBox.isSelected()) {
        component.setText(pathProvider.getPath());
      }
    }

    public void reset(String text) {
      isOverridden = null;
      checkBox.setSelected(!StringUtil.isEmptyOrSpaces(text));
      overrideText = StringUtil.isEmptyOrSpaces(text) ? null : text;
      update();
    }

    public String getResult() {
      return checkBox.isSelected() ? component.getText().trim() : "";
    }
  }
}
