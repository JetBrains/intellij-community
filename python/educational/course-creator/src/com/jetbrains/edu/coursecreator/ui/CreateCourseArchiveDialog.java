package com.jetbrains.edu.coursecreator.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.jetbrains.edu.coursecreator.actions.CCCreateCourseArchive;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class CreateCourseArchiveDialog extends DialogWrapper {

  private CreateCourseArchivePanel myPanel;
  private CCCreateCourseArchive myAction;

  public CreateCourseArchiveDialog(@NotNull final  Project project, CCCreateCourseArchive action) {
    super(project);
    setTitle("Create Course Archive");
    myPanel = new CreateCourseArchivePanel(project, this);
    myAction = action;
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public void enableOKAction(boolean isEnabled) {
    myOKAction.setEnabled(isEnabled);
  }

  @Override
  protected void doOKAction() {
    myAction.setZipName(myPanel.getZipName());
    myAction.setLocationDir(myPanel.getLocationPath());
    super.doOKAction();
  }
}
