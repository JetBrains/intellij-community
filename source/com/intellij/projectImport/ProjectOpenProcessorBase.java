/*
 * User: anna
 * Date: 12-Jul-2007
 */
package com.intellij.projectImport;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jdom.JDOMException;

import javax.swing.*;
import java.io.File;
import java.io.IOException;

public abstract class ProjectOpenProcessorBase extends ProjectOpenProcessor {

  private ProjectImportBuilder myBuilder;

  protected ProjectOpenProcessorBase(final ProjectImportBuilder builder) {
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
    if (!doQuickImport(virtualFile, wizardContext)) return null;

    if (wizardContext.getProjectName() == null) {
      wizardContext.setProjectName(IdeBundle.message("project.import.default.name", getName()) + ProjectFileType.DOT_DEFAULT_EXTENSION);
    }
    wizardContext.setProjectFileDirectory(virtualFile.getParent().getPath());
    for (Sdk projectJdk : ProjectJdkTable.getInstance().getAllJdks()) {
      if (projectJdk.getSdkType() instanceof JavaSdk) {
        final String jdkVersion = projectJdk.getVersionString();
        if (wizardContext.getProjectJdk() == null) {
          wizardContext.setProjectJdk(projectJdk);
        }
        else {
          final String version = wizardContext.getProjectJdk().getVersionString();
          if (jdkVersion == null || (version != null && version.compareTo(jdkVersion) < 0)) {
            wizardContext.setProjectJdk(projectJdk);
          }
        }
      }
    }


    final String projectPath = wizardContext.getProjectFileDirectory() + File.separator + wizardContext.getProjectName() +
                                  ProjectFileType.DOT_DEFAULT_EXTENSION;
    boolean shouldOpenExisting = false;

    File projectFile = new File(projectPath);
    if (!ApplicationManager.getApplication().isHeadlessEnvironment()
        && projectFile.exists()) {
      int result = Messages.showDialog(projectToClose,
                                       IdeBundle.message("project.import.open.existing",
                                                         projectFile.getName(),
                                                         projectFile.getParent(),
                                                         virtualFile.getName()),
                                       IdeBundle.message("title.open.project"),
                                       new String[] {
                                           IdeBundle.message("project.import.open.existing.reimport"),
                                           IdeBundle.message("project.import.open.existing.openExisting"),
                                           CommonBundle.message("button.cancel")},
                                       0,
                                       Messages.getQuestionIcon());
      if (result == 2) return null;
      shouldOpenExisting = result == 1;
    }

    final Project projectToOpen;
    if (shouldOpenExisting) {
      try {
        projectToOpen = ProjectManagerEx.getInstanceEx().loadProject(projectPath);
      }
      catch (IOException e) {
        return null;
      }
      catch (JDOMException e) {
        return null;
      }
      catch (InvalidDataException e) {
        return null;
      }
    }
    else {
      projectToOpen = ProjectManagerEx.getInstanceEx().newProject(projectPath, true, false);

      if (projectToOpen == null || !getBuilder().validate(projectToClose, projectToOpen)) {
        return null;
      }

      projectToOpen.save();

      final ProjectRootManagerEx rootManager = ProjectRootManagerEx.getInstanceEx(projectToOpen);

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          if (wizardContext.getProjectJdk() != null) {
            final String versionString = wizardContext.getProjectJdk().getVersionString();
            if (versionString != null) {
              rootManager.setProjectJdk(wizardContext.getProjectJdk());
              LanguageLevelProjectExtension.getInstance(projectToOpen).setLanguageLevel(NewProjectUtil.getDefaultLanguageLevel(versionString));
            }
          }

          final String projectFilePath = wizardContext.getProjectFileDirectory();
          CompilerProjectExtension.getInstance(projectToOpen).setCompilerOutputUrl(getUrl(
            StringUtil.endsWithChar(projectFilePath, '/') ? projectFilePath + "classes" : projectFilePath + "/classes"));
        }
      });

      getBuilder().commit(projectToOpen);
    }

    if (!forceOpenInNewFrame) {
      NewProjectUtil.closePreviousProject(projectToClose);
    }
    ProjectUtil.updateLastProjectLocation(projectPath);
    ProjectManagerEx.getInstanceEx().openProject(projectToOpen);

    return projectToOpen;
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