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
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.util.MavenEnv;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

public class MavenPathsForm {
  private JPanel panel;
  private LabeledComponent<TextFieldWithBrowseButton> mavenHomeComponent;
  private LabeledComponent<TextFieldWithBrowseButton> localRepositoryComponent;
  private LabeledComponent<TextFieldWithBrowseButton> mavenSettingsFileComponent;
  private JCheckBox mavenHomeOverrideCheckBox;
  private JCheckBox mavenSettingsFileOverrideCheckBox;
  private JCheckBox localRepositoryOverrideCheckBox;
  private Overrider mavenHomeOverrider;
  private Overrider mavenSettingsFileOverrider;
  private Overrider localRepositoryOverrider;

  public MavenPathsForm() {
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

  public boolean isModified(MavenCoreState data) {
    MavenCoreState formData = new MavenCoreState();
    setData(formData);
    return !formData.equals(data);
  }

  public void setData(MavenCoreState data) {
    data.setMavenHome(mavenHomeOverrider.getText());
    data.setMavenSettingsFile(mavenSettingsFileOverrider.getText());
    data.setLocalRepository(localRepositoryOverrider.getText());
  }

  public void getData(MavenCoreState data) {
    mavenHomeOverrider.setText(data.getMavenHome());
    mavenSettingsFileOverrider.setText(data.getMavenSettingsFile());
    localRepositoryOverrider.setText(data.getLocalRepository());
  }

  public void disposeUIResources() {
    mavenHomeOverrider.dispose();
    mavenSettingsFileOverrider.dispose();
    localRepositoryOverrider.dispose();
  }

  public JComponent getPanel() {
    return panel;
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