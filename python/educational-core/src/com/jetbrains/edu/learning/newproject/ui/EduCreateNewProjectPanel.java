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
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapperPeer;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;

public class EduCreateNewProjectPanel extends JPanel {
  private JPanel myPanel;
  private TextFieldWithBrowseButton myLocationField;
  private JLabel myErrorIcon;
  private JLabel myErrorLabel;
  private JTextPane myDescription;

  public EduCreateNewProjectPanel(@NotNull final Project project, @NotNull EduCreateNewProjectDialog dialog) {
    setLayout(new BorderLayout());
    add(myPanel, BorderLayout.CENTER);
    myErrorIcon.setIcon(AllIcons.Actions.Lightning);
    resetError();
    String location = findSequentNonExistingUntitled().toString();
    myLocationField.setText(location);
    final int index = location.lastIndexOf(File.separator);
    if (index > 0) {
      JTextField textField = myLocationField.getTextField();
      textField.select(index + 1, location.length());
      textField.putClientProperty(DialogWrapperPeer.HAVE_INITIAL_SELECTION, true);
    }

    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    myLocationField.addBrowseFolderListener("Select Base Directory",
                                            "Select base directory for the project",
                                            project,
                                            descriptor);

    myLocationField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        String location = FileUtil.toSystemDependentName(myLocationField.getText());
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

  @NotNull
  protected File findSequentNonExistingUntitled() {
    return FileUtil.findSequentNonexistentFile(new File(ProjectUtil.getBaseDir()), "course", "");
  }

  private void setState(boolean isVisible) {
    myErrorIcon.setVisible(isVisible);
    myErrorLabel.setVisible(isVisible);
  }

  void setError(@NotNull String message) {
    myErrorLabel.setText(message);
    setState(true);
  }

  public String getLocationPath() {
    return FileUtil.toSystemDependentName(myLocationField.getText());
  }

  public void resetError() {
    setState(false);
  }

  public void setDescription(@NotNull String description) {
    myDescription.setText(description);
  }
}
