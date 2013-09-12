package com.intellij.ide.browsers;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class StartBrowserPanel extends JPanel {
  private JCheckBox myStartBrowserCheckBox;
  private JComponent myBrowserComboBox;

  private JCheckBox myStartJavaScriptDebuggerCheckBox;

  private JTextField myStartupPage;
  private BrowserSelector myBrowserSelector;

  @SuppressWarnings("UnusedDeclaration")
  private JPanel ignored;

  public StartBrowserPanel() {
    myStartBrowserCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        boolean selected = myStartBrowserCheckBox.isSelected();
        myStartupPage.setEnabled(selected);
        myBrowserComboBox.setEnabled(selected);
        myStartJavaScriptDebuggerCheckBox.setEnabled(selected);
      }
    });

    myStartJavaScriptDebuggerCheckBox.setVisible(JavaScriptDebuggerStarter.Util.EP_NAME.getExtensions().length > 0);
  }

  public boolean isSelected() {
    return myStartBrowserCheckBox.isSelected();
  }

  public void setSelected(boolean value) {
    myStartBrowserCheckBox.setSelected(value);
    if (!value) {
      myStartupPage.setEnabled(false);
      myBrowserComboBox.setEnabled(false);
      myStartJavaScriptDebuggerCheckBox.setEnabled(false);
    }
  }

  public JCheckBox getStartJavaScriptDebuggerCheckBox() {
    return myStartJavaScriptDebuggerCheckBox;
  }

  public BrowserSelector getBrowserSelector() {
    return myBrowserSelector;
  }

  public JTextField getStartupPageField() {
    return myStartupPage;
  }

  private void createUIComponents() {
    myBrowserSelector = new BrowserSelector(true);
    myBrowserComboBox = myBrowserSelector.getMainComponent();
  }
}