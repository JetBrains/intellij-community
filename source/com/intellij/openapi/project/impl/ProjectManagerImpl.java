package com.intellij.openapi.project.impl;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.impl.convert.ProjectConversionHelper;
import com.intellij.ide.impl.convert.ProjectConversionUtil;
import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManagerListener;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.util.ProfilingUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.TObjectLongHashMap;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ProjectManagerImpl extends ProjectManagerEx implements NamedJDOMExternalizable, ExportableApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.project.impl.ProjectManagerImpl");
  public static final int CURRENT_FORMAT_VERSION = 4;

  private static final Key<ArrayList<ProjectManagerListener>> LISTENERS_IN_PROJECT_KEY = Key.create("LISTENERS_IN_PROJECT_KEY");
  @NonNls private static final String ELEMENT_DEFAULT_PROJECT = "defaultProject";

  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
  private ProjectImpl myDefaultProject; // Only used asynchronously in save and dispose, which itself are synchronized.

  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
  private Element myDefaultProjectRootElement; // Only used asynchronously in save and dispose, which itself are synchronized.

  private final ArrayList<Project> myOpenProjects = new ArrayList<Project>();
  private final ArrayList<ProjectManagerListener> myListeners = new ArrayList<ProjectManagerListener>();

  private Project myCurrentTestProject = null;

  private Map<VirtualFile, byte[]> mySavedCopies = new HashMap<VirtualFile, byte[]>();
  private TObjectLongHashMap<VirtualFile> mySavedTimestamps = new TObjectLongHashMap<VirtualFile>();
  private HashMap<Project, List<VirtualFile>> myChangedProjectFiles = new HashMap<Project, List<VirtualFile>>();
  //todo[mike] make private again
  public PathMacrosImpl myPathMacros;
  private volatile int myReloadBlockCount = 0;

  private static ProjectManagerListener[] getListeners(Project project) {
    ArrayList<ProjectManagerListener> array = project.getUserData(LISTENERS_IN_PROJECT_KEY);
    if (array == null) return ProjectManagerListener.EMPTY_ARRAY;
    return array.toArray(new ProjectManagerListener[array.size()]);
  }

  public ProjectManagerImpl(VirtualFileManagerEx virtualFileManagerEx) {
    addProjectManagerListener(
      new ProjectManagerListener() {

        public void projectOpened(final Project project) {
          MessageBus messageBus = project.getMessageBus();
          MessageBusConnection connection = messageBus.connect(project);
          connection.subscribe(StateStorage.STORAGE_TOPIC, new StateStorage.Listener() {
            public void storageFileChanged(final VirtualFileEvent event) {
              saveChangedProjectFile(event.getFile(), project);
            }
          });


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

    ProjectImpl project = createProject(filePath, false, isDummy, ApplicationManager.getApplication().isUnitTestMode(), null);

    if (useDefaultProjectSettings) {
      try {
        project.getStateStore().loadProjectFromTemplate((ProjectImpl)getDefaultProject());
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
    project.init();
    return project;
  }

  private ProjectImpl createProject(String filePath, boolean isDefault, boolean isDummy, boolean isOptimiseTestLoadSpeed,
                                    @Nullable ProjectConversionHelper conversionHelper) {
    final ProjectImpl project;
    if (isDummy) {
      throw new UnsupportedOperationException("Dummy project is deprecated and shall not be used anymore.");
    }
    else {
      project = new ProjectImpl(this, filePath, isDefault, isOptimiseTestLoadSpeed);
      if (conversionHelper != null) {
        project.getPicoContainer().registerComponentInstance(ProjectConversionHelper.class, conversionHelper);
      }
    }

    try {
      project.getStateStore().load();
    }
    catch (IOException e) {
      LOG.info(e);
      Messages.showErrorDialog(e.getMessage(), ProjectBundle.message("project.load.default.error"));
    }
    catch (StateStorage.StateStorageException e) {
      LOG.info(e);
      Messages.showErrorDialog(e.getMessage(), ProjectBundle.message("project.load.default.error"));
    }

    project.loadProjectComponents();
    return project;
  }

  @Nullable
  public Project loadProject(String filePath) throws IOException, JDOMException, InvalidDataException {
    try {
      return loadProject(filePath, false);
    }
    catch (StateStorage.StateStorageException e) {
      throw new IOException(e.getMessage());
    }
  }

  @Nullable
  private Project loadProject(String filePath, boolean convert) throws IOException, JDOMException, InvalidDataException, StateStorage.StateStorageException {
    filePath = canonicalize(filePath);

    ProjectConversionHelper conversionHelper = null;
    if (convert) {
      final ProjectConversionUtil.ProjectConversionResult result = ProjectConversionUtil.convertProject(filePath);
      if (result.isOpeningCancelled()) {
        return null;
      }
      conversionHelper = result.getConversionHelper();
    }

    ProjectImpl project = createProject(filePath, false, false, false, conversionHelper);
    project.getStateStore().loadProject();
    return project;
  }

  @Nullable
  private static String canonicalize(final String filePath) {
    if (filePath == null) return null;
    try {
      return FileUtil.resolveShortWindowsName(filePath);
    }
    catch (IOException e) {
      // OK. File does not yet exist so it's canonical path will be equal to its original path.
    }

    return filePath;
  }

  public static boolean showMacrosConfigurationDialog(Project project, final Set<String> undefinedMacros) {
    final String text = ProjectBundle.message("project.load.undefined.path.variables.message");
    final Application application = ApplicationManager.getApplication();
    if (application.isHeadlessEnvironment() || application.isUnitTestMode()) {
      throw new RuntimeException(text + ": " + StringUtil.join(undefinedMacros, ", "));
    }
    final UndefinedMacrosConfigurable configurable =
      new UndefinedMacrosConfigurable(text, undefinedMacros.toArray(new String[undefinedMacros.size()]));
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
      myDefaultProject = createProject(null, true, false, ApplicationManager.getApplication().isUnitTestMode(), null);

      myDefaultProjectRootElement = null;

      try {
        myDefaultProject.getStateStore().load();
        myDefaultProject.init();
      }
      catch (IOException e) {
        LOG.error(e);
      }
      catch (StateStorage.StateStorageException e) {
        LOG.error(e);
      }
    }
    return myDefaultProject;
  }


  public Element getDefaultProjectRootElement() {
    return myDefaultProjectRootElement;
  }

  @NotNull
  public Project[] getOpenProjects() {
    return myOpenProjects.toArray(new Project[myOpenProjects.size()]);
  }

  public boolean isProjectOpened(Project project) {
    return myOpenProjects.contains(project);
  }

  public boolean openProject(final Project project) {
    if (myOpenProjects.contains(project)) return false;
    if (!ApplicationManager.getApplication().isUnitTestMode() && !((ProjectImpl)project).getStateStore().checkVersion()) return false;


    myOpenProjects.add(project);
    fireProjectOpened(project);

    final StartupManagerImpl startupManager = (StartupManagerImpl)StartupManager.getInstance(project);

    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        startupManager.runStartupActivities();
      }
    }, ProjectBundle.message("project.load.progress"), false, project);
    startupManager.runPostStartupActivities();

    return true;
  }
  public boolean openProjectNoFire(final Project project) {
    if (myOpenProjects.contains(project)) return false;
    if (!ApplicationManager.getApplication().isUnitTestMode() && !((ProjectImpl)project).getStateStore().checkVersion()) return false;


    myOpenProjects.add(project);
    //fireProjectOpened(project);

    final StartupManagerImpl startupManager = (StartupManagerImpl)StartupManager.getInstance(project);

    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        startupManager.runStartupActivities();
      }
    }, ProjectBundle.message("project.load.progress"), false, project);
    startupManager.runPostStartupActivities();

    return true;
  }

  public Project loadAndOpenProject(String filePath) throws IOException, JDOMException, InvalidDataException {
    return loadAndOpenProject(filePath, true);
  }

  @Nullable
  public Project loadAndOpenProject(String filePath, boolean convert) throws IOException, JDOMException, InvalidDataException {
    try {
      Project project = loadProject(filePath, convert);
      if (project == null || !openProject(project)) {
        return null;
      }
      return project;
    }
    catch (StateStorage.StateStorageException e) {
      throw new IOException(e.getMessage());
    }
  }

  private void registerExternalProjectFileListener(VirtualFileManagerEx virtualFileManager) {
    virtualFileManager.addVirtualFileManagerListener(new VirtualFileManagerListener() {
      public void beforeRefreshStart(boolean asynchonous) {
      }

      public void afterRefreshFinish(boolean asynchonous) {
        askToReloadProjectIfConfigFilesChangedExternally();
      }
    });
  }

  private void askToReloadProjectIfConfigFilesChangedExternally() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (!myChangedProjectFiles.isEmpty() && myReloadBlockCount == 0) {
          Set<Project> projects = myChangedProjectFiles.keySet();
          List<Project> projectsToReload = new ArrayList<Project>();

          for (Project project : projects) {
            if (shouldReloadProject(project)) {
              projectsToReload.add(project);
            }
          }

          for (final Project projectToReload : projectsToReload) {
            reloadProject(projectToReload);
          }

          myChangedProjectFiles.clear();
        }
      }
    }, ModalityState.NON_MODAL);
  }

  private boolean shouldReloadProject(final Project project) {
    if (project.isDisposed()) return false;
    List<VirtualFile> causes = myChangedProjectFiles.get(project);
    Set<VirtualFile> liveCauses = new HashSet<VirtualFile>(causes);
    for (VirtualFile cause : causes) {
      if (!cause.isValid()) liveCauses.remove(cause);
    }

    if (liveCauses.isEmpty()) return false;


    if (((ProjectImpl)project).getStateStore().reload()) return false;

    String message;
    if (liveCauses.size() == 1) {
      message = ProjectBundle.message("project.reload.external.change.single", causes.get(0).getPresentableUrl());
    }
    else {
      StringBuilder filesBuilder = new StringBuilder();
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
                                 Messages.getQuestionIcon()) != 0) {
      return false;
    }

    return true;
  }

  public boolean isFileSavedToBeReloaded(VirtualFile candidate) {
    return mySavedCopies.containsKey(candidate);
  }

  public void blockReloadingProjectOnExternalChanges() {
    myReloadBlockCount++;
  }

  public void unblockReloadingProjectOnExternalChanges() {
    myReloadBlockCount--;
    askToReloadProjectIfConfigFilesChangedExternally();
  }

  public void setCurrentTestProject(@Nullable final Project project) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    myCurrentTestProject = project;
  }

  @Nullable
  public Project getCurrentTestProject() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    return myCurrentTestProject;
  }

  public void saveChangedProjectFile(final VirtualFile file, final Project project) {
    copyToTemp(file);
    registerProjectToReload(project, file);
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

  public void reloadProject(final Project p) {
    reloadProject(p, false);
  }

  public void reloadProject(final Project p, final boolean takeMemorySnapshot) {
    final Project[] project = new Project[]{p};

    ProjectReloadState.getInstance(project[0]).onBeforeAutomaticProjectReload();
    final Application application = ApplicationManager.getApplication();

    application.invokeLater(new Runnable() {
      public void run() {
        LOG.info("Reloading project.");
        ProjectImpl projectImpl = (ProjectImpl)project[0];
        IProjectStore projectStore = projectImpl.getStateStore();
        final String path = projectStore.getProjectFilePath();
        final List<VirtualFile> original = projectStore.getAllStorageFiles(true);

        if (project[0].isDisposed() || ProjectUtil.closeProject(project[0])) {
          application.runWriteAction(new Runnable() {
            public void run() {
              for (final VirtualFile aOriginal : original) {
                restoreCopy(aOriginal);
              }
            }
          });

          project[0] = null; // Let it go.

          if (takeMemorySnapshot) {
            String outputFileName = ProfilingUtil.createDumpFileName(ApplicationInfo.getInstance().getBuildNumber());
            ProfilingUtil.forceCaptureMemorySnapshot(outputFileName);
          }

          ProjectUtil.openProject(path, null, true);
        }
      }
    }, ModalityState.NON_MODAL);
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

    final ShutDownTracker shutDownTracker = ShutDownTracker.getInstance();
    shutDownTracker.registerStopperThread(Thread.currentThread());
    try {
      FileDocumentManager.getInstance().saveAllDocuments();
      project.save();

      myOpenProjects.remove(project);
      fireProjectClosed(project);

      ApplicationEx application = ApplicationManagerEx.getApplicationEx();
      if (!application.isUnitTestMode()) application.saveSettings();
    }
    finally {
      shutDownTracker.unregisterStopperThread(Thread.currentThread());
    }

    return true;
  }

  private void fireProjectClosing(Project project) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: fireProjectClosing()");
    }
    synchronized (myListeners) {
      if (!myListeners.isEmpty()) {
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
      if (!myListeners.isEmpty()) {
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
      if (!myListeners.isEmpty()) {
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
      if (!myListeners.isEmpty()) {
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
      myDefaultProject.save();
    }

    if (myDefaultProjectRootElement == null) { //read external isn't called if config folder is absent
      myDefaultProjectRootElement = new Element(ELEMENT_DEFAULT_PROJECT);
    }

    myDefaultProjectRootElement.detach();
    parentNode.addContent(myDefaultProjectRootElement);
  }


  public void setDefaultProjectRootElement(final Element defaultProjectRootElement) {
    myDefaultProjectRootElement = defaultProjectRootElement;
  }

  public void readExternal(Element parentNode) throws InvalidDataException {
    myDefaultProjectRootElement = parentNode.getChild(ELEMENT_DEFAULT_PROJECT);

    if (myDefaultProjectRootElement == null) {
      myDefaultProjectRootElement = new Element(ELEMENT_DEFAULT_PROJECT);
    }

    myDefaultProjectRootElement.detach();
  }

  public String getExternalFileName() {
    return "project.default";
  }

  @NotNull
  public String getComponentName() {
    return "ProjectManager";
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile(this)};
  }

  @NotNull
  public String getPresentableName() {
    return ProjectBundle.message("project.default.settings");
  }
}
