package com.jetbrains.edu.learning.newproject;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.jetbrains.edu.learning.EduPluginConfigurator;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.newproject.ui.EduCoursesPanel;
import com.jetbrains.edu.learning.newproject.ui.EduCreateNewProjectDialog;
import icons.EducationalCoreIcons;

public class EduStartCourseAction extends AnAction {
  public EduStartCourseAction() {
    super("Browse Courses", "Browse list of available courses", EducationalCoreIcons.Course);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    EduCoursesPanel panel = new EduCoursesPanel();
    DialogBuilder dialogBuilder = new DialogBuilder().title("Select Course").centerPanel(panel);
    dialogBuilder.removeAllActions();
    dialogBuilder.addOkAction().setText("Start");
    panel.addCourseValidationListener(new EduCoursesPanel.CourseValidationListener() {
      @Override
      public void validationStatusChanged(boolean canStartCourse) {
        dialogBuilder.setOkActionEnabled(canStartCourse);
      }
    });
    dialogBuilder.setOkOperation(() -> {
      dialogBuilder.getDialogWrapper().close(DialogWrapper.OK_EXIT_CODE);
      Course course = panel.getSelectedCourse();
      String location = panel.getLocationString();
      EduCreateNewProjectDialog.createProject(EduPluginConfigurator.INSTANCE.forLanguage(course.getLanguageById()).getEduCourseProjectGenerator(), course, location);
    });
    dialogBuilder.show();
  }
}
