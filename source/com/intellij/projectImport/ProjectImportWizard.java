package com.intellij.projectImport;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.util.projectWizard.AddModuleWizard;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.ui.Messages;
import com.intellij.projectImport.eclipse.action.EclipseProjectImporter;

import javax.swing.*;

/**
 * @author Vladislav.Kaznacheev
 */
public abstract class ProjectImportWizard implements ProjectImportProvider {

  public void doImport(final Project currentProject) {
    final String title = getTitle();

    final String[] options = new String[]{IdeBundle.message("project.import.into.new.project"),
      IdeBundle.message("project.import.into.existing.project"), CommonBundle.getCancelButtonText()};
    int ret = currentProject == null
              ? 0
              : Messages.showDialog(IdeBundle.message("project.import.destination"), title, options, 0, Messages.getQuestionIcon());

    if (ret == 2 || ret == -1) { // Cancel clicked or Esc pressed
      return;
    }

    final boolean updateCurrent = ret != 0;

    try {
      final AddModuleWizard dialog = new AddModuleWizard(title, getStepsFactory(currentProject, updateCurrent), !updateCurrent);
      dialog.show();
      if (!dialog.isOK()) {
        return;
      }

      final Project projectToUpdate =
        updateCurrent ? currentProject : ProjectManagerEx.getInstanceEx().newProject(dialog.getNewProjectFilePath(), true, false);

      if (!initImport(currentProject, projectToUpdate)) {
        return;
      }

      if (!updateCurrent) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            setProjectParameters(projectToUpdate, dialog.getNewProjectJdk(), dialog.getNewCompileOutput());
          }
        });
        ProjectUtil.closePreviousProject(currentProject);
        ProjectUtil.updateLastProjectLocation(dialog.getNewProjectFilePath());
        ProjectManagerEx.getInstanceEx().openProject(projectToUpdate);
      }

      commitImport(projectToUpdate);

      if (isOpenProjectSettingsAfter()) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            ModulesConfigurator.showDialog(projectToUpdate, null, null, false);
          }
        });
      }
    }
    finally {
      cleanup();
    }
  }

  private static void setProjectParameters(final Project project, final ProjectJdk jdk, final String compilerOutput) {
    final ProjectRootManagerEx rootManager = ProjectRootManagerEx.getInstanceEx(project);

    if (jdk != null) {
      final String versionString = jdk.getVersionString();
      if (versionString != null) {
        rootManager.setProjectJdk(jdk);
        rootManager.setLanguageLevel(ProjectUtil.getDefaultLanguageLevel(versionString));
      }
    }

    rootManager.setCompilerOutputUrl(EclipseProjectImporter.getUrl(compilerOutput));
  }

  protected String getTitle() {
    return IdeBundle.message("project.import.wizard.title", getName());
  }

  protected abstract AddModuleWizard.ModuleWizardStepFactory getStepsFactory(final Project currentProject, final boolean updateCurrent);

  protected void cleanup() {
  }

  protected boolean initImport(final Project currentProject, final Project dstProject) {
    return true;
  }

  protected abstract void commitImport(final Project project);

  protected boolean isOpenProjectSettingsAfter() {
    return false;
  }
}
