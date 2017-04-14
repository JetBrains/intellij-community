package com.jetbrains.edu.learning.ui;

import com.intellij.ide.BrowserUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.jetbrains.edu.learning.stepic.EduStepicNames;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class AuthorizationPanel {
  private JPanel myContentPanel;
  private JBLabel mySignUpLabel;
  private JBTextField myCodeTextField;

  public AuthorizationPanel() {
    
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

  public String getCode() {
    return myCodeTextField.getText();
  }


  public JComponent getPreferableFocusComponent() {
    return myCodeTextField;
  }
}
