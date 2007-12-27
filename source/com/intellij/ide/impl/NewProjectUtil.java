/*
 * @author max
 */
package com.intellij.ide.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.newProjectWizard.AddModuleWizard;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.JavaProjectExtension;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;

public class NewProjectUtil {
  private NewProjectUtil() {
  }

  public static void createNewProject(Project projectToClose, @Nullable final String defaultPath) {
    final boolean proceed = ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        ProjectManager.getInstance().getDefaultProject(); //warm up components
      }
    }, ProjectBundle.message("project.new.wizard.progress.title"), true, null);
    if (!proceed) return;
    AddModuleWizard dialog = new AddModuleWizard(null, ModulesProvider.EMPTY_MODULES_PROVIDER, defaultPath);
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }

    final ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    final String projectFilePath = dialog.getNewProjectFilePath();
    final ProjectBuilder projectBuilder = dialog.getProjectBuilder();

    try {
      final Project newProject =
        projectBuilder == null || !projectBuilder.isUpdate() ? projectManager.newProject(projectFilePath, true, false) : projectToClose;

      final ProjectJdk jdk = dialog.getNewProjectJdk();
      if (jdk != null) {
        final String versionString = jdk.getVersionString();
        if (versionString != null) { //jdk is valid
          CommandProcessor.getInstance().executeCommand(newProject, new Runnable() {
            public void run() {
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                public void run() {
                  final ProjectRootManagerEx projectRootManager = (ProjectRootManagerEx)ProjectRootManager.getInstance(newProject);
                  projectRootManager.setProjectJdk(jdk);
                  final LanguageLevel languageLevel = getDefaultLanguageLevel(versionString);
                  final JavaProjectExtension projectExtension = JavaProjectExtension.getInstance(newProject);
                  if (projectExtension.getLanguageLevel().compareTo(languageLevel) > 0) {
                    projectExtension.setLanguageLevel(languageLevel);
                  }
                }
              });
            }
          }, null, null);
        }
      }

      final String compileOutput = dialog.getNewCompileOutput();
      CommandProcessor.getInstance().executeCommand(newProject, new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              final ProjectRootManagerEx projectRootManager = (ProjectRootManagerEx)ProjectRootManager.getInstance(newProject);
              String canonicalPath = compileOutput;
              try {
                canonicalPath = FileUtil.resolveShortWindowsName(compileOutput);
              }
              catch (IOException e) {
                //file doesn't exist
              }
              canonicalPath = FileUtil.toSystemIndependentName(canonicalPath);
              projectRootManager.setCompilerOutputUrl(VfsUtil.pathToUrl(canonicalPath));
            }
          });
        }
      }, null, null);

      newProject.save();


      if (projectBuilder != null && !projectBuilder.validate(projectToClose, newProject)) {
        return;
      }

      if (newProject != projectToClose) {
        closePreviousProject(projectToClose);
      }

      if (projectBuilder != null) {
        projectBuilder.commit(newProject);
      }

      final boolean need2OpenProjectStructure = projectBuilder == null || projectBuilder.isOpenProjectSettingsAfter();
      StartupManager.getInstance(newProject).registerPostStartupActivity(new Runnable() {
        public void run() {
          // ensure the dialog is shown after all startup activities are done
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              if (need2OpenProjectStructure) {
                ModulesConfigurator.showDialog(newProject, null, null, true);
              }
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                  final ToolWindow toolWindow = ToolWindowManager.getInstance(newProject).getToolWindow(ToolWindowId.PROJECT_VIEW);
                  if (toolWindow != null) {
                    toolWindow.activate(null);
                  }
                }
              }, ModalityState.NON_MODAL);
            }
          });
        }
      });

      if (newProject != projectToClose) {
        ProjectUtil.updateLastProjectLocation(projectFilePath);

        projectManager.openProject(newProject);
      }
    }
    finally {
      if (projectBuilder != null) {
        projectBuilder.cleanup();
      }
    }
  }

  public static void closePreviousProject(final Project projectToClose) {
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    if (openProjects.length > 0) {
      int exitCode = Messages.showDialog(IdeBundle.message("prompt.open.project.in.new.frame"), IdeBundle.message("title.new.project"),
                                         new String[]{IdeBundle.message("button.newframe"), IdeBundle.message("button.existingframe")}, 1,
                                         Messages.getQuestionIcon());
      if (exitCode == 1) { // "No" option
        ProjectUtil.closeProject(projectToClose != null ? projectToClose : openProjects[openProjects.length - 1]);
      }
    }
  }

  public static LanguageLevel getDefaultLanguageLevel(@NotNull String versionString) {
    if (isOfVersionOrHigher(versionString, "1.5") || isOfVersionOrHigher(versionString, "5.0")) {
      return LanguageLevel.JDK_1_5;
    }

    if (isOfVersionOrHigher(versionString, "1.4")) {
      return LanguageLevel.JDK_1_4;
    }

    return LanguageLevel.JDK_1_3;
  }

  private static boolean isOfVersionOrHigher(@NotNull String versionString, String checkedVersion) {
    return JavaSdk.getInstance().compareTo(versionString, checkedVersion) >= 0;
  }
}