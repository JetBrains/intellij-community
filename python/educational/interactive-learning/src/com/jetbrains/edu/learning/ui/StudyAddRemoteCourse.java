package com.jetbrains.edu.learning.ui;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class StudyAddRemoteCourse {
  private JPanel myContentPanel;
  private JPasswordField myPasswordField;
  private JTextField myLoginField;
  private JBLabel myErrorLabel;

  public StudyAddRemoteCourse() {
    myErrorLabel.setText("");
    final DocumentListener documentListener = new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        myErrorLabel.setText("");
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        myErrorLabel.setText("");
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        myErrorLabel.setText("");
      }
    };
    myLoginField.getDocument().addDocumentListener(documentListener);
    myPasswordField.getDocument().addDocumentListener(documentListener);
  }

  public JPanel getContentPanel() {
    return myContentPanel;
  }

  public String getPassword() {
    return String.valueOf(myPasswordField.getPassword());
  }

  public String getLogin() {
    return myLoginField.getText();
  }

  public void setError(@NotNull final String errorMessage) {
    myErrorLabel.setForeground(JBColor.RED);
    myErrorLabel.setText(errorMessage);
  }
}
