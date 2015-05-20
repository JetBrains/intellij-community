package com.jetbrains.edu.coursecreator.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.jetbrains.edu.EduNames;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

public class CreateCourseArchivePanel extends JPanel {
  private JPanel myPanel;
  private JTextField myNameField;
  private TextFieldWithBrowseButton myLocationField;
  private JLabel myErrorIcon;
  private JLabel myErrorLabel;
  private CreateCourseArchiveDialog myDlg;

  public CreateCourseArchivePanel(@NotNull final Project project, CreateCourseArchiveDialog dlg) {
    setLayout(new BorderLayout());
    add(myPanel, BorderLayout.CENTER);
    myErrorIcon.setIcon(AllIcons.Actions.Lightning);
    setState(false);
    myDlg = dlg;
    myNameField.setText(EduNames.COURSE);
    myLocationField.setText(project.getBasePath());
    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    myLocationField.addBrowseFolderListener("Choose location folder", null, project, descriptor);
    myLocationField.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        String location = myLocationField.getText();
        File file = new File(location);
        if (!file.exists() || !file.isDirectory()) {
          myDlg.enableOKAction(false);
          setError("Invalid location");
        }
        myDlg.enableOKAction(true);
      }
    });
  }

  private void setState(boolean isVisible) {
    myErrorIcon.setVisible(isVisible);
    myErrorLabel.setVisible(isVisible);
  }

  private void setError(String message) {
    myErrorLabel.setText(message);
    setState(true);
  }

  public String getZipName() {
    return myNameField.getText();
  }

  public String getLocationPath() {
    return myLocationField.getText();
  }
}
