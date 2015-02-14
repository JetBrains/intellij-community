package com.jetbrains.edu.coursecreator.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public class CCCreateAnswerPlaceholderPanel extends JPanel {

  private final CCCreateAnswerPlaceholderDialog myDialog;
  private JPanel myPanel;
  private JTextArea myHintText;
  private JCheckBox myCreateHintCheckBox;
  private JLabel myHintTextLabel;
  private JTextField myAnswerPlaceholderText;
  private String myGeneratedHintName = "";

  public CCCreateAnswerPlaceholderPanel(CCCreateAnswerPlaceholderDialog dialog) {
    super(new BorderLayout());
    add(myPanel, BorderLayout.CENTER);
    myDialog = dialog;
    enableHint(false);
    myCreateHintCheckBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        int state = e.getStateChange();
        // 1 for checked
        enableHint(state == 1);
        if (state == 2) {
          resetHint();
        }
      }
    });

    myAnswerPlaceholderText.grabFocus();
  }

  private void enableHint(boolean isEnable) {
    myHintText.setEnabled(isEnable);
    myHintTextLabel.setEnabled(isEnable);
  }

  public void setAnswerPlaceholderText(String answerPlaceholderText) {
    myAnswerPlaceholderText.setText(answerPlaceholderText);
  }

  public void setHintText(String hintText) {
    myHintText.setText(hintText);
  }

  public String getAnswerPlaceholderText() {
    return myAnswerPlaceholderText.getText();
  }

  public String getHintText() {
    return myHintText.getText();
  }

  public void doClick() {
    myCreateHintCheckBox.doClick();
  }

  public void resetHint() {
    myHintText.setText("");
  }

  public void setGeneratedHintName(String generatedHintName) {
    myGeneratedHintName = generatedHintName;
  }

  public JComponent getPreferredFocusedComponent() {
    return myAnswerPlaceholderText;
  }
}
