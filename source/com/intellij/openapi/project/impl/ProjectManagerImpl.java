package com.intellij.openapi.project.impl;

import com.intellij.CommonBundle;
import com.intellij.application.options.PathMacrosImpl;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.project.ProjectReloadState;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.containers.HashMap;
import gnu.trove.TObjectLongHashMap;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ProjectManagerImpl extends ProjectManagerEx implements NamedJDOMExternalizable, ExportableApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.project.impl.ProjectManagerImpl");
  static final int CURRENT_FORMAT_VERSION = 4;

  private static final Key<ArrayList<ProjectManagerListener>> LISTENERS_IN_PROJECT_KEY = Key.create("LISTENERS_IN_PROJECT_KEY");
  @NonNls private static final String OLD_PROJECT_SUFFIX = "_old.";
  @NonNls private static final String ELEMENT_DEFAULT_PROJECT = "defaultProject";

  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
  private ProjectImpl myDefaultProject; // Only used asynchronously in save and dispose, which itself are synchronized.

  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
  private Element myDefaultProjectRootElement; // Only used asynchronously in save and dispose, which itself are synchronized.

  private final ArrayList<Project> myOpenProjects = new ArrayList<Project>();
  private final ArrayList<ProjectManagerListener> myListeners = new ArrayList<ProjectManagerListener>();

  /**
   * More then 0 while openProject is being executed: [openProject..runStartupActivities...runPostStartupActivitites].
   * This flag is required by SaveAndSynchHandler. We do not save
   * anything while project is being opened.
   */
  private int myCountOfProjectsBeingOpen;

  private boolean myIsInRefresh;
  private Map<VirtualFile, byte[]> mySavedCopies = new HashMap<VirtualFile, byte[]>();
  private TObjectLongHashMap<VirtualFile> mySavedTimestamps = new TObjectLongHashMap<VirtualFile>();
  private HashMap<Project, List<VirtualFile>> myChangedProjectFiles = new HashMap<Project, List<VirtualFile>>();
  private PathMacrosImpl myPathMacros;
  private VirtualFilePointerManager myFilePointerManager;

  private static ProjectManagerListener[] getListeners(Project project) {
    ArrayList<ProjectManagerListener> array = project.getUserData(LISTENERS_IN_PROJECT_KEY);
    if (array == null) return ProjectManagerListener.EMPTY_ARRAY;
    return array.toArray(new ProjectManagerListener[array.size()]);
  }

  /* @fabrique used by fabrique! */
  public ProjectManagerImpl(VirtualFileManagerEx virtualFileManagerEx,
                            PathMacrosImpl pathMacros,
                            VirtualFilePointerManager filePointerManager) {
    addProjectManagerListener(
      new ProjectManagerListener() {

        public void projectOpened(Project project) {
          ProjectManagerListener[] listeners = getListeners(project);
          for (ProjectManagerListener listener : listeners) {
            listener.projectOpened(project);
          }
        }

        public void projectClosed(Project project) {
          ProjectManagerListener[] listeners = getListeners(project);
          for (ProjectManagerListener listener : listeners) {
            listener.projectClosed(project);
          }
        }

        public boolean canCloseProject(Project project) {
          ProjectManagerListener[] listeners = getListeners(project);
          for (ProjectManagerListener listener : listeners) {
            if (!listener.canCloseProject(project)) {
              return false;
            }
          }
          return true;
        }

        public void projectClosing(Project project) {
          ProjectManagerListener[] listeners = getListeners(project);
          for (ProjectManagerListener listener : listeners) {
            listener.projectClosing(project);
          }
        }
      }
    );

    registerExternalProjectFileListener(virtualFileManagerEx);
    myPathMacros = pathMacros;
    myFilePointerManager = filePointerManager;
  }

  public void disposeComponent() {
    if (myDefaultProject != null) {
      Disposer.dispose(myDefaultProject);
      myDefaultProject = null;
    }
  }

  public void initComponent() {
  }

  public Project newProject(String filePath, boolean useDefaultProjectSettings, boolean isDummy) {
    filePath = canonicalize(filePath);

    ProjectImpl project = createProject(filePath, false, isDummy, ApplicationManager.getApplication().isUnitTestMode());

    if (useDefaultProjectSettings) {
      ProjectImpl defaultProject = (ProjectImpl)getDefaultProject();
      Element element = defaultProject.saveToXml(null, defaultProject.getProjectFile());
      try {
        project.loadFromXml(element, filePath);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
    project.init();
    return project;
  }

  private ProjectImpl createProject(String filePath, boolean isDefault, boolean isDummy, boolean isOptimiseTestLoadSpeed) {
    final ProjectImpl project = new ProjectImpl(this, filePath, isDefault, isOptimiseTestLoadSpeed, myPathMacros, myFilePointerManager);
    project.setDummy(isDummy);
    project.loadProjectComponents();
    return project;
  }

  public Project loadProject(String filePath) throws IOException, JDOMException, InvalidDataException {
    filePath = canonicalize(filePath);
    ProjectImpl project = createProject(filePath, false, false, false);

    // http://www.jetbrains.net/jira/browse/IDEA-1556. Enforce refresh. Project files may potentially reside in non-yet valid vfs paths.
    final ConfigurationFile[] files = project.getConfigurationFiles();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (ConfigurationFile file : files) {
          LocalFileSystem.getInstance().refreshAndFindFileByPath(file.getFilePath().replace(File.separatorChar, '/'));
        }
      }
    });

    final boolean macrosOk = checkMacros(project, getDefinedMacros());
    if (!macrosOk) {
      throw new IOException(ProjectBundle.message("project.load.undefined.path.variables.error"));
    }

    project.loadSavedConfiguration();

    project.init();
    return project;
  }

  private static String canonicalize(final String filePath) {
    if (filePath == null) return null;
    try {
      String canonicalPath = new File(filePath).getCanonicalPath();
      if (canonicalPath != null) {
        return canonicalPath;
      }
    }
    catch (IOException e) {
      // OK. File does not yet exist so it's canonical path will be equal to its original path.
    }

    return filePath;
  }

  private Set<String> getDefinedMacros() {
    Set<String> definedMacros = new HashSet<String>(myPathMacros.getUserMacroNames());
    definedMacros.addAll(myPathMacros.getSystemMacroNames());
    definedMacros = Collections.unmodifiableSet(definedMacros);
    return definedMacros;
  }

  private static boolean checkMacros(Project project, Set<String> definedMacros) throws IOException, JDOMException {
    String projectFilePath = project.getProjectFilePath();
    if (projectFilePath == null) {
      return true;
    }
    Document document = JDOMUtil.loadDocument(new File(projectFilePath));
    Element root = document.getRootElement();
    final Set<String> usedMacros = new HashSet<String>(Arrays.asList(ProjectImpl.readUsedMacros(root)));

    usedMacros.removeAll(definedMacros);
    if (usedMacros.isEmpty()) {
      return true; // all macros in configuration files are defined
    }
    // there are undefined macros, need to define them before loading components
    final String text = ProjectBundle.message("project.load.undefined.path.variables.message");
    return showMacrosConfigurationDialog(project, text, usedMacros);
  }

  private static boolean showMacrosConfigurationDialog(Project project, final String text, final Set<String> usedMacros) {
    final UndefinedMacrosConfigurable configurable =
      new UndefinedMacrosConfigurable(text, usedMacros.toArray(new String[usedMacros.size()]));
    final SingleConfigurableEditor editor = new SingleConfigurableEditor(project, configurable) {
      protected void doOKAction() {
        if (!getConfigurable().isModified()) {
          Messages.showErrorDialog(getContentPane(), ProjectBundle.message("project.load.undefined.path.variables.all.needed"),
                                   ProjectBundle.message("project.load.undefined.path.variables.title"));
          return;
        }
        super.doOKAction();
      }
    };
    editor.show();
    return editor.isOK();
  }

  public synchronized Project getDefaultProject() {
    if (myDefaultProject == null) {
      myDefaultProject = createProject(null, true, false, ApplicationManager.getApplication().isUnitTestMode());
      if (myDefaultProjectRootElement != null) {
        try {
          myDefaultProject.loadFromXml(myDefaultProjectRootElement, null);
        }
        catch (InvalidDataException e) {
          LOG.info(e);
          Messages.showErrorDialog(e.getMessage(), ProjectBundle.message("project.load.default.error"));
        }
        finally {
          myDefaultProjectRootElement = null;
        }
      }
      myDefaultProject.init();
    }
    return myDefaultProject;
  }

  public Project[] getOpenProjects() {
    return myOpenProjects.toArray(new Project[myOpenProjects.size()]);
  }

  public boolean isProjectOpened(Project project) {
    return myOpenProjects.contains(project);
  }

  public boolean openProject(final Project project) {
    if (myOpenProjects.contains(project)) return false;
    if (!ApplicationManager.getApplication().isUnitTestMode() && !checkVersion(project)) return false;


    myCountOfProjectsBeingOpen++;
    myOpenProjects.add(project);
    fireProjectOpened(project);

    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        ((StartupManagerImpl)StartupManager.getInstance(project)).runStartupActivities();
      }
    }, ProjectBundle.message("project.load.progress"), false, project);
    ((StartupManagerImpl)StartupManager.getInstance(project)).runPostStartupActivities();

    // Hack. We need to initialize FileDocumentManagerImpl's dummy project since it is lazy initialized and initialization can happen in
    // non-swing thread which could lead to some dummy components fail to initialize.
    FileDocumentManager fdManager = FileDocumentManager.getInstance();
    if (fdManager instanceof FileDocumentManagerImpl) {
      ((FileDocumentManagerImpl)fdManager).getDummyProject();
    }
    myCountOfProjectsBeingOpen--;
    return true;
  }

  public Project loadAndOpenProject(String filePath) throws IOException, JDOMException, InvalidDataException {
    Project project = loadProject(filePath);
    if (!openProject(project)) {
      return null;
    }
    return project;
  }

  private void registerExternalProjectFileListener(VirtualFileManagerEx virtualFileManager) {
    virtualFileManager.addVirtualFileManagerListener(new VirtualFileManagerListener() {
      public void beforeRefreshStart(boolean asynchonous) {
        myIsInRefresh = true;
      }

      public void afterRefreshFinish(boolean asynchonous) {
        myIsInRefresh = false;
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            if (myChangedProjectFiles.size() > 0) {
              Set<Project> projects = myChangedProjectFiles.keySet();
              List<Project> projectsToReload = new ArrayList<Project>();

              for (Project project : projects) {
                List<VirtualFile> causes = myChangedProjectFiles.get(project);
                Set<VirtualFile> liveCauses = new HashSet<VirtualFile>(causes);
                for (VirtualFile cause : causes) {
                  if (!cause.isValid()) liveCauses.remove(cause);
                }

                if (!liveCauses.isEmpty()) {
                  String message;
                  if (liveCauses.size() == 1) {
                    message = ProjectBundle.message("project.reload.external.change.single", causes.get(0).getPresentableUrl());
                  }
                  else {
                    StringBuffer filesBuilder = new StringBuffer();
                    boolean first = true;
                    for (VirtualFile cause : liveCauses) {
                      if (!first) filesBuilder.append("\n");
                      first = false;
                      filesBuilder.append(cause.getPresentableUrl());
                    }
                    message = ProjectBundle.message("project.reload.external.change.multiple", filesBuilder.toString());
                  }

                  if (Messages.showYesNoDialog(project,
                                               message,
                                               ProjectBundle.message("project.reload.external.change.title"),
                                               Messages.getQuestionIcon()) == 0) {
                    projectsToReload.add(project);
                  }
                }

                for (final Project projectToReload : projectsToReload) {
                  reloadProject(projectToReload);
                }
              }

              myChangedProjectFiles.clear();
            }
          }
        }, ModalityState.NON_MMODAL);
      }
    });

    virtualFileManager.addVirtualFileListener(new VirtualFileAdapter() {
      public void contentsChanged(VirtualFileEvent event) {
        if (event.isFromRefresh() && myIsInRefresh) { // external change
          saveChangedProjectFile(event.getFile());
        }
      }
    });
  }

  public boolean isFileSavedToBeReloaded(VirtualFile candidate) {
    return mySavedCopies.containsKey(candidate);
  }

  public void saveChangedProjectFile(final VirtualFile file) {
    final Project[] projects = getOpenProjects();
    for (Project project : projects) {
      if (file == project.getProjectFile() || file == project.getWorkspaceFile()) {
        copyToTemp(file);
        registerProjectToReload(project, file);
      }
      ModuleManager moduleManager = ModuleManager.getInstance(project);
      final Module[] modules = moduleManager.getModules();
      for (Module module : modules) {
        if (module.getModuleFile() == file) {
          copyToTemp(file);
          registerProjectToReload(project, file);
        }
      }
    }
  }


  private void registerProjectToReload(final Project project, final VirtualFile cause) {
    List<VirtualFile> changedProjectFiles = myChangedProjectFiles.get(project);

    if (changedProjectFiles == null) {
      changedProjectFiles = new ArrayList<VirtualFile>();
      myChangedProjectFiles.put(project, changedProjectFiles);
    }

    changedProjectFiles.add(cause);
  }

  private void copyToTemp(VirtualFile file) {
    try {
      final byte[] bytes = file.contentsToByteArray();
      mySavedCopies.put(file, bytes);
      mySavedTimestamps.put(file, file.getTimeStamp());
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private void restoreCopy(VirtualFile file) {
    try {
      if (file == null) return; // Externally deleted actually.
      if (!file.isWritable()) return; // IDEA was unable to save it as well. So no need to restore.

      final byte[] bytes = mySavedCopies.get(file);
      if (bytes != null) {
        try {
          file.setBinaryContent(bytes, -1, mySavedTimestamps.get(file));
        }
        catch (IOException e) {
          Messages.showWarningDialog(ProjectBundle.message("project.reload.write.failed", file.getPresentableUrl()),
                                     ProjectBundle.message("project.reload.write.failed.title"));
        }
      }
    }
    finally {
      mySavedCopies.remove(file);
      mySavedTimestamps.remove(file);
    }
  }

  public void reloadProject(final Project project) {
    ProjectReloadState.getInstance(project).onBeforeAutomaticProjectReload();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        LOG.info("Reloading project.");
        final String path = project.getProjectFilePath();
        final List<VirtualFile> original = getAllProjectFiles(project);

        if (project.isDisposed() || ProjectUtil.closeProject(project)) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              for (final VirtualFile aOriginal : original) {
                restoreCopy(aOriginal);
              }
            }
          });

          ProjectUtil.openProject(path, null, true);
        }
      }
    }, ModalityState.NON_MMODAL);
  }

  private static List<VirtualFile> getAllProjectFiles(Project project) {
    List<VirtualFile> files = new ArrayList<VirtualFile>();
    files.add(project.getProjectFile());
    files.add(project.getWorkspaceFile());

    ModuleManager moduleManager = ModuleManager.getInstance(project);
    final Module[] modules = moduleManager.getModules();
    for (Module module : modules) {
      files.add(module.getModuleFile());
    }
    return files;
  }

  private static boolean checkVersion(final Project project) {
    int version = ((ProjectImpl)project).getOriginalVersion();
    if (version >= 0 && version < CURRENT_FORMAT_VERSION) {
      final VirtualFile projectFile = project.getProjectFile();
      LOG.assertTrue(projectFile != null);
      String name = projectFile.getNameWithoutExtension();

      String message = ProjectBundle.message("project.convert.old.prompt", projectFile.getName(),
                                             ApplicationNamesInfo.getInstance().getProductName(),
                                             name + OLD_PROJECT_SUFFIX + projectFile.getExtension());
      if (Messages.showYesNoDialog(message, CommonBundle.getWarningTitle(), Messages.getWarningIcon()) != 0) return false;

      final ArrayList<String> conversionProblems = ((ProjectImpl)project).getConversionProblemsStorage();
      if (conversionProblems.size() > 0) {
        StringBuffer buffer = new StringBuffer();
        buffer.append(ProjectBundle.message("project.convert.problems.detected"));
        for (String s : conversionProblems) {
          buffer.append('\n');
          buffer.append(s);
        }
        buffer.append(ProjectBundle.message("project.convert.problems.help"));
        final int result = Messages.showDialog(project, buffer.toString(), ProjectBundle.message("project.convert.problems.title"),
                                               new String[]{ProjectBundle.message("project.convert.problems.help.button"),
                                                 CommonBundle.getCloseButtonText()}, 0,
                                                                                     Messages.getWarningIcon()
        );
        if (result == 0) {
          HelpManager.getInstance().invokeHelp("project.migrationProblems");
        }
      }

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          try {
            VirtualFile projectDir = projectFile.getParent();
            assert projectDir != null;

            final String oldProjectName = projectFile.getNameWithoutExtension() + OLD_PROJECT_SUFFIX + projectFile.getExtension();
            VirtualFile oldProject = projectDir.findChild(oldProjectName);
            if (oldProject == null) {
              oldProject = projectDir.createChildData(this, oldProjectName);
            }
            VfsUtil.saveText(oldProject, VfsUtil.loadText(projectFile));
            VirtualFile workspaceFile = project.getWorkspaceFile();
            if (workspaceFile != null) {
              final String oldWorkspaceName = workspaceFile.getNameWithoutExtension() + OLD_PROJECT_SUFFIX +
                                              project.getWorkspaceFile().getExtension();
              VirtualFile oldWorkspace = projectDir.findChild(oldWorkspaceName);
              if (oldWorkspace == null) {
                oldWorkspace = projectDir.createChildData(this, oldWorkspaceName);
              }
              VfsUtil.saveText(oldWorkspace, VfsUtil.loadText(workspaceFile));
            }
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }
      });
    }

    if (version > CURRENT_FORMAT_VERSION) {
      String message =
        ProjectBundle.message("project.load.new.version.warning", project.getName(), ApplicationNamesInfo.getInstance().getProductName());

      if (Messages.showYesNoDialog(message, CommonBundle.getWarningTitle(), Messages.getWarningIcon()) != 0) return false;
    }

    return true;
  }

  /*
  public boolean isOpeningProject() {
    return myCountOfProjectsBeingOpen > 0;
  }
  */

  public boolean closeProject(final Project project) {
    if (!isProjectOpened(project)) return true;
    if (!canClose(project)) return false;

    fireProjectClosing(project);

    ShutDownTracker.getInstance().registerStopperThread(Thread.currentThread());
    try {
      FileDocumentManager.getInstance().saveAllDocuments();
      project.save();

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          myOpenProjects.remove(project);
          fireProjectClosed(project);

          Disposer.dispose(project);
        }
      });

      ApplicationManagerEx.getApplicationEx().saveSettings();
    }
    finally {
      ShutDownTracker.getInstance().unregisterStopperThread(Thread.currentThread());
    }

    return true;
  }

  protected void fireProjectClosing(Project project) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: fireProjectClosing()");
    }
    synchronized (myListeners) {
      if (myListeners.size() > 0) {
        ProjectManagerListener[] listeners = myListeners.toArray(new ProjectManagerListener[myListeners.size()]);
        for (ProjectManagerListener listener : listeners) {
          listener.projectClosing(project);
        }
      }
    }
  }

  public void addProjectManagerListener(ProjectManagerListener listener) {
    synchronized (myListeners) {
      myListeners.add(listener);
    }
  }

  public void removeProjectManagerListener(ProjectManagerListener listener) {
    synchronized (myListeners) {
      boolean removed = myListeners.remove(listener);
      LOG.assertTrue(removed);
    }
  }

  public void addProjectManagerListener(Project project, ProjectManagerListener listener) {
    ArrayList<ProjectManagerListener> listeners = project.getUserData(LISTENERS_IN_PROJECT_KEY);
    if (listeners == null) {
      listeners = new ArrayList<ProjectManagerListener>();
      project.putUserData(LISTENERS_IN_PROJECT_KEY, listeners);
    }
    listeners.add(listener);
  }

  public void removeProjectManagerListener(Project project, ProjectManagerListener listener) {
    ArrayList<ProjectManagerListener> listeners = project.getUserData(LISTENERS_IN_PROJECT_KEY);
    if (listeners != null) {
      boolean removed = listeners.remove(listener);
      LOG.assertTrue(removed);
    }
    else {
      LOG.assertTrue(false);
    }
  }

  private void fireProjectOpened(Project project) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("projectOpened");
    }
    synchronized (myListeners) {
      if (myListeners.size() > 0) {
        ProjectManagerListener[] listeners = myListeners.toArray(new ProjectManagerListener[myListeners.size()]);
        for (ProjectManagerListener listener : listeners) {
          listener.projectOpened(project);
        }
      }
    }
  }

  private void fireProjectClosed(Project project) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("projectClosed");
    }
    synchronized (myListeners) {
      if (myListeners.size() > 0) {
        ProjectManagerListener[] listeners = myListeners.toArray(new ProjectManagerListener[myListeners.size()]);
        for (ProjectManagerListener listener : listeners) {
          listener.projectClosed(project);
        }
      }
    }
  }

  public boolean canClose(Project project) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: canClose()");
    }
    synchronized (myListeners) {
      if (myListeners.size() > 0) {
        ProjectManagerListener[] listeners = myListeners.toArray(new ProjectManagerListener[myListeners.size()]);
        for (ProjectManagerListener listener : listeners) {
          if (!listener.canCloseProject(project)) return false;
        }
      }
    }

    return true;
  }

  public void writeExternal(Element parentNode) throws WriteExternalException {
    if (myDefaultProject != null) {
      Element element = new Element(ELEMENT_DEFAULT_PROJECT);
      parentNode.addContent(element);
      myDefaultProject.saveToXml(element, myDefaultProject.getProjectFile());
    }
    else if (myDefaultProjectRootElement != null) {
      parentNode.addContent((Element)myDefaultProjectRootElement.clone());
    }

  }

  public void readExternal(Element parentNode) throws InvalidDataException {
    Element element = parentNode.getChild(ELEMENT_DEFAULT_PROJECT);
    if (element != null) {
      myDefaultProjectRootElement = element;
    }
  }

  public String getExternalFileName() {
    return "project.default";
  }

  public String getComponentName() {
    return "ProjectManager";
  }

  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile(this)};
  }

  public String getPresentableName() {
    return ProjectBundle.message("project.default.settings");
  }
}