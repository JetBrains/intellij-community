package com.jetbrains.edu.learning.ui;

import com.intellij.ide.BrowserUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBLabel;
import com.jetbrains.edu.learning.stepic.EduStepicNames;
import com.jetbrains.edu.learning.stepic.LoginDialog;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class LoginPanel {
  
  private JPanel myContentPanel;
  private JPasswordField myPasswordField;
  private JTextField myLoginField;
  private JBLabel mySignUpLabel;

  public LoginPanel(final LoginDialog dialog) {
    DocumentListener listener = new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        dialog.clearErrors();
      }
    };
    
    myLoginField.getDocument().addDocumentListener(listener);
    myPasswordField.getDocument().addDocumentListener(listener);
    
    mySignUpLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        BrowserUtil.browse(EduStepicNames.STEPIC_REGISTRATION_LINK);
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

  public JComponent getPreferableFocusComponent() {
    return myLoginField;
  }
}
