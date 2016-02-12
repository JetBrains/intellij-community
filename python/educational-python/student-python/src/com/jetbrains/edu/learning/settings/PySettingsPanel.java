package com.jetbrains.edu.learning.settings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;


public class PySettingsPanel implements ModifiableSettingsPanel{
  private JBCheckBox myAskToTweetCheckBox;
  private JPanel myPanel;
  private boolean myIsModified = false;

  public PySettingsPanel() {
    myAskToTweetCheckBox.addActionListener(e -> myIsModified = true);
    myAskToTweetCheckBox.setSelected(PyStudySettings.getInstance(ProjectUtil.guessCurrentProject(myPanel)).askToTweet());
    myPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UIUtil.getBoundsColor()));
  }

  @Override
  public void apply() {
    Project project = ProjectUtil.guessCurrentProject(myPanel);
    PyStudySettings.getInstance(project).setAskToTweet(myAskToTweetCheckBox.isSelected());
  }

  @Override
  public void reset() {
    Project project = ProjectUtil.guessCurrentProject(myPanel);
    PyStudySettings.getInstance(project).setAskToTweet(true);
  }

  @Override
  public void resetCredentialsModification() {
    myIsModified = false;
  }

  @Override
  public boolean isModified() {
    return myIsModified;
  }

  @NotNull
  public JPanel getPanel() {
    return myPanel;
  }
}
