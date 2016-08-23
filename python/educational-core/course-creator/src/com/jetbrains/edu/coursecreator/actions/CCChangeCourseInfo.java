package com.jetbrains.edu.coursecreator.actions;

import com.intellij.codeInspection.InspectionProfile;
import com.intellij.ide.IdeView;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiDirectory;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.coursecreator.ui.CCNewProjectPanel;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.courseFormat.Course;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class CCChangeCourseInfo extends DumbAwareAction {
  private static final String ACTION_TEXT = "Change Course Information";

  public CCChangeCourseInfo() {
    super(ACTION_TEXT, ACTION_TEXT, null);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    final Project project = event.getProject();
    final Presentation presentation = event.getPresentation();
    if (project == null) {
      return;
    }
    presentation.setEnabledAndVisible(false);
    if (!CCUtils.isCourseCreator(project)) {
      return;
    }
    final IdeView view = event.getData(LangDataKeys.IDE_VIEW);
    if (view == null) {
      return;
    }
    final PsiDirectory[] directories = view.getDirectories();
    if (directories.length == 0) {
      return;
    }
    presentation.setEnabledAndVisible(true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) {
      return;
    }

    CCNewProjectPanel panel =
      new CCNewProjectPanel(course.getName(), Course.getAuthorsString(course.getAuthors()), course.getDescription());
    setupLanguageLevels(course, panel);
    DialogBuilder builder = createChangeInfoDialog(project, panel);
    if (builder.showAndGet()) {
      course.setAuthorsAsString(panel.getAuthors());
      course.setName(panel.getName());
      course.setDescription(panel.getDescription());
      setVersion(course, panel);
      ProjectView.getInstance(project).refresh();
      final InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
      InspectionProfileManager.getInstance().fireProfileChanged(profile);
    }
  }

  protected void setVersion(Course course, CCNewProjectPanel panel) {}

  protected void setupLanguageLevels(Course course, CCNewProjectPanel panel) {}

  private static DialogBuilder createChangeInfoDialog(Project project, @NotNull CCNewProjectPanel panel) {
    DialogBuilder builder = new DialogBuilder(project);

    builder.setTitle(ACTION_TEXT);
    JPanel changeInfoPanel = panel.getMainPanel();
    changeInfoPanel.setPreferredSize(new Dimension(450, 300));
    changeInfoPanel.setMinimumSize(new Dimension(450, 300));
    builder.setCenterPanel(changeInfoPanel);

    return builder;
  }
}
