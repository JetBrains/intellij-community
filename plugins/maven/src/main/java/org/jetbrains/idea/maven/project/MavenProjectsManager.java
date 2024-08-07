// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.configurationStore.SettingsSavingComponentJavaAdapter;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.ModifiableModelCommitter;
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.EventDispatcher;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.PathKt;
import com.intellij.util.ui.update.Update;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.idea.maven.buildtool.MavenImportSpec;
import org.jetbrains.idea.maven.buildtool.MavenSyncConsole;
import org.jetbrains.idea.maven.buildtool.MavenSyncSpec;
import org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes.CacheForCompilerErrorMessages;
import org.jetbrains.idea.maven.importing.MavenImportUtil;
import org.jetbrains.idea.maven.importing.MavenPomPathModuleService;
import org.jetbrains.idea.maven.importing.MavenProjectImporter;
import org.jetbrains.idea.maven.importing.workspaceModel.WorkspaceProjectImporterKt;
import org.jetbrains.idea.maven.indices.MavenIndicesManager;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.navigator.MavenProjectsNavigator;
import org.jetbrains.idea.maven.project.auto.reload.MavenProjectManagerWatcher;
import org.jetbrains.idea.maven.tasks.MavenShortcutsManager;
import org.jetbrains.idea.maven.tasks.MavenTasksManager;
import org.jetbrains.idea.maven.utils.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.jetbrains.idea.maven.server.MavenWrapperSupport.getWrapperDistributionUrl;

