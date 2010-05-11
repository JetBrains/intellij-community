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
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.MavenUtil;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

public class MavenEnvironmentForm {
  private JPanel panel;
  private LabeledComponent<TextFieldWithBrowseButton> mavenHomeComponent;
  private LabeledComponent<TextFieldWithBrowseButton> localRepositoryComponent;
  private LabeledComponent<TextFieldWithBrowseButton> mavenSettingsFileComponent;
  private JCheckBox mavenHomeOverrideCheckBox;
  private JCheckBox mavenSettingsFileOverrideCheckBox;
  private final PathOverrider mavenHomeOverrider;
  private final PathOverrider mavenSettingsFileOverrider;

  public MavenEnvironmentForm() {

    mavenHomeOverrider = new PathOverrider(mavenHomeComponent, mavenHomeOverrideCheckBox, new PathOverrider.PathProvider() {
      @Nullable
      protected File getFile() {
        return MavenUtil.resolveMavenHomeDirectory("");
      }
    });

    mavenSettingsFileOverrider =
      new PathOverrider(mavenSettingsFileComponent, mavenSettingsFileOverrideCheckBox, new PathOverrider.PathProvider() {
        @Nullable
        protected File getFile() {
          return MavenUtil.resolveUserSettingsFile("");
        }
      });
  }

  public boolean isModified(MavenGeneralSettings data) {
    MavenGeneralSettings formData = new MavenGeneralSettings();
    setData(formData);
    return !formData.equals(data);
  }

  public void setData(MavenGeneralSettings data) {
    data.setMavenHome(mavenHomeOverrider.getText());
    data.setMavenSettingsFile(mavenSettingsFileOverrider.getText());
  }

  public void getData(MavenGeneralSettings data) {
    mavenHomeOverrider.setText(data.getMavenHome());
    mavenSettingsFileOverrider.setText(data.getMavenSettingsFile());
  }

  public JComponent createComponent() {
    // all listeners will be removed when dialog is closed
    mavenHomeComponent.getComponent().addBrowseFolderListener(ProjectBundle.message("maven.select.maven.home.directory"), "", null,
                                                              new FileChooserDescriptor(false, true, false, false, false, false));
    mavenSettingsFileComponent.getComponent().addBrowseFolderListener(ProjectBundle.message("maven.select.maven.settings.file"), "", null,
                                                                      new FileChooserDescriptor(true, false, false, false, false, false));
    localRepositoryComponent.getComponent().addBrowseFolderListener(ProjectBundle.message("maven.select.local.repository"), "", null,
                                                                    new FileChooserDescriptor(false, true, false, false, false, false));

    return panel;
  }

  private static class PathOverrider {
    static abstract class PathProvider {
      public String getPath() {
        final File file = getFile();
        return file == null ? "" : file.getPath();
      }

      @Nullable
      abstract protected File getFile();
    }

    private final TextFieldWithBrowseButton component;
    private final JCheckBox checkBox;
    private final PathProvider pathProvider;

    private String overrideText;
    private final ActionListener listener = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        update();
      }
    };

    public PathOverrider(final LabeledComponent<TextFieldWithBrowseButton> component,
                         final JCheckBox checkBox,
                         PathProvider pathProvider) {
      this.component = component.getComponent();
      this.checkBox = checkBox;
      this.pathProvider = pathProvider;
      checkBox.addActionListener(listener);
    }

    private void update() {
      final boolean override = checkBox.isSelected();

      component.setEditable(override);
      component.setEnabled(override);

      if (override) {
        component.setText(overrideText);
      }
      else {
        overrideText = component.getText();
        component.setText(pathProvider.getPath());
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
