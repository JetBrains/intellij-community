package com.jetbrains.edu.coursecreator;

import com.intellij.ide.util.projectWizard.AbstractNewProjectDialog;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.jetbrains.edu.learning.statistics.EduUsagesCollector;

public class PyCCCreateProjectFromArchive extends DumbAwareAction {
  public PyCCCreateProjectFromArchive() {
    super("Preview Course");
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final Module module = e.getData(LangDataKeys.MODULE);
    if (project == null || module == null) {
      return;
    }

    EduUsagesCollector.coursePreviewed();
    AbstractNewProjectDialog dialog = new AbstractNewProjectDialog() {
      @Override
      protected DefaultActionGroup createRootStep() {
        return new CreateFromArchiveProjectStep(project, module);
      }
    };
    dialog.show();
  }

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getProject();
    presentation.setEnabledAndVisible(project != null && CCUtils.isCourseCreator(project));
  }
}
