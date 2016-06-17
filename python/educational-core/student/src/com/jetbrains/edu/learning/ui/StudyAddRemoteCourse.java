package com.jetbrains.edu.learning.ui;

import com.intellij.ide.BrowserUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.jetbrains.edu.learning.stepic.EduStepicNames;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class StudyAddRemoteCourse {
  
  private JPanel myContentPanel;
  private JPasswordField myPasswordField;
  private JTextField myLoginField;
  private JBLabel myErrorLabel;
  private JBLabel mySignUpLabel;

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
    
    mySignUpLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        BrowserUtil.browse(EduStepicNames.STEPIC_SIGN_IN_LINK);
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        e.getComponent().setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      }

      @Override
      public void mouseExited(MouseEvent e) {
        e.getComponent().setCursor(Cursor.getDefaultCursor());
      }
    });
  }

  public JPanel getContentPanel() {
    return myContentPanel;
  }

  public JTextField getLoginField() {
    return myLoginField;
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
