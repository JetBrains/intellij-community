/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.edu.learning.newproject.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.util.projectWizard.ProjectWizardUtil;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;

/**
 * @author meanmail
 */
public class EduCreateNewProjectPanel extends JPanel {
  private JPanel panel;
  private JTextField nameField;
  private TextFieldWithBrowseButton locationField;
  private JLabel errorIcon;
  private JLabel errorLabel;

  public EduCreateNewProjectPanel(@NotNull final Project project, @NotNull EduCreateNewProjectDialog dialog) {
    setLayout(new BorderLayout());
    add(panel, BorderLayout.CENTER);
    errorIcon.setIcon(AllIcons.Actions.Lightning);
    resetError();
    String location = RecentProjectsManager.getInstance().getLastProjectCreationLocation();
    locationField.setText(location);
    String name = ProjectWizardUtil.findNonExistingFileName(location, "course", "");
    nameField.setText(name);
    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    locationField.addBrowseFolderListener("Choose Location Folder", null, project, descriptor);
    locationField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        String location = FileUtil.toSystemDependentName(locationField.getText());
        File file = new File(location);
        if (!FileUtil.ensureCanCreateFile(file)) {
          dialog.setOKActionEnabled(false);
          setError("Invalid location");
        } else {
          dialog.setOKActionEnabled(true);
          resetError();
        }
      }
    });
  }

  private void setState(boolean isVisible) {
    errorIcon.setVisible(isVisible);
    errorLabel.setVisible(isVisible);
  }

  void setError(@NotNull String message) {
    errorLabel.setText(message);
    setState(true);
  }

  public String getName() {
    return nameField.getText();
  }

  public String getLocationPath() {
    return locationField.getText();
  }

  public void resetError() {
    setState(false);
  }
}
