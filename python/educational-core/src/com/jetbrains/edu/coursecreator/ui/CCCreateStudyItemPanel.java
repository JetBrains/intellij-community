package com.jetbrains.edu.coursecreator.ui;

import javax.swing.*;
import java.awt.*;

public class CCCreateStudyItemPanel extends JPanel {
  private final String myItemName;
  private JPanel myPanel;
  private JTextField myNameField;
  private CCItemPositionPanel myPositionalPanel;
  private final String myThresholdName;

  public CCCreateStudyItemPanel(String itemName, String thresholdName, int thresholdIndex) {
    myThresholdName = thresholdName;
    myItemName = itemName;
    int newIndex = thresholdIndex + 1;
    myNameField.setText(itemName + newIndex);
    add(myPanel, BorderLayout.CENTER);
  }

  private void createUIComponents() {
    myPositionalPanel = new CCItemPositionPanel(myItemName, myThresholdName);
  }

  public String getItemName() {
    return myNameField.getText();
  }

  public int getIndexDelta() {
    return myPositionalPanel.getIndexDelta();
  }
}
