package com.intellij.ide.impl;

import com.intellij.ide.util.projectWizard.AddModuleWizard;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.GeneralSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.pom.java.LanguageLevel;
import org.jdom.JDOMException;

import javax.swing.*;
import java.io.File;
import java.io.IOException;

/**
 * @author Eugene Belyaev
 */
public class ProjectUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.impl.ProjectUtil");

  public static void createNewProject(Project projectToClose) {
    AddModuleWizard dialog = new AddModuleWizard(null, ModulesProvider.EMPTY_MODULES_PROVIDER);
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }

    final ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    final String projectFilePath = dialog.getNewProjectFilePath();

    final Project newProject = projectManager.newProject(projectFilePath, true, false);

    final ProjectJdk jdk = dialog.getNewProjectJdk();
    if (jdk != null) {
      CommandProcessor.getInstance().executeCommand(newProject, new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              final ProjectRootManagerEx projectRootManager = (ProjectRootManagerEx)ProjectRootManager.getInstance(newProject);
              projectRootManager.setProjectJdk(jdk);
              projectRootManager.setLanguageLevel(getDefaultLanguageLevel(jdk.getVersionString()));
            }
          });
        }
      }, null, null);
    }

    newProject.save();

    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    if (openProjects.length > 0) {
      int exitCode = Messages.showDialog(
        "Would you like to open the project in a new frame?",
        "New Project",
        new String[]{"_Yes", "_No"},
        1,
        Messages.getQuestionIcon()
      );
      if (exitCode == 1) { // "No" option
        closeProject(projectToClose != null ? projectToClose : openProjects[openProjects.length - 1]);
      }
    }

    final ModuleBuilder moduleBuilder = dialog.getModuleBuilder();
    if (moduleBuilder != null) {
      Exception ex = ApplicationManager.getApplication().runWriteAction(new Computable<Exception>() {
        public Exception compute() {
          try {
            final ModifiableModuleModel moduleModel = ModuleManager.getInstance(newProject).getModifiableModel();
            final Module module = moduleBuilder.createModule(moduleModel);
            if (module != null) {
              moduleModel.commitAssertingNoCircularDependency();
            }
            return null;
          }
          catch (Exception e) {
            return e;
          }
        }
      });
      if (ex != null) {
        Messages.showErrorDialog("Error adding module to project: " + ex.getMessage(), "Add Module");
      }
    }

    StartupManager.getInstance(newProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        // ensure the dialog is shown after all startup activities are done
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            final ToolWindow toolWindow = ToolWindowManager.getInstance(newProject).getToolWindow(ToolWindowId.PROJECT_VIEW);
            if (toolWindow != null) {
              toolWindow.activate(null);
            }
            if (moduleBuilder == null) {
              ModulesConfigurator.showDialog(newProject, null, null, true);
            }
          }
        });
      }
    });

    updateLastProjectLocation(projectFilePath);

    projectManager.openProject(newProject);
  }

  private static LanguageLevel getDefaultLanguageLevel(String versionString) {
    if (isOfVersion(versionString, "1.5") || isOfVersion(versionString, "5.0")) {
      return LanguageLevel.JDK_1_5;
    }

    if (isOfVersion(versionString, "1.4")) {
      return LanguageLevel.JDK_1_4;
    }

    return LanguageLevel.JDK_1_3;
  }

  private static boolean isOfVersion(String versionString, String checkedVersion) {
    return versionString.indexOf(checkedVersion) > -1;
  }

  private static void updateLastProjectLocation(final String projectFilePath) {
    File lastProjectLocation = new File(projectFilePath).getParentFile();
    if (lastProjectLocation == null) { // the immediate parent of the ipr file
      return;
    }
    lastProjectLocation = lastProjectLocation.getParentFile(); // the candidate directory to be saved
    if (lastProjectLocation == null) {
      return;
    }
    try {
      lastProjectLocation = lastProjectLocation.getCanonicalFile();
    }
    catch (IOException e) {
      LOG.info(e);
      return ;
    }
    GeneralSettings.getInstance().setLastProjectLocation(lastProjectLocation.getPath().replace(File.separatorChar, '/'));
  }

  /**
   * @param project cannot be null
   */ 
  public static boolean closeProject(Project project) {
    if (project == null) {
      throw new IllegalArgumentException("project cannot be null");
    }

    if (!ProjectManagerEx.getInstanceEx().closeProject(project)) return false;
    ((ProjectEx)project).dispose();
    return true;
  }

  public static Project openProject(final String path, Project projectToClose, boolean forceOpenInNewFrame) {
    File file = new File(path);
    if (!file.exists()) {
      Messages.showMessageDialog("Cannot load " + path + ". The file does not exist.", "Error", Messages.getErrorIcon());
      return null;
    }

    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    for (int i = 0; i < openProjects.length; i++) {
      Project project = openProjects[i];
      if (Comparing.equal(path, project.getProjectFilePath())) {
        return project;
      }
    }

    if (!forceOpenInNewFrame && openProjects.length > 0) {
      int exitCode = Messages.showDialog(
        "Would you like to open the project in a new frame?",
        "Open Project",
        new String[]{"_Yes", "_No", "Cancel"},
        1,
        Messages.getQuestionIcon()
      );
      if (exitCode == 1) { // "No" option
        if(!closeProject(projectToClose != null ? projectToClose : openProjects[openProjects.length - 1])) return null;
      }
      else if (exitCode != 0) { // not "Yes"
        return null;
      }
    }

    ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    Project project = null;
    try {
      project = projectManager.loadAndOpenProject(path);
    }
    catch (IOException e) {
      Messages.showMessageDialog("Cannot load project: " + e.getMessage(), "Cannot Load Project",
                                 Messages.getErrorIcon());
    }
    catch (JDOMException e) {
      Messages.showMessageDialog("Project file is corrupted", "Cannot Load Project", Messages.getErrorIcon());
    }
    catch (InvalidDataException e) {
      Messages.showMessageDialog("Project file is corrupted", "Cannot Load Project", Messages.getErrorIcon());
    }
    return project;
  }

  public static String mainModulePathByProjectPath(String path) {
    int dotIdx = path.lastIndexOf('.');
    final String filePath = dotIdx >= 0 ? path.substring(0, dotIdx) + ".iml" : "";
    return filePath;
  }

  public static String getInitialModuleRootPath(String projectFilePath) {
    return new File(projectFilePath).getParentFile().getAbsolutePath();
  }

  public static String getInitialModuleLocation(final String projectFilePath) {
    int dotIdx = projectFilePath.lastIndexOf('.');
    final String filePath = dotIdx >= 0 ? projectFilePath.substring(0, dotIdx) + ".iml" : "";
    return filePath;
  }

}