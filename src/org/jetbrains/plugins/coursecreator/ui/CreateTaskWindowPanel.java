package org.jetbrains.plugins.coursecreator.ui;

import com.intellij.ui.DocumentAdapter;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public class CreateTaskWindowPanel extends JPanel {

  private final CreateTaskWindowDialog myDialog;
  private JPanel myPanel;
  private JTextArea myTaskWindowText;
  private JTextField myHintName;
  private JTextArea myHintText;
  private JCheckBox myCreateHintCheckBox;
  private JLabel myHintNameLabel;
  private JLabel myHintTextLabel;
  private String myGeneratedHintName = "";

  public CreateTaskWindowPanel(CreateTaskWindowDialog dialog) {
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
          myDialog.deleteHint();
        }
      }
    });

    myHintName.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        myDialog.validateInput();
      }
    });
  }

  public void enableHint(boolean isEnable) {
    myHintName.setEnabled(isEnable);
    myHintText.setEnabled(isEnable);
    myHintNameLabel.setEnabled(isEnable);
    myHintTextLabel.setEnabled(isEnable);
    myHintName.setText(myGeneratedHintName);
  }

  public void setTaskWindowText(String taskWindowText) {
    myTaskWindowText.setText(taskWindowText);
  }

  public void setHintName(String hintName) {
    myHintName.setText(hintName);
  }

  public void setHintText(String hintText) {
    myHintText.setText(hintText);
  }

  public String getTaskWindowText() {
    return myTaskWindowText.getText();
  }

  public String getHintName() {
    return myHintName.getText();
  }

  public String getHintText() {
    return myHintText.getText();
  }

  public boolean createHint() {
    return myHintName.isEnabled();
  }

  public void doClick() {
    myCreateHintCheckBox.doClick();
  }

  public void resetHint() {
    myHintName.setText("");
    myHintText.setText("");
  }

  public void setGeneratedHintName(String generatedHintName) {
    myGeneratedHintName = generatedHintName;
  }
}
