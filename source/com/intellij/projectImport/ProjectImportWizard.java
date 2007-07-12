package com.intellij.projectImport;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.util.projectWizard.AddModuleWizard;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;

/**
 * @author Vladislav.Kaznacheev
 */
public abstract class ProjectImportWizard implements ProjectImportProvider {
  protected String myNewProjectName;
  protected String myNewProjectFilePath;
  protected ProjectJdk myNewProjectJdk;
  protected String myNewCompileOutput;

  @NonNls private static final String DEFAULT_OUTPUT = "classes";

  public void doImport(final Project currentProject, boolean forceOpenInNewFrame) {
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
      initImport(currentProject, updateCurrent);

      final AddModuleWizard dialog = new AddModuleWizard(title, getStepsFactory(currentProject, updateCurrent), !updateCurrent);
      dialog.show();
      if (!dialog.isOK()) {
        return;
      }

      myNewProjectFilePath = dialog.getNewProjectFilePath();
      myNewProjectJdk = dialog.getNewProjectJdk();
      myNewCompileOutput = dialog.getNewCompileOutput();

      final Project projectToUpdate = performImport(currentProject, updateCurrent, forceOpenInNewFrame);

      if (projectToUpdate == null) {
        return;
      }

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

  @Nullable
  private Project performImport(final Project currentProject, final boolean updateCurrent, final boolean forceOpenInNewFrame) {
    final Project projectToUpdate =
      updateCurrent ? currentProject : ProjectManagerEx.getInstanceEx().newProject(myNewProjectFilePath, true, false);

    if (!beforeProjectOpen(currentProject, projectToUpdate)) {
      return null;
    }

    if (!updateCurrent) {
      setProjectParameters(projectToUpdate);
      if(!forceOpenInNewFrame){
        ProjectUtil.closePreviousProject(currentProject);
      }
      ProjectUtil.updateLastProjectLocation(myNewProjectFilePath);
      ProjectManagerEx.getInstanceEx().openProject(projectToUpdate);
    }

    afterProjectOpen(projectToUpdate);

    return projectToUpdate;
  }

  private void setProjectParameters(final Project project) {
    final ProjectRootManagerEx rootManager = ProjectRootManagerEx.getInstanceEx(project);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        if (myNewProjectJdk != null) {
          final String versionString = myNewProjectJdk.getVersionString();
          if (versionString != null) {
            rootManager.setProjectJdk(myNewProjectJdk);
            rootManager.setLanguageLevel(ProjectUtil.getDefaultLanguageLevel(versionString));
          }
        }

        if (myNewCompileOutput != null) {
          rootManager.setCompilerOutputUrl(getUrl(myNewCompileOutput));
        }
      }
    });
  }

  public static String getUrl(String path) {
    try {
      path = FileUtil.resolveShortWindowsName(path);
    }
    catch (IOException e) {
      //file doesn't exist
    }
    return VfsUtil.pathToUrl(FileUtil.toSystemIndependentName(path));
  }

  protected String getTitle() {
    return IdeBundle.message("project.import.wizard.title", getName());
  }

  @Nullable
  public Icon getIcon(final VirtualFile file, final boolean open) {
    return null;
  }

  public boolean canOpenProject(final VirtualFile file) {
    return false;
  }

  @Nullable
  public Project doOpenProject(@NotNull VirtualFile virtualFile, Project projectToClose, boolean forceOpenInNewFrame) {
    initImport(null, false);
    if (doQuickImport(virtualFile)) {
      if (myNewProjectFilePath == null) {
        if (myNewProjectName == null) {
          myNewProjectName = IdeBundle.message("project.import.default.name", getName()) + ProjectFileType.DOT_DEFAULT_EXTENSION;
        }
        myNewProjectFilePath = getSiblingPath(virtualFile.getPath(), myNewProjectName);
      }
      if (myNewProjectJdk == null) {
        for (ProjectJdk projectJdk : ProjectJdkTable.getInstance().getAllJdks()) {
          if (myNewProjectJdk == null || myNewProjectJdk.getVersionString().compareTo(projectJdk.getVersionString()) < 0) {
            myNewProjectJdk = projectJdk;
          }
        }
      }
      if (myNewCompileOutput != null) {
        myNewCompileOutput = getUrl(getSiblingPath(myNewProjectFilePath, DEFAULT_OUTPUT));
      }
      return performImport(projectToClose, false, forceOpenInNewFrame);
    }
    return null;
  }

  private static String getSiblingPath(final String path, final String relPath) {
    return new File(new File(path).getParent(), relPath).getPath();
  }

  protected boolean doQuickImport(VirtualFile file) {
    return false;
  }

  protected abstract AddModuleWizard.ModuleWizardStepFactory getStepsFactory(final Project currentProject, final boolean updateCurrent);

  protected void cleanup() {
  }

  protected void initImport(final Project currentProject, final boolean updateCurrent) {
    myNewProjectName = null;
    myNewProjectFilePath = null;
    myNewProjectJdk = null;
    myNewCompileOutput = null;
  }

  protected boolean beforeProjectOpen(final Project currentProject, final Project dstProject) {
    return true;
  }

  protected abstract void afterProjectOpen(final Project project);

  protected boolean isOpenProjectSettingsAfter() {
    return false;
  }
}
