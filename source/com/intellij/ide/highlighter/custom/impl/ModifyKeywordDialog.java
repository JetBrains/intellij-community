package com.intellij.ide.highlighter.custom.impl;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;

import javax.swing.*;
import java.awt.*;

/**
 * @author Yura Cangea
 */
public class ModifyKeywordDialog extends DialogWrapper {
  private JTextField myKeywordName = new JTextField();

  public ModifyKeywordDialog(Component parent, String initialValue) {
    super(parent, false);
    if (initialValue == null || "".equals(initialValue)) {
      setTitle("Add New Keyword");
    } else {
      setTitle("Edit Keyword");
    }
    init();
    myKeywordName.setText(initialValue);
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gc = new GridBagConstraints();
    gc.gridx = 0;
    gc.gridy = 0;
    gc.weightx = 0;
    gc.insets = new Insets(5, 0, 5, 5);
    panel.add(new JLabel("Keyword: "), gc);

    gc = new GridBagConstraints();
    gc.gridx = 1;
    gc.gridy = 0;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.gridwidth = 2;
    gc.insets = new Insets(0, 0, 5, 0);
    panel.add(myKeywordName, gc);

    panel.setPreferredSize(new Dimension(220, 40));
    return panel;
  }

  protected JComponent createCenterPanel() {
    return null;
  }

  protected void doOKAction() {
    if (myKeywordName.getText().trim().length() == 0) {
      Messages.showMessageDialog(getContentPane(), "Keyword cannot be empty", "Error", Messages.getErrorIcon());
      return;
    }
    super.doOKAction();
  }

  public JComponent getPreferredFocusedComponent() {
    return myKeywordName;
  }

  public String getKeywordName() {
    return myKeywordName.getText();
  }
}
