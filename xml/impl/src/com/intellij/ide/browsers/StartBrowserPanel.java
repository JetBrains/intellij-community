package com.intellij.ide.browsers;

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class StartBrowserPanel extends JPanel {
  private JCheckBox myStartBrowserCheckBox;
  private JComponent myBrowserComboBox;

  private JCheckBox myStartJavaScriptDebuggerCheckBox;

  private JTextField myStartupPage;
  private BrowserSelector myBrowserSelector;

  private JPanel myRoot;

  public StartBrowserPanel() {
    myStartBrowserCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        setPanelEnabled(myStartBrowserCheckBox.isSelected());
      }
    });

    myStartJavaScriptDebuggerCheckBox.setVisible(JavaScriptDebuggerStarter.Util.EP_NAME.getExtensions().length > 0);
  }

  public boolean isSelected() {
    return myStartBrowserCheckBox.isSelected();
  }

  public void setSelected(boolean value) {
    myStartBrowserCheckBox.setSelected(value);
    setPanelEnabled(value);
  }

  private void setPanelEnabled(boolean enabled) {
    UIUtil.setEnabled(myRoot, enabled, true);
    myStartBrowserCheckBox.setEnabled(true);
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

  public JPanel getRoot() {
    return myRoot;
  }

  private void createUIComponents() {
    myBrowserSelector = new BrowserSelector(true);
    myBrowserComboBox = myBrowserSelector.getMainComponent();
  }
}