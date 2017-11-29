package com.jetbrains.edu.learning.newproject;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.PlatformUtils;
import com.jetbrains.edu.learning.EduPluginConfigurator;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.newproject.ui.EduCoursesPanel;
import com.jetbrains.edu.learning.newproject.ui.EduCreateNewProjectDialog;
import icons.EducationalCoreIcons;

public class EduBrowseCoursesAction extends AnAction {
  public EduBrowseCoursesAction() {
    super("Browse Courses", "Browse list of available courses", EducationalCoreIcons.Course);
  }

  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(PlatformUtils.isJetBrainsProduct());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    EduCoursesPanel panel = new EduCoursesPanel();
    DialogBuilder dialogBuilder = new DialogBuilder().title("Select Course").centerPanel(panel);
    dialogBuilder.addOkAction().setText("Join");
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
