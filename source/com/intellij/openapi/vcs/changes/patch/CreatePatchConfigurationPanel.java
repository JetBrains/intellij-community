/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 14.11.2006
 * Time: 19:04:28
 */
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.ui.TextFieldWithBrowseButton;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.io.File;

public class CreatePatchConfigurationPanel {
  private JPanel myPanel;
  private TextFieldWithBrowseButton myFileNameField;

  public CreatePatchConfigurationPanel() {
    myFileNameField.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showSaveDialog(myFileNameField) != JFileChooser.APPROVE_OPTION) {
          return;
        }
        myFileNameField.setText(fileChooser.getSelectedFile().getPath());
      }
    });
  }

  public JPanel getPanel() {
    return myPanel;
  }

  public String getFileName() {
    return myFileNameField.getText();
  }

  public void setFileName(final File file) {
    myFileNameField.setText(file.getPath());
  }
}