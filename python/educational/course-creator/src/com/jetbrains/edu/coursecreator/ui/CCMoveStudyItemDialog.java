package com.jetbrains.edu.coursecreator.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class CCMoveStudyItemDialog extends DialogWrapper {
  private final CCItemPositionPanel myPanel;

  public CCMoveStudyItemDialog(@Nullable Project project, String itemName, String thresholdName) {
    super(project);
    myPanel = new CCItemPositionPanel(itemName, thresholdName);
    setTitle("Move " + StringUtil.toTitleCase(itemName));
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public int getIndexDelta() {
    return myPanel.getIndexDelta();
  }
}
