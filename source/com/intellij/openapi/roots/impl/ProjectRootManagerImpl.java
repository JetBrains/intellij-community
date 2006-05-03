package com.intellij.openapi.roots.impl;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileSystemSynchronizer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.ModuleImpl;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.ProjectRootType;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.EventDispatcher;
import com.intellij.util.PendingEventDispatcher;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author max
 */
public class ProjectRootManagerImpl extends ProjectRootManagerEx implements ProjectComponent, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.projectRoots.impl.ProjectRootManagerImpl");

  @NonNls private static final String ASSERT_KEYWORD_ATTR = "assert-keyword";
  @NonNls private static final String JDK_15_ATTR = "jdk-15";
  @NonNls private static final String PROJECT_JDK_NAME_ATTR = "project-jdk-name";
  @NonNls private static final String PROJECT_JDK_TYPE_ATTR = "project-jdk-type";
  private final ProjectEx myProject;
  private ProjectFileIndex myProjectFileIndex;

  private final PendingEventDispatcher<ModuleRootListener> myModuleRootEventDispatcher = PendingEventDispatcher.create(ModuleRootListener.class);
  private final PendingEventDispatcher<ProjectJdkListener> myProjectJdkEventDispatcher = PendingEventDispatcher.create(ProjectJdkListener.class);

  private final MyVirtualFilePointerListener myVirtualFilePointerListener = new MyVirtualFilePointerListener();

  private MyVirtualFileManagerListener myVirtualFileManagerListener;

  private String myProjectJdkName;
  private String myProjectJdkType;

  private ArrayList<CacheUpdater> myChangeUpdaters = new ArrayList<CacheUpdater>();

  private boolean myProjectOpened = false;
  private LanguageLevel myLanguageLevel = LanguageLevel.JDK_1_3;
  private LanguageLevel myOriginalLanguageLevel = myLanguageLevel;
  private FileTypeListener myFileTypeListener;
  private long myModificationCount = 0;
  private Set<LocalFileSystem.WatchRequest> myRootsToWatch = new HashSet<LocalFileSystem.WatchRequest>();
  private Runnable myReloadProjectRequest = null;
  @NonNls private static final String ATTRIBUTE_VERSION = "version";

  static ProjectRootManagerImpl getInstanceImpl(Project project) {
    return (ProjectRootManagerImpl)getInstance(project);
  }

  public ProjectRootManagerImpl(Project project, FileTypeManager fileTypeManager, DirectoryIndex directoryIndex) {
    myProject = (ProjectEx)project;
    myFileTypeListener = new FileTypeListener() {
      public void beforeFileTypesChanged(FileTypeEvent event) {
        beforeRootsChange(true);
      }

      public void fileTypesChanged(FileTypeEvent event) {
        rootsChanged(true);
      }
    };

    fileTypeManager.addFileTypeListener(myFileTypeListener);
    myProjectFileIndex = new ProjectFileIndexImpl(myProject, directoryIndex, fileTypeManager);
  }

  public void registerChangeUpdater(CacheUpdater updater) {
    myChangeUpdaters.add(updater);
  }

  public void unregisterChangeUpdater(CacheUpdater updater) {
    boolean success = myChangeUpdaters.remove(updater);
    LOG.assertTrue(success);
  }


  public void multiCommit(ModifiableRootModel[] rootModels) {
    ModuleRootManagerImpl.multiCommit(rootModels, ModuleManager.getInstance(myProject).getModifiableModel());
  }

  public void multiCommit(ModifiableModuleModel moduleModel, ModifiableRootModel[] rootModels) {
    ModuleRootManagerImpl.multiCommit(rootModels, moduleModel);
  }

  public void checkCircularDependency(ModifiableRootModel[] rootModels, ModifiableModuleModel moduleModel)
    throws ModuleCircularDependencyException {
    ModuleRootManagerImpl.checkCircularDependencies(rootModels, moduleModel);
  }

  public VirtualFilePointerListener getVirtualFilePointerListener() {
    return myVirtualFilePointerListener;
  }

  @NotNull
  public ProjectFileIndex getFileIndex() {
    return myProjectFileIndex;
  }

  public void addModuleRootListener(final ModuleRootListener listener) {
    myModuleRootEventDispatcher.addListener(listener);
  }

  public void removeModuleRootListener(ModuleRootListener listener) {
    myModuleRootEventDispatcher.removeListener(listener);
  }

  public void dispatchPendingEvent(ModuleRootListener listener) {
    myModuleRootEventDispatcher.dispatchPendingEvent(listener);
  }

  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }

  public void setLanguageLevel(LanguageLevel languageLevel) {
    myLanguageLevel = languageLevel;
    if (myProject.isOpen()) {
      myReloadProjectRequest = new Runnable() {
        public void run() {
          if (myReloadProjectRequest != this) {
            // obsolete, another request has already replaced this one
            return;
          }
          if (myOriginalLanguageLevel.equals(getLanguageLevel())) {
            // the question does not make sence now
            return;
          }
          final String _message = ProjectBundle.message("project.language.level.reload.prompt", myProject.getName());
          if (Messages.showYesNoDialog(myProject, _message, ProjectBundle.message("project.language.level.reload.title"), Messages.getQuestionIcon()) == 0) {
            ProjectManagerEx.getInstanceEx().reloadProject(myProject);
          }
          myReloadProjectRequest = null;
        }
      };
      ApplicationManager.getApplication().invokeLater(myReloadProjectRequest, ModalityState.NON_MMODAL);
    }
    else {
      // if the project is not open, reset the original level to the same value as mylanguageLevel has
      myOriginalLanguageLevel = languageLevel;
    }
  }

  private final static HashMap<ProjectRootType, OrderRootType> ourProjectRootTypeToOrderRootType = new HashMap<ProjectRootType, OrderRootType>();

  static {
    ourProjectRootTypeToOrderRootType.put(ProjectRootType.CLASS, OrderRootType.CLASSES);
    ourProjectRootTypeToOrderRootType.put(ProjectRootType.SOURCE, OrderRootType.SOURCES);
    ourProjectRootTypeToOrderRootType.put(ProjectRootType.JAVADOC, OrderRootType.JAVADOC);
  }

  public VirtualFile[] getRootFiles(ProjectRootType type) {
    if (ourProjectRootTypeToOrderRootType.get(type) != null) {
      return getFilesFromAllModules(ourProjectRootTypeToOrderRootType.get(type));
    }
    else if (type == ProjectRootType.EXCLUDE) {
      return getExcludeRootsFromAllModules();
    }
    else if (type == ProjectRootType.PROJECT) {
      return getContentRootsFromAllModules();
    }
    LOG.assertTrue(false);
    return null;
  }

  @NotNull
  public VirtualFile[] getContentRoots() {
    ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    final Module[] modules = getModuleManager().getModules();
    for (Module module : modules) {
      final VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
      result.addAll(Arrays.asList(contentRoots));
    }
    return result.toArray(new VirtualFile[result.size()]);
  }

  public VirtualFile[] getContentSourceRoots() {
    ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    final Module[] modules = getModuleManager().getModules();
    for (Module module : modules) {
      final VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
      result.addAll(Arrays.asList(sourceRoots));
    }
    return result.toArray(new VirtualFile[result.size()]);
  }

  private VirtualFile[] getFilesFromAllModules(OrderRootType type) {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    final Module[] modules = getModuleManager().getSortedModules();
    for (Module module : modules) {
      final VirtualFile[] files = ModuleRootManager.getInstance(module).getFiles(type);
      result.addAll(Arrays.asList(files));
    }
    return result.toArray(new VirtualFile[result.size()]);
  }

  private VirtualFile[] getExcludeRootsFromAllModules() {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    final Module[] modules = getModuleManager().getSortedModules();
    for (Module module : modules) {
      final VirtualFile[] files = ModuleRootManager.getInstance(module).getExcludeRoots();
      result.addAll(Arrays.asList(files));
    }
    return result.toArray(new VirtualFile[result.size()]);
  }

  private VirtualFile[] getContentRootsFromAllModules() {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    final Module[] modules = getModuleManager().getSortedModules();
    for (Module module : modules) {
      final VirtualFile[] files = ModuleRootManager.getInstance(module).getContentRoots();
      result.addAll(Arrays.asList(files));
    }
    return result.toArray(new VirtualFile[result.size()]);
  }

  public VirtualFile[] getFullClassPath() {
    return getFilesFromAllModules(OrderRootType.CLASSES_AND_OUTPUT);
  }

  public ProjectJdk getJdk() {
    final Module[] modules = ModuleManager.getInstance(myProject).getModules();
    if (modules.length > 0) {
      return ModuleRootManager.getInstance(modules[0]).getJdk();
    }
    else {
      return null;
    }
  }

  public ProjectJdk getProjectJdk() {
    if (myProjectJdkName != null) {
      return ProjectJdkTable.getInstance().findJdk(myProjectJdkName, myProjectJdkType);
    }
    else {
      return null;
    }
  }

  public String getProjectJdkName() {
    return myProjectJdkName;
  }

  public void setProjectJdk(ProjectJdk projectJdk) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    if (projectJdk != null) {
      myProjectJdkName = projectJdk.getName();
      myProjectJdkType = projectJdk.getSdkType().getName();
    }
    else {
      myProjectJdkName = null;
      myProjectJdkType = null;
    }
    doRootsChangedOnDemand(new Runnable() {
      public void run() {
        myProjectJdkEventDispatcher.getMulticaster().projectJdkChanged();
      }
    });
  }

  public void setProjectJdkName(String name) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    myProjectJdkName = name;

    doRootsChangedOnDemand(new Runnable() {
      public void run() {
        myProjectJdkEventDispatcher.getMulticaster().projectJdkChanged();
      }
    });
  }

  public void addProjectJdkListener(ProjectJdkListener listener) {
    myProjectJdkEventDispatcher.addListener(listener);
  }

  public void removeProjectJdkListener(ProjectJdkListener listener) {
    myProjectJdkEventDispatcher.removeListener(listener);
  }


  public void projectOpened() {
    addRootsToWatch();
    myVirtualFileManagerListener = new MyVirtualFileManagerListener();
    VirtualFileManager.getInstance().addVirtualFileManagerListener(myVirtualFileManagerListener);
    myProjectOpened = true;
  }

  public void projectClosed() {
    LocalFileSystem.getInstance().removeWatchedRoots(myRootsToWatch);
    VirtualFileManager.getInstance().removeVirtualFileManagerListener(myVirtualFileManagerListener);
    myProjectOpened = false;
  }

  public String getComponentName() {
    return "ProjectRootManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    if (myJdkTableMultilistener != null) {
      myJdkTableMultilistener.uninstallListner(false);
      myJdkTableMultilistener = null;
    }
    FileTypeManager.getInstance().removeFileTypeListener(myFileTypeListener);
  }

  public void readExternal(Element element) throws InvalidDataException {
    final boolean assertKeyword = Boolean.valueOf(element.getAttributeValue(ASSERT_KEYWORD_ATTR)).booleanValue();
    final boolean jdk15 = Boolean.valueOf(element.getAttributeValue(JDK_15_ATTR)).booleanValue();
    if (jdk15) {
      myLanguageLevel = LanguageLevel.JDK_1_5;
    }
    else if (assertKeyword) {
      myLanguageLevel = LanguageLevel.JDK_1_4;
    }
    else {
      myLanguageLevel = LanguageLevel.JDK_1_3;
    }
    myOriginalLanguageLevel = myLanguageLevel;
    myProjectJdkName = element.getAttributeValue(PROJECT_JDK_NAME_ATTR);
    myProjectJdkType = element.getAttributeValue(PROJECT_JDK_TYPE_ATTR);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    element.setAttribute(ATTRIBUTE_VERSION, "2");
    final boolean is14 = LanguageLevel.JDK_1_4.equals(myLanguageLevel);
    final boolean is15 = LanguageLevel.JDK_1_5.equals(myLanguageLevel);
    element.setAttribute(ASSERT_KEYWORD_ATTR, Boolean.toString(is14 || is15));
    element.setAttribute(JDK_15_ATTR, Boolean.toString(is15));
    if (myProjectJdkName != null) {
      element.setAttribute(PROJECT_JDK_NAME_ATTR, myProjectJdkName);
    }
    if (myProjectJdkType != null){
      element.setAttribute(PROJECT_JDK_TYPE_ATTR, myProjectJdkType);
    }
  }


  private boolean myIsRootsChangedOnDemandStartedButNotDemanded = false;

  private int myRootsChangeCounter = 0;

  private void doRootsChangedOnDemand(Runnable r) {
    LOG.assertTrue(!myIsRootsChangedOnDemandStartedButNotDemanded, "Nested on-demand rootsChanged not supported");
    LOG.assertTrue(myRootsChangeCounter == 0, "On-demand rootsChanged not allowed inside rootsChanged");
    myIsRootsChangedOnDemandStartedButNotDemanded = true;
    try {
      r.run();
    }
    finally {
      if (myIsRootsChangedOnDemandStartedButNotDemanded) {
        myIsRootsChangedOnDemandStartedButNotDemanded = false;
      }
      else {
        if (myRootsChangeCounter != 1) {
          LOG.assertTrue(false, "myRootsChangedCounter = " + myRootsChangeCounter);
        }
        myIsRootsChangedOnDemandStartedButNotDemanded = false;
        rootsChanged(false);
      }
    }
  }

  public void beforeRootsChange(boolean filetypes) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    if (myRootsChangeCounter == 0) {
      if (myIsRootsChangedOnDemandStartedButNotDemanded) {
        myIsRootsChangedOnDemandStartedButNotDemanded = false;
        myRootsChangeCounter++; // blocks all firing until finishRootsChangedOnDemand
      }
      myModuleRootEventDispatcher.getMulticaster().beforeRootsChange(new ModuleRootEventImpl(myProject, filetypes));
    }

    myRootsChangeCounter++;
  }

  private void rootsChanged(boolean filetypes, boolean synchronize) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    myRootsChangeCounter--;
    if (myRootsChangeCounter > 0) return;

    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      ((ModuleRootManagerImpl)ModuleRootManager.getInstance(module)).dropCaches();
      ((ModuleImpl)module).clearScopesCache();
    }

    myModuleRootEventDispatcher.getMulticaster().rootsChanged(new ModuleRootEventImpl(myProject, filetypes));

    for (Module module : modules) {
      ((ModuleImpl)module).clearScopesCache();
    }

    if (synchronize) doSynchronize();

    addRootsToWatch();

    myModificationCount++;
  }

  private void doSynchronize() {
    final FileSystemSynchronizer synchronizer = new FileSystemSynchronizer();
    for (CacheUpdater updater : myChangeUpdaters) {
      synchronizer.registerCacheUpdater(updater);
    }

    if (!ApplicationManager.getApplication().isUnitTestMode() && myProjectOpened) {
      Runnable process = new Runnable() {
        public void run() {
          synchronizer.execute();
        }
      };
      ProgressManager.getInstance().runProcessWithProgressSynchronously(process, ProjectBundle.message("project.root.change.loading.progress"), false, myProject);
    }
    else {
      synchronizer.execute();
    }
  }

  public void rootsChanged(boolean filetypes) {
    rootsChanged(filetypes, true);
  }

  private void addRootsToWatch() {
    LocalFileSystem.getInstance().removeWatchedRoots(myRootsToWatch);
    myRootsToWatch.clear();

    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    Set<String> contentRoots = new HashSet<String>();
    for (Module module : modules) {
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      final String[] contentRootUrls = moduleRootManager.getContentRootUrls();
      for (String url : contentRootUrls) {
        contentRoots.add(VfsUtil.urlToPath(url));
      }

      final String compilerOutputPath = VfsUtil.urlToPath(moduleRootManager.getCompilerOutputPathUrl());
      if (compilerOutputPath.length() > 0) {
        contentRoots.add(compilerOutputPath);
      }
      final String compilerOutputPathForTests = VfsUtil.urlToPath(moduleRootManager.getCompilerOutputPathForTestsUrl());
      if (compilerOutputPathForTests.length() > 0) {
        contentRoots.add(compilerOutputPathForTests);
      }

      contentRoots.add(module.getModuleFilePath());
    }

    final String projectFile = myProject.getProjectFilePath();
    if (projectFile != null) {
      contentRoots.add(projectFile);
      // No need to add workspace file separately since they're definetely on same directory with ipr.
    }

    myRootsToWatch.addAll(LocalFileSystem.getInstance().addRootsToWatch(contentRoots, true));


    Set<String> libraryRoots = new HashSet<String>();
    for (Module module : modules) {
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      final OrderEntry[] orderEntries = moduleRootManager.getOrderEntries();
      for (OrderEntry entry : orderEntries) {
        if (entry instanceof LibraryOrderEntry) {
          final Library library = ((LibraryOrderEntry)entry).getLibrary();
          libraryRoots.addAll(getRootsToTrack(library, OrderRootType.CLASSES));
          libraryRoots.addAll(getRootsToTrack(library, OrderRootType.SOURCES));
          libraryRoots.addAll(getRootsToTrack(library, OrderRootType.JAVADOC));
        }
      }
    }

    myRootsToWatch.addAll(LocalFileSystem.getInstance().addRootsToWatch(libraryRoots, false));

    Set<String> explodedDirs = new HashSet<String>();
    for (Module module : modules) {
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      final String explodedDirectory = moduleRootManager.getExplodedDirectoryUrl();
      if (explodedDirectory != null) {
        explodedDirs.add(VfsUtil.urlToPath(explodedDirectory));
      }
    }
    myRootsToWatch.addAll(LocalFileSystem.getInstance().addRootsToWatch(explodedDirs, true));
  }

  private Collection<String> getRootsToTrack(final Library library, final OrderRootType rootType) {
    List<String> result = new ArrayList<String>();
    if (library != null) {
      final String[] urls = library.getUrls(rootType);
      for (String url : urls) {
        if (url != null) {
          String path = VfsUtil.urlToPath(url);
          result.add(path);
        }
      }
    }
    return result;
  }

  private ModuleManager getModuleManager() {
    return ModuleManager.getInstance(myProject);
  }

  void addRootSetChangedListener(RootProvider.RootSetChangedListener rootSetChangedListener,
                                 final RootProvider provider) {
    RootSetChangedMulticaster multicaster = myRegisteredRootProviderListeners.get(provider);
    if (multicaster == null) {
      multicaster = new RootSetChangedMulticaster(provider);
    }
    multicaster.addListener(rootSetChangedListener);
  }

  void removeRootSetChangedListener(RootProvider.RootSetChangedListener rootSetChangedListener,
                                    final RootProvider provider) {
    RootSetChangedMulticaster multicaster = myRegisteredRootProviderListeners.get(provider);
    if (multicaster != null) {
      multicaster.removeListener(rootSetChangedListener);
    }
  }


  private class MyVirtualFilePointerListener implements VirtualFilePointerListener {
    private void assertPointersCorrect(VirtualFilePointer[] pointers) {
      for (VirtualFilePointer pointer : pointers) {
        final RootModelImpl rootModel = pointer.getUserData(RootModelImpl.ORIGINATING_ROOT_MODEL);
        LOG.assertTrue(rootModel != null);
        LOG.assertTrue(!rootModel.isDisposed());
        LOG.assertTrue(!rootModel.isWritable());
      }
    }

    public void beforeValidityChanged(VirtualFilePointer[] pointers) {
      assertPointersCorrect(pointers);
      beforeRootsChange(false);
    }

    public void validityChanged(VirtualFilePointer[] pointers) {
      assertPointersCorrect(pointers);
      rootsChanged(false, !myInsideRefresh);
      if (myInsideRefresh) myChangesDetected = true;
    }
  }

  private boolean myInsideRefresh = false;
  private boolean myChangesDetected = false;

  private class MyVirtualFileManagerListener implements VirtualFileManagerListener {
    public void beforeRefreshStart(boolean asynchonous) {
      myInsideRefresh = true;
    }

    public void afterRefreshFinish(boolean asynchonous) {
      myInsideRefresh = false;
      if (myChangesDetected) {
        doSynchronize();
        myChangesDetected = false;
      }
    }
  }


  void addListenerForTable(LibraryTable.Listener libraryListener,
                           final LibraryTable libraryTable) {
    LibraryTableMultilistener multilistener = myLibraryTableMultilisteners.get(libraryTable);
    if (multilistener == null) {
      multilistener = new LibraryTableMultilistener(libraryTable);
    }
    multilistener.addListener(libraryListener);
  }

  void removeListenerForTable(LibraryTable.Listener libraryListener,
                              final LibraryTable libraryTable) {
    LibraryTableMultilistener multilistener = myLibraryTableMultilisteners.get(libraryTable);
    if (multilistener == null) {
      multilistener = new LibraryTableMultilistener(libraryTable);
    }
    multilistener.removeListener(libraryListener);
  }

  Map<LibraryTable, LibraryTableMultilistener> myLibraryTableMultilisteners = new HashMap<LibraryTable, LibraryTableMultilistener>();

  private class LibraryTableMultilistener implements LibraryTable.Listener {
    EventDispatcher<LibraryTable.Listener> myDispatcher = EventDispatcher.create(LibraryTable.Listener.class);
    Set<LibraryTable.Listener> myListeners = new HashSet<LibraryTable.Listener>();
    private final LibraryTable myLibraryTable;

    private LibraryTableMultilistener(LibraryTable libraryTable) {
      myLibraryTable = libraryTable;
      myLibraryTable.addListener(this);
      myLibraryTableMultilisteners.put(myLibraryTable, this);
    }

    private void addListener(LibraryTable.Listener listener) {
      myListeners.add(listener);
      myDispatcher.addListener(listener);
    }

    private void removeListener(LibraryTable.Listener listener) {
      myDispatcher.removeListener(listener);
      myListeners.remove(listener);
      if (!myDispatcher.hasListeners()) {
        myLibraryTable.removeListener(this);
        myLibraryTableMultilisteners.remove(myLibraryTable);
      }
    }

    public void afterLibraryAdded(final Library newLibrary) {
      doRootsChangedOnDemand(new Runnable() {
        public void run() {
          myDispatcher.getMulticaster().afterLibraryAdded(newLibrary);
        }
      });
    }

    public void afterLibraryRenamed(final Library library) {
      doRootsChangedOnDemand(new Runnable() {
        public void run() {
          myDispatcher.getMulticaster().afterLibraryRenamed(library);
        }
      });
    }

    public void beforeLibraryRemoved(final Library library) {
      doRootsChangedOnDemand(new Runnable() {
        public void run() {
          myDispatcher.getMulticaster().beforeLibraryRemoved(library);
        }
      });
    }

    public void afterLibraryRemoved(final Library library) {
      doRootsChangedOnDemand(new Runnable() {
        public void run() {
          myDispatcher.getMulticaster().afterLibraryRemoved(library);
        }
      });
    }
  }

  private JdkTableMultilistener myJdkTableMultilistener = null;

  private class JdkTableMultilistener implements ProjectJdkTable.Listener {
    EventDispatcher<ProjectJdkTable.Listener> myDispatcher = EventDispatcher.create(ProjectJdkTable.Listener.class);
    Set<ProjectJdkTable.Listener> myListeners = new HashSet<ProjectJdkTable.Listener>();

    private JdkTableMultilistener() {
      ProjectJdkTable.getInstance().addListener(this);
    }

    private void addListener(ProjectJdkTable.Listener listener) {
      myDispatcher.addListener(listener);
      myListeners.add(listener);
    }

    private void removeListener(ProjectJdkTable.Listener listener) {
      myDispatcher.removeListener(listener);
      myListeners.remove(listener);
      uninstallListner(true);
    }

    public void jdkAdded(final ProjectJdk jdk) {
      doRootsChangedOnDemand(new Runnable() {
        public void run() {
          myDispatcher.getMulticaster().jdkAdded(jdk);
        }
      });
    }

    public void jdkRemoved(final ProjectJdk jdk) {
      doRootsChangedOnDemand(new Runnable() {
        public void run() {
          myDispatcher.getMulticaster().jdkRemoved(jdk);
        }
      });
    }

    public void jdkNameChanged(final ProjectJdk jdk, final String previousName) {
      doRootsChangedOnDemand(new Runnable() {
        public void run() {
          myDispatcher.getMulticaster().jdkNameChanged(jdk, previousName);
        }
      });
      String currentName = getProjectJdkName();
      if (previousName != null && previousName.equals(currentName)) {
        // if already had jdk name and that name was the name of the jdk just changed
        myProjectJdkName = jdk.getName();
        myProjectJdkType = jdk.getSdkType().getName();
      }
    }

    public void uninstallListner(boolean soft) {
      if (!soft || !myDispatcher.hasListeners()) {
        ProjectJdkTable.getInstance().removeListener(this);
      }
    }
  }

  private final Map<RootProvider, RootSetChangedMulticaster> myRegisteredRootProviderListeners = new HashMap<RootProvider, RootSetChangedMulticaster>();

  void addJdkTableListener(ProjectJdkTable.Listener jdkTableListener) {
    getJdkTableMultiListener().addListener(jdkTableListener);
  }

  private JdkTableMultilistener getJdkTableMultiListener() {
    if (myJdkTableMultilistener == null) {
      myJdkTableMultilistener = new JdkTableMultilistener();
    }
    return myJdkTableMultilistener;
  }


  void removeJdkTableListener(ProjectJdkTable.Listener jdkTableListener) {
    if (myJdkTableMultilistener == null) return;
    myJdkTableMultilistener.removeListener(jdkTableListener);
  }

  private class RootSetChangedMulticaster implements RootProvider.RootSetChangedListener {
    EventDispatcher<RootProvider.RootSetChangedListener> myDispatcher = EventDispatcher.create(RootProvider.RootSetChangedListener.class);
    private final RootProvider myProvider;

    private RootSetChangedMulticaster(RootProvider provider) {
      myProvider = provider;
      provider.addRootSetChangedListener(this);
      myRegisteredRootProviderListeners.put(myProvider, this);
    }

    private void addListener(RootProvider.RootSetChangedListener listener) {
      myDispatcher.addListener(listener);
    }

    private void removeListener(RootProvider.RootSetChangedListener listener) {
      myDispatcher.removeListener(listener);
      if (!myDispatcher.hasListeners()) {
        myProvider.removeRootSetChangedListener(this);
        myRegisteredRootProviderListeners.remove(myProvider);
      }
    }

    public void rootSetChanged(final RootProvider wrapper) {
      LOG.assertTrue(myProvider.equals(wrapper));
      Runnable runnable = new Runnable() {
        public void run() {
          myDispatcher.getMulticaster().rootSetChanged(wrapper);
        }
      };
      doRootsChangedOnDemand(runnable);
    }

  }

  public long getModificationCount() {
    return myModificationCount;
  }


}
