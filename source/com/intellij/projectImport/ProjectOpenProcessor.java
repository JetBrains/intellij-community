/*
 * User: anna
 * Date: 12-Jul-2007
 */
package com.intellij.projectImport;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;

public abstract class ProjectOpenProcessor {
  public static final ExtensionPointName<ProjectOpenProcessor> EXTENSION_POINT_NAME = new ExtensionPointName<ProjectOpenProcessor>("com.intellij.projectOpenProcessor");

  private ProjectImportBuilder myBuilder;

  protected ProjectOpenProcessor(final ProjectImportBuilder builder) {
    myBuilder = builder;
  }

  public String getName() {
    return getBuilder().getName();
  }

  @Nullable
  public Icon getIcon(){
    return getBuilder().getIcon();
  }

  public boolean canOpenProject(final VirtualFile file) {
    final String[] supported = getSupportedExtensions();
    if (supported != null) {
      final String fileName = file.getName();
      for (String name : supported) {
        if (fileName.equals(name)) {
          return true;
        }
      }
    }
    return false;
  }

  protected boolean doQuickImport(VirtualFile file, final WizardContext wizardContext) {
    return false;
  }

  public ProjectImportBuilder getBuilder() {
    return myBuilder;
  }

  @Nullable
  public abstract String [] getSupportedExtensions();

  @Nullable
  public Project doOpenProject(@NotNull VirtualFile virtualFile, Project projectToClose, boolean forceOpenInNewFrame) {
    final WizardContext wizardContext = new WizardContext(null);
    if (doQuickImport(virtualFile, wizardContext)) {
      if (wizardContext.getProjectName() == null) {
        wizardContext.setProjectName(IdeBundle.message("project.import.default.name", getName()) + ProjectFileType.DOT_DEFAULT_EXTENSION);
      }
      wizardContext.setProjectFileDirectory(virtualFile.getParent().getPath());
      if (wizardContext.getProjectJdk() == null) {
        for (ProjectJdk projectJdk : ProjectJdkTable.getInstance().getAllJdks()) {
          if (wizardContext.getProjectJdk() == null ||
              wizardContext.getProjectJdk().getVersionString().compareTo(projectJdk.getVersionString()) < 0) {
            wizardContext.setProjectJdk(projectJdk);
          }
        }
      }

      final String newProjectPath = wizardContext.getProjectFileDirectory() + File.separator + wizardContext.getProjectName() +
                                    ProjectFileType.DOT_DEFAULT_EXTENSION;
      final Project projectToUpdate = ProjectManagerEx.getInstanceEx().newProject(newProjectPath, true, false);

      if (!getBuilder().validate(projectToClose, projectToUpdate)) {
        return null;
      }

      projectToUpdate.save();

      final ProjectRootManagerEx rootManager = ProjectRootManagerEx.getInstanceEx(projectToUpdate);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        if (wizardContext.getProjectJdk() != null) {
          final String versionString = wizardContext.getProjectJdk().getVersionString();
          if (versionString != null) {
            rootManager.setProjectJdk(wizardContext.getProjectJdk());
            rootManager.setLanguageLevel(ProjectUtil.getDefaultLanguageLevel(versionString));
          }
        }

        final String projectFilePath = wizardContext.getProjectFileDirectory();
        rootManager.setCompilerOutputUrl(getUrl(
          StringUtil.endsWithChar(projectFilePath, '/') ? projectFilePath + "classes" : projectFilePath + "/classes"));
      }
    });

      getBuilder().commit(projectToUpdate);
      if (!forceOpenInNewFrame) {
        ProjectUtil.closePreviousProject(projectToClose);
      }
      ProjectUtil.updateLastProjectLocation(newProjectPath);
      ProjectManagerEx.getInstanceEx().openProject(projectToUpdate);
      return projectToUpdate;
    }
    return null;
  }

  public static String getUrl(@NonNls String path) {
    try {
      path = FileUtil.resolveShortWindowsName(path);
    }
    catch (IOException e) {
      //file doesn't exist
    }
    return VfsUtil.pathToUrl(FileUtil.toSystemIndependentName(path));
  }
}