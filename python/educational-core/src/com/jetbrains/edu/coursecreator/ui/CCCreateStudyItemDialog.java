package com.jetbrains.edu.coursecreator.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class CCCreateStudyItemDialog extends DialogWrapper {
  private final CCCreateStudyItemPanel myPanel;

  public CCCreateStudyItemDialog(@Nullable Project project, String itemName, String thresholdName, int thresholdIndex) {
    super(project);
    myPanel = new CCCreateStudyItemPanel(itemName, thresholdName, thresholdIndex);
    setTitle("Create New " + StringUtil.toTitleCase(itemName));
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
   return myPanel;
  }

  public String getName() {
    return myPanel.getItemName();
  }

  public int getIndexDelta() {
    return myPanel.getIndexDelta();
  }
}
