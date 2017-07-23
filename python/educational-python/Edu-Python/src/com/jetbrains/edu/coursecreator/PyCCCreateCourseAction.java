package com.jetbrains.edu.coursecreator;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.projectWizard.AbstractNewProjectDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import icons.EducationalCoreIcons;

import static com.jetbrains.edu.coursecreator.actions.CCPluginToggleAction.COURSE_CREATOR_ENABLED;

public class PyCCCreateCourseAction extends AnAction {
  public PyCCCreateCourseAction() {
    super("Create New Course", "Create New Course", EducationalCoreIcons.EducationalProjectType);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    AbstractNewProjectDialog dialog = new AbstractNewProjectDialog() {
      @Override
      protected DefaultActionGroup createRootStep() {
        return new PyCCCreateCourseProjectStep();
      }
    };
    dialog.show();
  }

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(PropertiesComponent.getInstance().getBoolean(COURSE_CREATOR_ENABLED));
  }
}