@State(name = "MavenProjectsManager")
public abstract class MavenProjectsManager extends MavenSimpleProjectComponent
  implements PersistentStateComponent<MavenProjectsManagerState>, SettingsSavingComponentJavaAdapter, Disposable,
             MavenAsyncProjectsManager {
  private final ReentrantLock initLock = new ReentrantLock();
  private final AtomicBoolean projectsTreeInitialized = new AtomicBoolean();
  private final AtomicBoolean isInitialized = new AtomicBoolean();
  private final AtomicBoolean isActivated = new AtomicBoolean();

  @NotNull
  private MavenProjectsManagerState myState = new MavenProjectsManagerState();

  private final MavenEmbeddersManager myEmbeddersManager;

  private MavenProjectsTree myProjectsTree;
  private MavenProjectManagerWatcher myWatcher;

  private final EventDispatcher<MavenProjectsTree.Listener> myProjectsTreeDispatcher =
    EventDispatcher.create(MavenProjectsTree.Listener.class);
  private final List<Listener> myManagerListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final ModificationTracker myModificationTracker;

  private final AtomicReference<MavenSyncConsole> mySyncConsole = new AtomicReference<>();
  private final MavenMergingUpdateQueue mySaveQueue;
  private static final int SAVE_DELAY = 1000;
  protected Module myPreviewModule;
  private transient boolean forceUpdateSnapshots = false;

  public static MavenProjectsManager getInstance(@NotNull Project project) {
    return project.getService(MavenProjectsManager.class);
  }

  @Nullable
  public static MavenProjectsManager getInstanceIfCreated(@NotNull Project project) {
    return project.getServiceIfCreated(MavenProjectsManager.class);
  }

  public MavenProjectsManager(@NotNull Project project, @NotNull CoroutineScope coroutineScope) {
    super(project);

    myEmbeddersManager = new MavenEmbeddersManager(project);
    myModificationTracker = new MavenModificationTracker(this);
    mySaveQueue = new MavenMergingUpdateQueue("Maven save queue", SAVE_DELAY, !MavenUtil.isMavenUnitTestModeEnabled(), coroutineScope);
    MavenRehighlighter.install(project, this);
    Disposer.register(this, this::projectClosed);
    CacheForCompilerErrorMessages.connectToJdkListener(project, this);
  }

  @Override
  @NotNull
  public MavenProjectsManagerState getState() {
    if (isInitialized()) {
      applyTreeToState();
    }
    return myState;
  }

  @Override
  public void loadState(@NotNull MavenProjectsManagerState state) {
    myState = state;
    if (isInitialized()) {
      applyStateToTree(getProjectsTree(), this);
      scheduleUpdateAllMavenProjects(MavenSyncSpec.incremental("MavenProjectsManager.loadState"));
    }
  }

  @Override
  public void dispose() {
    mySyncConsole.set(null);
    myManagerListeners.clear();
  }

  public ModificationTracker getModificationTracker() {
    return myModificationTracker;
  }

  public MavenGeneralSettings getGeneralSettings() {
    MavenGeneralSettings generalSettings = getWorkspaceSettings().getGeneralSettings();
    generalSettings.setProject(myProject);
    return generalSettings;
  }

  public MavenImportingSettings getImportingSettings() {
    return getWorkspaceSettings().getImportingSettings();
  }

  private MavenWorkspaceSettings getWorkspaceSettings() {
    return MavenWorkspaceSettingsComponent.getInstance(myProject).getSettings();
  }

  public File getLocalRepository() {
    return getGeneralSettings().getEffectiveLocalRepository();
  }

  @ApiStatus.Internal
  public int getFilterConfigCrc(@NotNull ProjectFileIndex projectFileIndex) {
    return getProjectsTree().getFilterConfigCrc(projectFileIndex);
  }

  @TestOnly
  public void initForTests() {
    initProjectsTree();
    doInit();
  }

  private void doInit() {
    initLock.lock();
    try {
      if (isInitialized.getAndSet(true)) {
        return;
      }
      initPreloadMavenServices();
      initWorkers();
      updateTabTitles();
    }
    finally {
      initLock.unlock();
    }
  }

  private void doActivate() {
    if (isActivated.getAndSet(true)) {
      return;
    }
    fireActivated();
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      listenForExternalChanges();
      MavenIndicesManager.getInstance(myProject).scheduleUpdateIndicesList();
    }
  }

  protected void onProjectStartup() {
    if (!isNormalProject()) return;

    boolean wasMavenized = !myState.originalFiles.isEmpty();
    if (!wasMavenized) return;

    initProjectsTree();
    doInit();
    doActivate();
    var forceImport =
      Boolean.TRUE.equals(myProject.getUserData(WorkspaceProjectImporterKt.getNOTIFY_USER_ABOUT_WORKSPACE_IMPORT_KEY()));
    if (forceImport) {
      scheduleUpdateAllMavenProjects(MavenSyncSpec.full("MavenProjectsManager.onProjectStartup"));
    }
  }


  private void initPreloadMavenServices() {
    // init maven tool window
    MavenProjectsNavigator.getInstance(myProject);
    // add CompileManager before/after tasks
    MavenTasksManager.getInstance(myProject);
    // init maven shortcuts manager to subscribe to KeymapManagerListener
    MavenShortcutsManager.getInstance(myProject);
  }

  private void updateTabTitles() {
    Application app = ApplicationManager.getApplication();
    if (MavenUtil.isMavenUnitTestModeEnabled() || app.isHeadlessEnvironment()) {
      return;
    }

    addProjectsTreeListener(new MavenProjectsTree.Listener() {
      @Override
      public void projectsUpdated(List<? extends Pair<MavenProject, MavenProjectChanges>> updated, List<? extends MavenProject> deleted) {
        updateTabName(MavenUtil.collectFirsts(updated), myProject);
      }
    });
  }

  private static void updateTabName(@NotNull List<MavenProject> projects, @NotNull Project project) {
    MavenUtil.invokeLater(project, () -> {
      for (MavenProject each : projects) {
        FileEditorManagerEx.getInstanceEx(project).updateFilePresentation(each.getFile());
      }
    });
  }

  public MavenSyncConsole getSyncConsole() {
    if (null == mySyncConsole.get()) {
      mySyncConsole.compareAndSet(null, new MavenSyncConsole(myProject));
    }
    return mySyncConsole.get();
  }

  private void initProjectsTree() {
    initLock.lock();
    try {
      if (projectsTreeInitialized.getAndSet(true)) return;

      if (!PlatformProjectOpenProcessor.Companion.isNewProject(myProject)) {
        loadTree();
      }

      if (myProjectsTree == null) {
        myProjectsTree = new MavenProjectsTree(myProject);
        applyStateToTree(myProjectsTree, this);
      }

      myProjectsTree.addListener(myProjectsTreeDispatcher.getMulticaster(), this);
    }
    finally {
      initLock.unlock();
    }
  }

  private void loadTree() {
    try {
      Path file = getProjectsTreeFile();
      if (Files.exists(file)) {
        var readTree = MavenProjectsTree.read(myProject, file);
        if (null != readTree) {
          myProjectsTree = readTree;
        }
        else {
          MavenLog.LOG.warn("Could not load existing tree, read null");
        }
      }
    }
    catch (IOException e) {
      MavenLog.LOG.info(e);
    }
  }

  private void applyTreeToState() {
    var tree = getProjectsTree();
    myState.originalFiles = tree.getManagedFilesPaths();
    myState.ignoredFiles = new HashSet<>(tree.getIgnoredFilesPaths());
    myState.ignoredPathMasks = tree.getIgnoredFilesPatterns();
  }

  private static void applyStateToTree(MavenProjectsTree tree, MavenProjectsManager manager) {
    MavenWorkspaceSettings settings = manager.getWorkspaceSettings();
    MavenExplicitProfiles explicitProfiles = new MavenExplicitProfiles(settings.enabledProfiles, settings.disabledProfiles);
    tree.resetManagedFilesPathsAndProfiles(manager.myState.originalFiles, explicitProfiles);
    tree.setIgnoredFilesPaths(new ArrayList<>(manager.myState.ignoredFiles));
    tree.setIgnoredFilesPatterns(manager.myState.ignoredPathMasks);
  }

  @Override
  public void doSave() {
    mySaveQueue.queue(new Update(this) {
      @Override
      public void run() {
        try {
          MavenProjectsTree tree = myProjectsTree;
          if (tree == null) {
            return;
          }
          tree.save(getProjectsTreeFile());
        }
        catch (IOException e) {
          MavenLog.LOG.info(e);
        }
      }
    });
  }

  @ApiStatus.Internal
  public Path getProjectsTreeFile() {
    return getProjectCacheDir().resolve("tree.dat");
  }

  @ApiStatus.Internal
  public Path getProjectCacheDir() {
    return getProjectsTreesDir().resolve(myProject.getLocationHash());
  }

  @NotNull
  @ApiStatus.Internal
  public static Path getProjectsTreesDir() {
    return MavenUtil.getPluginSystemDir("Projects");
  }

  private void initWorkers() {
    myWatcher = new MavenProjectManagerWatcher(myProject, myProjectsTree);
  }

  public void listenForExternalChanges() {
    myWatcher.start();
  }

  @TestOnly
  public void enableAutoImportInTests() {
    assert isInitialized();
    listenForExternalChanges();
    myWatcher.enableAutoImportInTests();
  }

  private void projectClosed() {
    initLock.lock();
    try {
      if (!isInitialized.getAndSet(false)) {
        return;
      }

      myWatcher.stop();

      mySaveQueue.flush();

      if (MavenUtil.isMavenUnitTestModeEnabled()) {
        PathKt.delete(getProjectsTreesDir());
      }
    }
    finally {
      initLock.unlock();
    }
  }

  public MavenEmbeddersManager getEmbeddersManager() {
    return myEmbeddersManager;
  }

  private boolean isInitialized() {
    return !initLock.isLocked() && isInitialized.get();
  }

  public boolean isMavenizedProject() {
    return isInitialized();
  }

  public boolean isMavenizedModule(@NotNull Module m) {
    return MavenUtil.isMavenizedModule(m);
  }

  protected void doAddManagedFilesWithProfiles(List<VirtualFile> files, MavenExplicitProfiles profiles, Module previewModuleToDelete) {
    myPreviewModule = previewModuleToDelete;
    if (!isInitialized()) {
      doInit();
      doActivate();
      var distributionUrl = getWrapperDistributionUrl(ProjectUtil.guessProjectDir(myProject));
      if (distributionUrl != null) {
        getGeneralSettings().setMavenHomeType(MavenWrapper.INSTANCE);
      }
    }
    getProjectsTree().addManagedFilesWithProfiles(files, profiles);
  }

  public void addManagedFiles(@NotNull List<VirtualFile> files) {
    doAddManagedFilesWithProfiles(files, MavenExplicitProfiles.NONE, null);
    scheduleUpdateAllMavenProjects(MavenSyncSpec.incremental("MavenProjectsManager.addManagedFiles"));
  }

  public void addManagedFilesOrUnignoreNoUpdate(@NotNull List<VirtualFile> files) {
    removeIgnoredFilesPaths(MavenUtil.collectPaths(files));
    doAddManagedFilesWithProfiles(files, MavenExplicitProfiles.NONE, null);
  }

  public void addManagedFilesOrUnignore(@NotNull List<VirtualFile> files) {
    addManagedFilesOrUnignoreNoUpdate(files);
    scheduleUpdateAllMavenProjects(MavenSyncSpec.incremental("MavenProjectsManager.addManagedFilesOrUnignore"));
  }

  public boolean isManagedFile(@NotNull VirtualFile f) {
    return getProjectsTree().isManagedFile(f);
  }

  @NotNull
  public MavenExplicitProfiles getExplicitProfiles() {
    return getProjectsTree().getExplicitProfiles();
  }

  @NotNull
  public Collection<String> getAvailableProfiles() {
    return getProjectsTree().getAvailableProfiles();
  }

  @NotNull
  public Collection<Pair<String, MavenProfileKind>> getProfilesWithStates() {
    return getProjectsTree().getProfilesWithStates();
  }

  public boolean hasProjects() {
    return getProjectsTree().hasProjects();
  }

  @NotNull
  public List<MavenProject> getProjects() {
    return getProjectsTree().getProjects();
  }

  @NotNull
  public List<MavenProject> getRootProjects() {
    return getProjectsTree().getRootProjects();
  }

  @NotNull
  public List<MavenProject> getNonIgnoredProjects() {
    return getProjectsTree().getNonIgnoredProjects();
  }

  @NotNull
  public List<VirtualFile> getProjectsFiles() {
    return getProjectsTree().getProjectsFiles();
  }

  @Nullable
  public MavenProject findProject(@NotNull VirtualFile f) {
    return getProjectsTree().findProject(f);
  }


  public MavenProject findSingleProjectInReactor(@NotNull MavenId id) {
    return getProjectsTree().findSingleProjectInReactor(id);
  }


  @Nullable
  public MavenProject findProject(@NotNull MavenId id) {
    return getProjectsTree().findProject(id);
  }

  @Nullable
  public MavenProject findProject(@NotNull MavenArtifact artifact) {
    return getProjectsTree().findProject(artifact);
  }

  @Nullable
  public MavenProject findProject(@NotNull Module module) {
    MavenProject mavenProject = getMavenProject(module);
    String moduleName = module.getName();
    if (mavenProject == null && MavenImportUtil.isMainOrTestSubmodule(moduleName)) {
      Module parentModule = ModuleManager.getInstance(myProject).findModuleByName(MavenImportUtil.getParentModuleName(moduleName));
      mavenProject = parentModule != null ? getMavenProject(parentModule) : null;
    }
    return mavenProject;
  }

  private MavenProject getMavenProject(@NotNull Module module) {
    return CachedValuesManager.getManager(module.getProject()).getCachedValue(module, () -> {
      VirtualFile f = findPomFile(module, new MavenModelsProvider() {
        @Override
        public Module[] getModules() {
          throw new UnsupportedOperationException();
        }

        @Override
        public VirtualFile[] getContentRoots(Module module) {
          return ModuleRootManager.getInstance(module).getContentRoots();
        }
      });
      MavenProject result = f == null ? null : findProject(f);
      return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  @RequiresReadLock
  public @Nullable Module findModule(@NotNull MavenProject project) {
    if (!isInitialized()) return null;
    return ProjectRootManager.getInstance(myProject).getFileIndex().getModuleForFile(project.getFile());
  }

  @NotNull
  public Collection<MavenProject> findInheritors(@Nullable MavenProject parent) {
    if (parent == null) return Collections.emptyList();
    return getProjectsTree().findInheritors(parent);
  }

  @Nullable
  public MavenProject findContainingProject(@NotNull VirtualFile file) {
    if (!isInitialized()) return null;
    Module module = ProjectRootManager.getInstance(myProject).getFileIndex().getModuleForFile(file);
    return module == null ? null : findProject(module);
  }

  @Nullable
  private VirtualFile findPomFile(@NotNull Module module, @NotNull MavenModelsProvider modelsProvider) {
    String pomFileUrl = MavenPomPathModuleService.getInstance(module).getPomFileUrl();
    if (pomFileUrl != null) {
      return VirtualFileManager.getInstance().findFileByUrl(pomFileUrl);
    }
    for (VirtualFile root : modelsProvider.getContentRoots(module)) {
      List<VirtualFile> pomFiles = MavenUtil.streamPomFiles(module.getProject(), root).toList();
      if (pomFiles.isEmpty()) {
        continue;
      }

      if (pomFiles.size() == 1) {
        return pomFiles.get(0);
      }

      for (VirtualFile file : pomFiles) {
        if (module.getName().equals(file.getNameWithoutExtension())) {
          return file;
        }
        MavenProject mavenProject = findProject(file);
        if (mavenProject != null) {
          if (module.getName().equals(mavenProject.getMavenId().getArtifactId())) {
            return file;
          }
        }
      }
    }
    return null;
  }

  @Nullable
  public MavenProject findAggregator(@NotNull MavenProject mavenProject) {
    return getProjectsTree().findAggregator(mavenProject);
  }

  @Nullable
  public MavenProject findRootProject(@NotNull MavenProject mavenProject) {
    return getProjectsTree().findRootProject(mavenProject);
  }

  @NotNull
  public List<MavenProject> getModules(@NotNull MavenProject aggregator) {
    return getProjectsTree().getModules(aggregator);
  }

  @NotNull
  public List<String> getIgnoredFilesPaths() {
    return getProjectsTree().getIgnoredFilesPaths();
  }

  public void setIgnoredFilesPaths(@NotNull List<String> paths) {
    getProjectsTree().setIgnoredFilesPaths(paths);
  }

  public void removeIgnoredFilesPaths(final Collection<String> paths) {
    getProjectsTree().removeIgnoredFilesPaths(paths);
  }

  public boolean getIgnoredState(@NotNull MavenProject project) {
    return getProjectsTree().getIgnoredState(project);
  }

  @ApiStatus.Internal
  public void setIgnoredStateForPoms(@NotNull List<String> pomPaths, boolean ignored) {
    getProjectsTree().setIgnoredStateForPoms(pomPaths, ignored);
  }

  public void setIgnoredState(@NotNull List<MavenProject> projects, boolean ignored) {
    getProjectsTree().setIgnoredState(projects, ignored);
  }

  @NotNull
  public List<String> getIgnoredFilesPatterns() {
    return getProjectsTree().getIgnoredFilesPatterns();
  }

  public void setIgnoredFilesPatterns(@NotNull List<String> patterns) {
    getProjectsTree().setIgnoredFilesPatterns(patterns);
  }

  public boolean isIgnored(@NotNull MavenProject project) {
    return getProjectsTree().isIgnored(project);
  }

  public Set<MavenRemoteRepository> getRemoteRepositories() {
    Set<MavenRemoteRepository> result = new HashSet<>();
    for (MavenProject each : getProjects()) {
      result.addAll(each.getRemoteRepositories());
    }
    return result;
  }

  @TestOnly
  public MavenProjectsTree getProjectsTreeForTests() {
    return myProjectsTree;
  }

  @ApiStatus.Internal
  @NotNull
  public MavenProjectsTree getProjectsTree() {
    if (myProjectsTree == null) {
      initProjectsTree();
    }
    return myProjectsTree;
  }

  /**
   * @deprecated Use {@link #scheduleUpdateAllMavenProjects(MavenSyncSpec)}
   */
  @Deprecated(forRemoval = true)
  protected abstract List<Module> updateAllMavenProjectsSync(MavenImportSpec spec);

  public synchronized void removeManagedFiles(@NotNull List<@NotNull VirtualFile> files) {
    getProjectsTree().removeManagedFiles(files);
    scheduleUpdateAllMavenProjects(MavenSyncSpec.full("MavenProjectsManager.removeManagedFiles", true));
  }

  public synchronized void setExplicitProfiles(MavenExplicitProfiles profiles) {
    getProjectsTree().setExplicitProfiles(profiles);
  }

  @ApiStatus.Internal
  public void forceUpdateProjects() {
    scheduleUpdateAllMavenProjects(MavenSyncSpec.full("MavenProjectsManager.forceUpdateProjects", true));
  }

  /**
   * @deprecated Use {@link #scheduleForceUpdateMavenProjects(List)}}
   */
  @Deprecated
  // used in third-party plugins
  public AsyncPromise<Void> forceUpdateProjects(@NotNull Collection<@NotNull MavenProject> projects) {
    return doForceUpdateProjects(projects);
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated
  protected abstract AsyncPromise<Void> doForceUpdateProjects(@NotNull Collection<@NotNull MavenProject> projects);

  public void forceUpdateAllProjectsOrFindAllAvailablePomFiles() {
    forceUpdateAllProjectsOrFindAllAvailablePomFiles(
      MavenSyncSpec.full("MavenProjectsManager.forceUpdateAllProjectsOrFindAllAvailablePomFiles", true));
  }

  private void forceUpdateAllProjectsOrFindAllAvailablePomFiles(MavenSyncSpec spec) {
    if (!isMavenizedProject()) {
      addManagedFiles(collectAllAvailablePomFiles());
      return;
    }
    scheduleUpdateAllMavenProjects(spec);
  }

  /**
   * Returned {@link Promise} instance isn't guarantied to be marked as rejected in all cases where importing wasn't performed (e.g.
   * if project is closed)
   *
   * @deprecated Use {@link #scheduleUpdateAllMavenProjects(MavenSyncSpec)}}
   */
  // used in third-party plugins
  @Deprecated
  public Promise<List<Module>> scheduleImportAndResolve() {
    var promise = new AsyncPromise<List<Module>>();
    var modules = updateAllMavenProjectsSync(MavenImportSpec.IMPLICIT_IMPORT);
    promise.setResult(modules);
    return promise;
  }

  public void showServerException(Throwable e) {
    getSyncConsole().addException(e);
  }

  public void terminateImport(int exitCode) {
    getSyncConsole().terminated(exitCode);
  }

  /**
   * @deprecated use {@link MavenFolderResolver}
   */
  // used in third-party plugins
  @Deprecated
  public void scheduleFoldersResolveForAllProjects() {
    MavenProjectsManagerUtilKt.scheduleFoldersResolveForAllProjects(myProject);
  }

  /**
   * @deprecated This method returns immediately. Kept for compatibility reasons.
   */
  @Deprecated(forRemoval = true)
  public void waitForPostImportTasksCompletion() {
  }

  public void updateProjectTargetFolders() {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (myProject.isDisposed()) return;

      MavenProjectImporter.tryUpdateTargetFolders(myProject);
      VirtualFileManager.getInstance().asyncRefresh();
    });
  }

  /**
   * @deprecated Use {@link #scheduleUpdateAllMavenProjects(MavenSyncSpec)}}
   */
  @Deprecated(forRemoval = true)
  // used in third-party plugins
  public List<Module> importProjects() {
    scheduleUpdateAllMavenProjects(MavenSyncSpec.full("MavenProjectsManager.importProjects"));
    return List.of();
  }

  @ApiStatus.Internal
  public Map<VirtualFile, Module> getFileToModuleMapping(MavenModelsProvider modelsProvider) {
    Map<VirtualFile, Module> result = new HashMap<>();
    for (Module each : modelsProvider.getModules()) {
      VirtualFile f = findPomFile(each, modelsProvider);
      if (f != null) result.put(f, each);
    }
    return result;
  }

  @ApiStatus.Internal
  public List<VirtualFile> collectAllAvailablePomFiles() {
    List<VirtualFile> result = new ArrayList<>(getFileToModuleMapping(new MavenDefaultModelsProvider(myProject)).keySet());
    MavenUtil.streamPomFiles(myProject, myProject.getBaseDir()).forEach(result::add);
    return result;
  }


  /**
   * @deprecated use addManagerListener(Listener, Disposable) instead
   */
  @Deprecated
  public void addManagerListener(Listener listener) {
    myManagerListeners.add(listener);
  }

  public void addManagerListener(Listener listener, @NotNull Disposable parentDisposable) {
    myManagerListeners.add(listener);
    Disposer.register(parentDisposable, () -> myManagerListeners.remove(listener));
  }

  public void addProjectsTreeListener(MavenProjectsTree.Listener listener) {
    myProjectsTreeDispatcher.addListener(listener, this);
  }

  public void addProjectsTreeListener(@NotNull MavenProjectsTree.Listener listener, @NotNull Disposable parentDisposable) {
    myProjectsTreeDispatcher.addListener(listener, parentDisposable);
  }

  @TestOnly
  public void fireActivatedInTests() {
    fireActivated();
  }

  private void fireActivated() {
    for (Listener each : myManagerListeners) {
      each.activated();
    }
  }

  protected void fireImportAndResolveScheduled() {
    for (Listener each : myManagerListeners) {
      each.importAndResolveScheduled();
    }
  }


  void fireProjectImportCompleted() {
    for (Listener each : myManagerListeners) {
      each.projectImportCompleted();
    }
  }

  public void setForceUpdateSnapshots(boolean forceUpdateSnapshots) {
    this.forceUpdateSnapshots = forceUpdateSnapshots;
  }

  public boolean getForceUpdateSnapshots() {
    return forceUpdateSnapshots;
  }

  @ApiStatus.Internal
  @RequiresEdt
  public void removeManagedFiles(List<VirtualFile> selectedFiles,
                                 @Nullable Consumer<MavenProject> removeNotification,
                                 @Nullable Predicate<List<String>> removeConfirmation) {
    List<VirtualFile> removableFiles = new ArrayList<>();
    List<String> filesToUnIgnore = new ArrayList<>();

    List<Module> modulesToRemove = new ArrayList<>();

    for (VirtualFile pomXml : selectedFiles) {
      if (isManagedFile(pomXml)) {
        MavenProject managedProject = findProject(pomXml);
        if (managedProject == null) {
          continue;
        }
        addModuleToRemoveList(modulesToRemove, managedProject);
        getModules(managedProject).forEach(mp -> {
          addModuleToRemoveList(modulesToRemove, mp);
          filesToUnIgnore.add(mp.getFile().getPath());
        });
        removableFiles.add(pomXml);
        filesToUnIgnore.add(pomXml.getPath());
      }
      else {
        if (removeNotification != null) {
          removeNotification.accept(findProject(pomXml));
        }
      }
    }
    if (removeConfirmation != null && !removeConfirmation.test(ContainerUtil.map(modulesToRemove, m -> m.getName()))) {
      return;
    }
    removeModules(ModuleManager.getInstance(getProject()), modulesToRemove);
    removeManagedFiles(removableFiles);
    removeIgnoredFilesPaths(filesToUnIgnore);
  }

  private void addModuleToRemoveList(List<Module> modulesToRemove, MavenProject project) {
    Module module = findModule(project);
    if (module == null) {
      return;
    }
    modulesToRemove.add(module);
  }

  private static void removeModules(ModuleManager moduleManager, List<Module> modulesToRemove) {
    WriteAction.run(() -> {
      List<ModifiableRootModel> usingModels = new SmartList<>();

      for (Module module : modulesToRemove) {

        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        for (OrderEntry entry : moduleRootManager.getOrderEntries()) {
          if (entry instanceof ModuleOrderEntry) {
            usingModels.add(moduleRootManager.getModifiableModel());
            break;
          }
        }
      }

      final ModifiableModuleModel moduleModel = moduleManager.getModifiableModel();
      for (Module module : modulesToRemove) {
        ModuleDeleteProvider.removeModule(module, usingModels, moduleModel);
      }
      ModifiableModelCommitter.multiCommit(usingModels, moduleModel);
    });
  }

  public interface Listener {
    default void activated() {
    }

    default void importAndResolveScheduled() {
    }

    default void projectImportCompleted() {
    }
  }
}
