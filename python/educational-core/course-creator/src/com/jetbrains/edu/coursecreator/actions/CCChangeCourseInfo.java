package com.jetbrains.edu.coursecreator.actions;

import com.intellij.ide.IdeView;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiDirectory;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.coursecreator.CCProjectService;
import com.jetbrains.edu.coursecreator.ui.CCNewProjectPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class CCChangeCourseInfo extends DumbAwareAction {
  public CCChangeCourseInfo() {
    super("Change Course Information", "Change Course Information", null);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    if (!CCProjectService.setCCActionAvailable(event)) {
      return;
    }
    final Presentation presentation = event.getPresentation();
    presentation.setVisible(false);
    presentation.setEnabled(false);
    final Project project = event.getData(CommonDataKeys.PROJECT);
    if (project == null) {
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
    final PsiDirectory directory = DirectoryChooserUtil.getOrChooseDirectory(view);
    if (directory != null && !project.getBaseDir().equals(directory.getVirtualFile())) {
      return;
    }
    presentation.setVisible(true);
    presentation.setEnabled(true);

  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    if (!CCProjectService.setCCActionAvailable(e)) {
      return;
    }
    final Project project = e.getProject();
    if (project == null) {
      return;
    }
    Course course = CCProjectService.getInstance(project).getCourse();
    if (course == null) {
      return;
    }
    final IdeView view = e.getData(LangDataKeys.IDE_VIEW);
    if (view == null) {
      return;
    }
    final PsiDirectory[] directories = view.getDirectories();
    if (directories.length == 0) {
      return;
    }
    final PsiDirectory directory = DirectoryChooserUtil.getOrChooseDirectory(view);
    if (directory != null && !project.getBaseDir().equals(directory.getVirtualFile())) {
      return;
    }
    CCNewProjectPanel panel = new CCNewProjectPanel(course.getName(), Course.getAuthorsString(course.getAuthors()), course.getDescription());
    ChangeCourseInfoDialog changeCourseInfoDialog =
      new ChangeCourseInfoDialog(project, panel);
    changeCourseInfoDialog.show();
    if (changeCourseInfoDialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      course.setAuthors(panel.getAuthors());
      course.setName(panel.getName());
      course.setDescription(panel.getDescription());
      ProjectView.getInstance(project).refresh();
    }
  }

  static class ChangeCourseInfoDialog extends DialogWrapper {

    private final JPanel myMainPanel;

    public ChangeCourseInfoDialog(@Nullable Project project, CCNewProjectPanel panel) {
      super(project);
      setTitle("Change Course Information");
      myMainPanel = panel.getMainPanel();
      init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      myMainPanel.setPreferredSize(new Dimension(400, 300));
      myMainPanel.setSize(new Dimension(400, 300));
      myMainPanel.setMaximumSize(new Dimension(400, 300));
      return myMainPanel;
    }
  }
}
