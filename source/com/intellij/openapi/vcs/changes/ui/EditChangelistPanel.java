package com.intellij.openapi.vcs.changes.ui;

import javax.swing.*;

/**
 * @author max
 */
public class EditChangelistPanel {
  private JTextField myNameTextField;
  private JTextArea myDescriptionTextArea;
  private JPanel myContent;

  public void setName(String s) {
    myNameTextField.setText(s);
  }

  public String getName() {
    return myNameTextField.getText();
  }

  public void setDescription(String s) {
    myDescriptionTextArea.setText(s);
  }

  public String getDescription() {
    return myDescriptionTextArea.getText();
  }

  public JComponent getContent() {
    return myContent;
  }

  public void setEnabled(boolean b) {
    myNameTextField.setEnabled(b);
    myDescriptionTextArea.setEnabled(b);
  }

  public void requestFocus() {
    myNameTextField.requestFocus();
  }

  public JComponent getPrefferedFocusedComponent() {
    return myNameTextField;
  }
}
