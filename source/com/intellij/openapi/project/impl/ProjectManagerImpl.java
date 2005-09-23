package com.intellij.openapi.project.impl;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.project.ProjectReloadState;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.ex.VirtualFileManagerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.containers.HashMap;
import gnu.trove.TObjectLongHashMap;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.*;

public class ProjectManagerImpl extends ProjectManagerEx implements NamedJDOMExternalizable, ExportableApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.project.impl.ProjectManagerImpl");
  static final int CURRENT_FORMAT_VERSION = 4;

  private static final Key<ArrayList<ProjectManagerListener>> LISTENERS_IN_PROJECT_KEY = Key.create("LISTENERS_IN_PROJECT_KEY");

  private ProjectImpl myDefaultProject;
  private Element myDefaultProjectRootElement;

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
    ProjectManagerListener[] listeners = array.toArray(new ProjectManagerListener[array.size()]);
    return listeners;
  }

  /* @fabrique used by fabrique! */
  public ProjectManagerImpl(VirtualFileManagerEx virtualFileManagerEx,
                            PathMacrosImpl pathMacros,
                            VirtualFilePointerManager filePointerManager) {
    addProjectManagerListener(
      new ProjectManagerListener() {

        public void projectOpened(Project project) {
          ProjectManagerListener[] listeners = getListeners(project);
          for (int i = 0; i < listeners.length; i++) {
            ProjectManagerListener listener = listeners[i];
            listener.projectOpened(project);
          }
        }

        public void projectClosed(Project project) {
          ProjectManagerListener[] listeners = getListeners(project);
          for (int i = 0; i < listeners.length; i++) {
            ProjectManagerListener listener = listeners[i];
            listener.projectClosed(project);
          }
        }

        public boolean canCloseProject(Project project) {
          ProjectManagerListener[] listeners = getListeners(project);
          for (int i = 0; i < listeners.length; i++) {
            ProjectManagerListener listener = listeners[i];
            if (!listener.canCloseProject(project)) {
              return false;
            }
          }
          return true;
        }

        public void projectClosing(Project project) {
          ProjectManagerListener[] listeners = getListeners(project);
          for (int i = 0; i < listeners.length; i++) {
            ProjectManagerListener listener = listeners[i];
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
      myDefaultProject.dispose();
      myDefaultProject = null;
    }
  }

  public void initComponent() {
  }

  public Project newProject(String filePath, boolean useDefaultProjectSettings, boolean isDummy) {
    if (filePath != null) {
      try {
        String canonicalPath = new File(filePath).getCanonicalPath();
        if (canonicalPath != null) {
          filePath = canonicalPath;
        }
      }
      catch (IOException e) {
      }
    }
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
    try {
      String canonicalPath = new File(filePath).getCanonicalPath();
      if (canonicalPath != null) {
        filePath = canonicalPath;
      }
    }
    catch (IOException e) {
    }
    ProjectImpl project = createProject(filePath, false, false, false);

    // http://www.jetbrains.net/jira/browse/IDEA-1556. Enforce refresh. Project files may potentially reside in non-yet valid vfs paths.
    final String[] paths = project.getConfigurationFilePaths();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (int i = 0; i < paths.length; i++) {
          LocalFileSystem.getInstance().refreshAndFindFileByPath(paths[i].replace(File.separatorChar, '/'));
        }
      }
    });

    final boolean macrosOk = checkMacros(project, getDefinedMacros());
    if (!macrosOk) {
      throw new IOException("There are undefined path variables in project file");
    }

    project.loadSavedConfiguration();

    project.init();
    return project;
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
    final String text = "There are undefined path variables in project configuration files.\n" +
                  "In order for the project to load all path variables must be defined.";
    return showMacrosConfigurationDialog(project, text, usedMacros);
  }

  private static boolean showMacrosConfigurationDialog(Project project, final String text, final Set<String> usedMacros) {
    final UndefinedMacrosConfigurable configurable = new UndefinedMacrosConfigurable(text, usedMacros.toArray(new String[usedMacros.size()]));
    final SingleConfigurableEditor editor = new SingleConfigurableEditor(project, configurable) {
      protected void doOKAction() {
        if (!myConfigurable.isModified()) {
          Messages.showErrorDialog(getContentPane(), "All path variables should be defined", "Path Variables Not Defined");
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
          Messages.showErrorDialog(e.getMessage(), "Error Loading Default Project");
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

    ApplicationManager.getApplication().runProcessWithProgressSynchronously(
      new Runnable() {
        public void run() {
          ((StartupManagerImpl)StartupManager.getInstance(project)).runStartupActivities();
        }
      },
      "Loading Project",
      false,
      project
    );
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

                  for (Iterator<Project> iterator = projects.iterator(); iterator.hasNext();) {
                    Project project = iterator.next();
                    List<VirtualFile> causes = myChangedProjectFiles.get(project);
                    Set<VirtualFile> liveCauses = new HashSet<VirtualFile>(causes);
                    for (int i = 0; i < causes.size(); i++) {
                      VirtualFile cause = causes.get(i);
                      if (!cause.isValid()) liveCauses.remove(cause);
                    }

                    if (!liveCauses.isEmpty()) {
                      StringBuffer message = new StringBuffer();
                      message.append("Project file");
                      if (liveCauses.size() > 1) {
                        message.append("s:\n");
                      }
                      else {
                        message.append(" ");
                      }

                      boolean first = true;
                      for (Iterator<VirtualFile> it = liveCauses.iterator(); it.hasNext();) {
                        VirtualFile cause = it.next();
                        if (!first) message.append("\n");
                        first = false;
                        message.append(cause.getPresentableUrl());
                      }

                      message.append(liveCauses.size() > 1 ? "\nhave" : " has");
                      message.append(" been changed externally.\n\nReload project?");

                      if (Messages.showYesNoDialog(project,
                                                   message.toString(),
                                                   "Project Files Changed",
                                                   Messages.getQuestionIcon()) == 0) {
                        projectsToReload.add(project);
                      }
                    }

                    for (Iterator<Project> reloadIterator = projectsToReload.iterator(); reloadIterator.hasNext();) {
                      reloadProject(reloadIterator.next());
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
        if (event.getRequestor() == null && myIsInRefresh) { // external change
          saveChangedProjectFile(event.getFile());
        }
      }
    });
  }

  public void saveChangedProjectFile(final VirtualFile file) {
    final Project[] projects = getOpenProjects();
    for (int i = 0; i < projects.length; i++) {
      Project project = projects[i];
      if (file == project.getProjectFile() || file == project.getWorkspaceFile()) {
        copyToTemp(file);
        registerProjectToReload(project, file);
      }

      ModuleManager moduleManager = ModuleManager.getInstance(project);
      final Module[] modules = moduleManager.getModules();
      for (int j = 0; j < modules.length; j++) {
        if (modules[j].getModuleFile() == file) {
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
      mySavedTimestamps.put(file, file.getActualTimeStamp());
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
          final OutputStream stream = file.getOutputStream(this, -1, mySavedTimestamps.get(file));
          stream.write(bytes);
          stream.close();
        }
        catch (IOException e) {
          Messages.showWarningDialog("Error writing to file '" + file.getPresentableUrl() + "'. Project may reload incorrectly.", "Write error");
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

        if (project.isDisposed() || ProjectUtil.closeProject(project)){
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              for (Iterator<VirtualFile> iterator = original.iterator(); iterator.hasNext();) {
                restoreCopy(iterator.next());
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
    for (int j = 0; j < modules.length; j++) {
      Module module = modules[j];
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

      String message =
        "The project " + projectFile.getName() + " you are about to open has an older format.\n"
        + ApplicationNamesInfo.getInstance().getProductName() + " will automatically convert it to the new format.\n"
        + "You will not be able to open it by earlier versions.\n"
        + "The old project file will be saved to " + name + "_old.ipr.\n"
        + "Proceed with conversion?";
      if (Messages.showYesNoDialog(message, "Warning", Messages.getWarningIcon()) != 0) return false;

      final ArrayList<String> conversionProblems = ((ProjectImpl) project).getConversionProblemsStorage();
      if (conversionProblems.size() > 0) {
        StringBuffer buffer = new StringBuffer();
        buffer.append("During your project conversion, the following problem(s) were detected:");
        for (Iterator<String> iterator = conversionProblems.iterator(); iterator.hasNext();) {
          String s = iterator.next();
          buffer.append('\n');
          buffer.append(s);
        }
        buffer.append("\n\nPress 'Show Help' for more information.");
        final int result = Messages.showDialog(project, buffer.toString(), "Project Conversion Problems",
                                               new String[]{"Show Help", "Close"}, 0,
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

            final String oldProjectName = projectFile.getNameWithoutExtension() + "_old." + projectFile.getExtension();
            VirtualFile oldProject = projectDir.findChild(oldProjectName);
            if (oldProject == null) {
              oldProject = projectDir.createChildData(this, oldProjectName);
            }
            Writer writer = oldProject.getWriter(this);
            writer.write(projectFile.contentsToCharArray());
            writer.close();

            VirtualFile workspaceFile = project.getWorkspaceFile();
            if (workspaceFile != null) {
              final String oldWorkspaceName = workspaceFile.getNameWithoutExtension() + "_old." +
                                              project.getWorkspaceFile().getExtension();
              VirtualFile oldWorkspace = projectDir.findChild(oldWorkspaceName);
              if (oldWorkspace == null) {
                oldWorkspace = projectDir.createChildData(this, oldWorkspaceName);
              }
              writer = oldWorkspace.getWriter(this);
              writer.write(workspaceFile.contentsToCharArray());
              writer.close();
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
        "The project " + project.getName() + " you are about to open " +
        "has been created by a newer version of " + ApplicationNamesInfo.getInstance().getProductName() +
        ". If you open it, your project" +
        " is likely to be corrupted. Continue?";

      if (Messages.showYesNoDialog(message, "Warning", Messages.getWarningIcon()) != 0) return false;
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

      myOpenProjects.remove(project);
      fireProjectClosed(project);

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
        for (int i = 0; i < listeners.length; i++) {
          listeners[i].projectClosing(project);
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
        for (int i = 0; i < listeners.length; i++) {
          listeners[i].projectOpened(project);
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
        for (int i = 0; i < listeners.length; i++) {
          listeners[i].projectClosed(project);
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
        for (int i = 0; i < listeners.length; i++) {
          if (!listeners[i].canCloseProject(project)) return false;
        }
      }
    }

    return true;
  }

  public void writeExternal(Element parentNode) throws WriteExternalException {
    if (myDefaultProject != null) {
      Element element = new Element("defaultProject");
      parentNode.addContent(element);
      myDefaultProject.saveToXml(element, myDefaultProject.getProjectFile());
    }
    else if (myDefaultProjectRootElement != null){
      parentNode.addContent((Element)myDefaultProjectRootElement.clone());
    }

  }

  public void readExternal(Element parentNode) throws InvalidDataException {
    Element element = parentNode.getChild("defaultProject");
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
    return "Default project settings";
  }

}