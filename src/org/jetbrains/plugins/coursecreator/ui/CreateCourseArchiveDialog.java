package org.jetbrains.plugins.coursecreator.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.coursecreator.actions.CreateCourseArchive;

import javax.swing.*;

public class CreateCourseArchiveDialog extends DialogWrapper {

  private CreateCourseArchivePanel myPanel;
  private CreateCourseArchive myAction;

  public CreateCourseArchiveDialog(@NotNull final  Project project, CreateCourseArchive action) {
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
