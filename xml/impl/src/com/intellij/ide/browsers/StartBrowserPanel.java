package com.intellij.ide.browsers;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class StartBrowserPanel {
  private JCheckBox myStartBrowserCheckBox;
  private JComponent myBrowserComboBox;

  private JCheckBox myStartJavaScriptDebuggerCheckBox;

  private JTextField myStartupPage;
  private BrowserSelector myBrowserSelector;

  private JPanel myRoot;
  private JLabel urlFieldLabel;

  public StartBrowserPanel() {
    myStartBrowserCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        setPanelEnabled(myStartBrowserCheckBox.isSelected());
      }
    });

    myStartJavaScriptDebuggerCheckBox.setVisible(JavaScriptDebuggerStarter.Util.EP_NAME.getExtensions().length > 0);
  }

  public JPanel getComponent() {
    return myRoot;
  }

  public void clearBorder() {
    myRoot.setBorder(null);
  }

  public boolean isSelected() {
    return myStartBrowserCheckBox.isSelected();
  }

  public void setSelected(boolean value) {
    myStartBrowserCheckBox.setSelected(value);
    setPanelEnabled(value);
  }

  private void setPanelEnabled(boolean enabled) {
    myBrowserComboBox.setEnabled(enabled);
    myStartJavaScriptDebuggerCheckBox.setEnabled(enabled);
    myStartupPage.setEnabled(enabled);
    urlFieldLabel.setEnabled(enabled);
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