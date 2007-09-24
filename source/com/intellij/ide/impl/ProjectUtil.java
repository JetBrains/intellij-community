package com.intellij.ide.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.util.newProjectWizard.AddModuleWizard;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.projectImport.ProjectOpenProcessor;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;

/**
 * @author Eugene Belyaev
 */
public class ProjectUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.impl.ProjectUtil");

  private ProjectUtil() {
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
                  if (projectRootManager.getLanguageLevel().compareTo(languageLevel) > 0) {
                    projectRootManager.setLanguageLevel(languageLevel);
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
              final ToolWindow toolWindow = ToolWindowManager.getInstance(newProject).getToolWindow(ToolWindowId.PROJECT_VIEW);
              if (toolWindow != null) {
                toolWindow.activate(null);
              }
              if (need2OpenProjectStructure) {
                ModulesConfigurator.showDialog(newProject, null, null, true);
              }
            }
          });
        }
      });

      if (newProject != projectToClose) {
        updateLastProjectLocation(projectFilePath);

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
        closeProject(projectToClose != null ? projectToClose : openProjects[openProjects.length - 1]);
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

  public static void updateLastProjectLocation(final String projectFilePath) {
    File lastProjectLocation = new File(projectFilePath).getParentFile();
    if (lastProjectLocation == null) { // the immediate parent of the ipr file
      return;
    }
    lastProjectLocation = lastProjectLocation.getParentFile(); // the candidate directory to be saved
    if (lastProjectLocation == null) {
      return;
    }
    String path = lastProjectLocation.getPath();
    try {
      path = FileUtil.resolveShortWindowsName(path);
    }
    catch (IOException e) {
      LOG.info(e);
      return;
    }
    GeneralSettings.getInstance().setLastProjectLocation(path.replace(File.separatorChar, '/'));
  }

  /**
   * @param project cannot be null
   */
  public static boolean closeProject(@NotNull Project project) {
    if (!ProjectManagerEx.getInstanceEx().closeProject(project)) return false;
    Disposer.dispose(project);
    return true;
  }

  /**
   * @param path                project file path
   * @param projectToClose      currently active project
   * @param forceOpenInNewFrame forces opening in new frame
   * @return true if the path was recognized as IDEA project file or one of the project formats supported by
   *         installed importers (regardless of opening/import result)
   */
  public static boolean openOrImport(@NotNull final String path, final Project projectToClose, boolean forceOpenInNewFrame) {
    final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);

    if (virtualFile == null) return false;

    if (path.endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION) || virtualFile.isDirectory()) {
      openProject(path, projectToClose, forceOpenInNewFrame);
      return true;
    }
    else {
      ProjectOpenProcessor provider = getImportProvider(virtualFile);
      if (provider != null) {
        provider.doOpenProject(virtualFile, projectToClose, forceOpenInNewFrame);
        return true;
      }
      return false;
    }
  }

  @Nullable
  public static ProjectOpenProcessor getImportProvider(VirtualFile file) {
    for (ProjectOpenProcessor provider : Extensions.getExtensions(ProjectOpenProcessor.EXTENSION_POINT_NAME)) {
      if (provider.canOpenProject(file)) {
        return provider;
      }
    }
    return null;
  }

  public static Project openProject(final String path, Project projectToClose, boolean forceOpenInNewFrame) {
    File file = new File(path);
    if (!file.exists()) {
      Messages.showMessageDialog(IdeBundle.message("error.project.file.does.not.exist", path), CommonBundle.getErrorTitle(),
                                 Messages.getErrorIcon());
      return null;
    }

    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : openProjects) {
      if (isSameProject(path, project)) {
        focusProjectWindow(project);
        return project;
      }
    }

    if (!forceOpenInNewFrame && openProjects.length > 0) {
      int exitCode = Messages.showDialog(IdeBundle.message("prompt.open.project.in.new.frame"), IdeBundle.message("title.open.project"),
                                         new String[]{IdeBundle.message("button.newframe"), IdeBundle.message("button.existingframe"),
                                           CommonBundle.getCancelButtonText()}, 1, Messages.getQuestionIcon());
      if (exitCode == 1) { // "No" option
        if (!closeProject(projectToClose != null ? projectToClose : openProjects[openProjects.length - 1])) return null;
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
      Messages.showMessageDialog(IdeBundle.message("error.cannot.load.project", e.getMessage()),
                                 IdeBundle.message("title.cannot.load.project"), Messages.getErrorIcon());
    }
    catch (JDOMException e) {
      LOG.info(e);
      Messages.showMessageDialog(IdeBundle.message("error.project.file.is.corrupted"), IdeBundle.message("title.cannot.load.project"),
                                 Messages.getErrorIcon());
    }
    catch (InvalidDataException e) {
      LOG.info(e);
      Messages.showMessageDialog(IdeBundle.message("error.project.file.is.corrupted"), IdeBundle.message("title.cannot.load.project"),
                                 Messages.getErrorIcon());
    }
    return project;
  }

  private static boolean isSameProject(String path, Project p) {
    String projectPath = ((ProjectImpl)p).getStateStore().getProjectFilePath();
    String p1 = FileUtil.toSystemIndependentName(path);
    String p2 = FileUtil.toSystemIndependentName(projectPath);
    return FileUtil.pathsEqual(p1, p2);
  }

  private static void focusProjectWindow(Project p) {
    JFrame f = WindowManager.getInstance().getFrame(p);
    f.requestFocus();
  }

  public static String mainModulePathByProjectPath(String path) {
    int dotIdx = path.lastIndexOf('.');
    return dotIdx >= 0 ? path.substring(0, dotIdx) + ModuleFileType.DOT_DEFAULT_EXTENSION : "";
  }

  public static String getInitialModuleRootPath(String projectFilePath) {
    return new File(projectFilePath).getParentFile().getAbsolutePath();
  }

  public static String getInitialModuleLocation(final String projectFilePath) {
    int dotIdx = projectFilePath.lastIndexOf('.');
    return dotIdx >= 0 ? projectFilePath.substring(0, dotIdx) + ModuleFileType.DOT_DEFAULT_EXTENSION : "";
  }

}
