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

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.MavenUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

public class MavenEnvironmentForm {
  private JPanel panel;
  private LabeledComponent<TextFieldWithBrowseButton> mavenHomeComponent;
  private LabeledComponent<TextFieldWithBrowseButton> settingsFileComponent;
  private LabeledComponent<TextFieldWithBrowseButton> localRepositoryComponent;
  private JCheckBox mavenHomeOverrideCheckBox;
  private JCheckBox settingsOverrideCheckBox;
  private JCheckBox localRepositoryOverrideCheckBox;

  private final PathOverrider mavenHomeOverrider;
  private final PathOverrider userSettingsFileOverrider;
  private final PathOverrider localRepositoryOverrider;

  private final Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  public MavenEnvironmentForm() {
    DocumentAdapter listener = new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            myUpdateAlarm.cancelAllRequests();
            myUpdateAlarm.addRequest(new Runnable() {
                @Override
                public void run() {
                  mavenHomeOverrider.updateDefault();
                  userSettingsFileOverrider.updateDefault();
                  localRepositoryOverrider.updateDefault();
                }
              }, 100);
          }
        });
      }
    };

    mavenHomeOverrider = new PathOverrider(mavenHomeComponent, mavenHomeOverrideCheckBox, listener, new PathProvider() {
      @Nullable
      protected File getFile() {
        return MavenUtil.resolveMavenHomeDirectory("");
      }
    });

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
                                                  mavenHomeComponent.getComponent().getText(),
                                                  settingsFileComponent.getComponent().getText());
        }
      });
  }

  public boolean isModified(MavenGeneralSettings data) {
    MavenGeneralSettings formData = new MavenGeneralSettings();
    setData(formData);
    return !formData.equals(data);
  }

  public void setData(MavenGeneralSettings data) {
    data.setMavenHome(mavenHomeOverrider.getResult());
    data.setUserSettingsFile(userSettingsFileOverrider.getResult());
    data.setLocalRepository(localRepositoryOverrider.getResult());
  }

  public void getData(MavenGeneralSettings data) {
    mavenHomeOverrider.reset(data.getMavenHome());
    userSettingsFileOverrider.reset(data.getUserSettingsFile());
    localRepositoryOverrider.reset(data.getLocalRepository());
  }

  public JComponent createComponent() {
    // all listeners will be removed when dialog is closed
    mavenHomeComponent.getComponent().addBrowseFolderListener(ProjectBundle.message("maven.select.maven.home.directory"), "", null,
                                                              new FileChooserDescriptor(false, true, false, false, false, false));
    settingsFileComponent.getComponent().addBrowseFolderListener(ProjectBundle.message("maven.select.maven.settings.file"), "", null,
                                                                 new FileChooserDescriptor(true, false, false, false, false, false));
    localRepositoryComponent.getComponent().addBrowseFolderListener(ProjectBundle.message("maven.select.local.repository"), "", null,
                                                                    new FileChooserDescriptor(false, true, false, false, false, false));
    return panel;
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
      component.setEnabled(override);

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
