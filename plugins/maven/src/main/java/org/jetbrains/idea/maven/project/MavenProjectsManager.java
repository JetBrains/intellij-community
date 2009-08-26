package org.jetbrains.idea.maven.project;

import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.update.Update;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.importing.MavenDefaultModifiableModelsProvider;
import org.jetbrains.idea.maven.importing.MavenFoldersImporter;
import org.jetbrains.idea.maven.importing.MavenModifiableModelsProvider;
import org.jetbrains.idea.maven.importing.MavenProjectImporter;
import org.jetbrains.idea.maven.runner.SoutMavenConsole;
import org.jetbrains.idea.maven.utils.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@State(name = "MavenProjectsManager", storages = {@Storage(id = "default", file = "$PROJECT_FILE$")})
public class MavenProjectsManager extends SimpleProjectComponent implements PersistentStateComponent<MavenProjectsManagerState>,
                                                                            SettingsSavingComponent {
  private static final int IMPORT_DELAY = 1000;

  private final AtomicBoolean isInitialized = new AtomicBoolean();

  private MavenProjectsManagerState myState = new MavenProjectsManagerState();

  private MavenEmbeddersManager myEmbeddersManager;

  private MavenProjectsTree myProjectsTree;
  private MavenProjectsManagerWatcher myWatcher;

  private MavenProjectsProcessor myReadingProcessor;
  private MavenProjectsProcessor myResolvingProcessor;
  private MavenProjectsProcessor myPluginsResolvingProcessor;
  private MavenProjectsProcessor myFoldersResolvingProcessor;
  private MavenProjectsProcessor myArtifactsDownloadingProcessor;
  private MavenProjectsProcessor myPostProcessor;

  private MavenMergingUpdateQueue myImportingQueue;
  private final Object myImportingDataLock = new Object();
  private final Map<MavenProject, MavenProjectChanges> myProjectsToImport = new THashMap<MavenProject, MavenProjectChanges>();
  private boolean myImportModuleGroupsRequired = false;

  private MavenMergingUpdateQueue mySchedulesQueue;

  private final EventDispatcher<MavenProjectsTree.Listener> myProjectsTreeDispatcher
    = EventDispatcher.create(MavenProjectsTree.Listener.class);
  private final List<Listener> myManagerListeners = ContainerUtil.createEmptyCOWList();

  public static MavenProjectsManager getInstance(Project p) {
    return p.getComponent(MavenProjectsManager.class);
  }

  public MavenProjectsManager(Project project) {
    super(project);
  }

  public MavenProjectsManagerState getState() {
    if (isInitialized()) {
      applyTreeToState();
    }
    return myState;
  }

  public void loadState(MavenProjectsManagerState state) {
    myState = state;
    if (isInitialized()) {
      applyStateToTree();
      scheduleUpdateAllProjects();
    }
  }

  public MavenGeneralSettings getGeneralSettings() {
    return getWorkspaceSettings().generalSettings;
  }

  public MavenImportingSettings getImportingSettings() {
    return getWorkspaceSettings().importingSettings;
  }

  private MavenWorkspaceSettings getWorkspaceSettings() {
    return MavenWorkspaceSettingsComponent.getInstance(myProject).getState();
  }

  public File getLocalRepository() {
    return getGeneralSettings().getEffectiveLocalRepository();
  }

  @Override
  public void initComponent() {
    if (!isNormalProject()) return;

    StartupManagerEx.getInstanceEx(myProject).registerStartupActivity(new Runnable() {
      public void run() {
        boolean wasMavenized = !myState.originalFiles.isEmpty();
        if (!wasMavenized) return;
        initMavenized();
      }
    });
  }

  private void initMavenized() {
    doInit(true);
  }

  private void initNew(List<VirtualFile> files, List<String> profiles) {
    myState.originalFiles = MavenUtil.collectPaths(files);
    myState.activeProfiles = profiles;
    doInit(false);
  }

  @TestOnly
  public void initForTests() {
    doInit(true);
  }

  private void doInit(boolean tryToLoadExistingTree) {
    if (isInitialized.getAndSet(true)) return;

    initProjectsTree(tryToLoadExistingTree);

    initWorkers();
    listenForSettingsChanges();
    listenForProjectsTreeChanges();
    listenForProjectProcessors();

    MavenUtil.runWhenInitialized(myProject, new DumbAwareRunnable() {
      public void run() {
        if (!isUnitTestMode()) {
          fireActivated();
          listenForExternalChanges();
        }
        scheduleUpdateAllProjects();
      }
    });
  }

  private void initProjectsTree(boolean tryToLoadExisting) {
    if (tryToLoadExisting) {
      File file = getProjectsTreeFile();
      try {
        if (file.exists()) {
          myProjectsTree = MavenProjectsTree.read(file);
        }
      }
      catch (IOException e) {
        MavenLog.LOG.info(e);
      }
    }

    if (myProjectsTree == null) myProjectsTree = new MavenProjectsTree();
    applyStateToTree();
    myProjectsTree.addListener(myProjectsTreeDispatcher.getMulticaster());
  }

  private void applyTreeToState() {
    myState.originalFiles = myProjectsTree.getManagedFilesPaths();
    myState.activeProfiles = myProjectsTree.getActiveProfiles();
    myState.ignoredFiles = new THashSet<String>(myProjectsTree.getIgnoredFilesPaths());
    myState.ignoredPathMasks = myProjectsTree.getIgnoredFilesPatterns();
  }

  private void applyStateToTree() {
    myProjectsTree.resetManagedFilesPathsAndProfiles(myState.originalFiles, myState.activeProfiles);
    myProjectsTree.setIgnoredFilesPaths(new ArrayList<String>(myState.ignoredFiles));
    myProjectsTree.setIgnoredFilesPatterns(myState.ignoredPathMasks);
  }

  public void save() {
    if (myProjectsTree != null) {
      try {
        myProjectsTree.save(getProjectsTreeFile());
      }
      catch (IOException e) {
        MavenLog.LOG.error(e);
      }
    }
  }

  private File getProjectsTreeFile() {
    File file = new File(getProjectsTreesDir(), myProject.getLocationHash() + "/tree.dat");
    file.getParentFile().mkdirs();
    return file;
  }

  private File getProjectsTreesDir() {
    return MavenUtil.getPluginSystemDir("Projects");
  }

  private void initWorkers() {
    myEmbeddersManager = new MavenEmbeddersManager(getGeneralSettings());

    myReadingProcessor = new MavenProjectsProcessor(myProject,
                                                    ProjectBundle.message("maven.reading"),
                                                    false,
                                                    myEmbeddersManager);
    myResolvingProcessor = new MavenProjectsProcessor(myProject,
                                                      ProjectBundle.message("maven.resolving"),
                                                      true,
                                                      myEmbeddersManager);
    myPluginsResolvingProcessor = new MavenProjectsProcessor(myProject,
                                                             ProjectBundle.message("maven.downloading.plugins"),
                                                             true,
                                                             myEmbeddersManager);
    myFoldersResolvingProcessor = new MavenProjectsProcessor(myProject,
                                                             ProjectBundle.message("maven.updating.folders"),
                                                             true,
                                                             myEmbeddersManager);
    myArtifactsDownloadingProcessor = new MavenProjectsProcessor(myProject,
                                                                 ProjectBundle.message("maven.downloading"),
                                                                 true,
                                                                 myEmbeddersManager);
    myPostProcessor = new MavenProjectsProcessor(myProject,
                                                 ProjectBundle.message("maven.post.processing"),
                                                 true,
                                                 myEmbeddersManager);

    myWatcher = new MavenProjectsManagerWatcher(myProject, myProjectsTree, getGeneralSettings(), myReadingProcessor, myEmbeddersManager);

    myImportingQueue = new MavenMergingUpdateQueue(getComponentName() + ": Importing queue", IMPORT_DELAY, !isUnitTestMode(), myProject);
    myImportingQueue.setPassThrough(false);

    myImportingQueue.makeUserAware(myProject);
    myImportingQueue.makeDumbAware(myProject);
    myImportingQueue.makeModalAware(myProject);

    mySchedulesQueue = new MavenMergingUpdateQueue(getComponentName() + ": Schedules queue", 1000, true, myProject);
    mySchedulesQueue.setPassThrough(false);
  }

  private void listenForSettingsChanges() {
    getImportingSettings().addListener(new MavenImportingSettings.Listener() {
      public void createModuleGroupsChanged() {
        scheduleImport(true);
      }

      public void createModuleForAggregatorsChanged() {
        scheduleImport();
      }
    });
  }

  private void listenForProjectsTreeChanges() {
    myProjectsTree.addListener(new MavenProjectsTree.ListenerAdapter() {
      @Override
      public void projectsIgnoredStateChanged(List<MavenProject> ignored, List<MavenProject> unignored, boolean fromImport) {
        if (!fromImport) scheduleImport();
      }

      @Override
      public void projectsUpdated(List<Pair<MavenProject, MavenProjectChanges>> updated, List<MavenProject> deleted) {
        myEmbeddersManager.clearCaches();

        unscheduleAllTasks(deleted);

        List<MavenProject> updatedProjects = MavenUtil.collectFirsts(updated);

        // import only updated and the dependents (we need to update faced-deps, packaging etc);
        List<Pair<MavenProject, MavenProjectChanges>> toImport = new ArrayList<Pair<MavenProject, MavenProjectChanges>>(updated);
        for (MavenProject each : updatedProjects) {
          for (MavenProject eachDependent : myProjectsTree.getDependentProjects(each)) {
            toImport.add(Pair.create(eachDependent, MavenProjectChanges.DEPENDENCIES));
          }
        }

        // resolve updated, theirs dependents, and dependents of deleted
        Set<MavenProject> toResolve = new THashSet<MavenProject>(updatedProjects);
        for (MavenProject each : ContainerUtil.concat(updatedProjects, deleted)) {
          toResolve.addAll(myProjectsTree.getDependentProjects(each));
        }

        // do not try to resolve projects with syntactic errors
        Iterator<MavenProject> it = toResolve.iterator();
        while (it.hasNext()) {
          MavenProject each = it.next();
          if (each.hasErrors()) it.remove();
        }

        if (haveChanges(toImport) || !deleted.isEmpty()) {
          scheduleImport(toImport);
        }
        scheduleResolve(toResolve);
      }

      private boolean haveChanges(List<Pair<MavenProject, MavenProjectChanges>> projectsWithChanges) {
        for (MavenProjectChanges each : MavenUtil.collectSeconds(projectsWithChanges)) {
          if (each.hasChanges()) return true;
        }
        return false;
      }

      @Override
      public void projectResolved(Pair<MavenProject, MavenProjectChanges> projectWithChanges,
                                  org.apache.maven.project.MavenProject nativeMavenProject) {
        if (!shouldScheduleImport(projectWithChanges)) return;

        if (projectWithChanges.first.hasUnresolvedPlugins()) {
          schedulePluginsResolving(projectWithChanges.first, nativeMavenProject);
        }
        scheduleForNextImport(projectWithChanges);
      }

      @Override
      public void foldersResolved(Pair<MavenProject, MavenProjectChanges> projectWithChanges) {
        if (!shouldScheduleImport(projectWithChanges)) return;
        scheduleForNextImport(projectWithChanges);
      }
    });
  }

  private void listenForProjectProcessors() {
    MavenProjectsProcessor.Listener l = new MavenProjectsProcessor.Listener() {
      public void onIdle() {
        scheduleImport();
      }
    };
    myResolvingProcessor.addListener(l);
    myFoldersResolvingProcessor.addListener(l);
  }

  private boolean shouldScheduleImport(Pair<MavenProject, MavenProjectChanges> projectWithChanges) {
    return !projectWithChanges.first.hasErrors() && projectWithChanges.second.hasChanges();
  }

  public void listenForExternalChanges() {
    myWatcher.start();
  }

  @Override
  public void projectClosed() {
    if (!isInitialized.getAndSet(false)) return;

    Disposer.dispose(myImportingQueue);

    myWatcher.stop();

    myReadingProcessor.stop();
    myResolvingProcessor.stop();
    myPluginsResolvingProcessor.stop();
    myFoldersResolvingProcessor.stop();
    myArtifactsDownloadingProcessor.stop();
    myPostProcessor.stop();

    myEmbeddersManager.release();

    if (isUnitTestMode()) {
      FileUtil.delete(getProjectsTreesDir());
    }
  }

  private boolean isInitialized() {
    return isInitialized.get();
  }

  public boolean isMavenizedProject() {
    return isInitialized();
  }

  public boolean isMavenizedModule(final Module m) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        return "true".equals(m.getOptionValue(getMavenizedModuleOptionName()));
      }
    });
  }

  public void setMavenizedModules(Collection<Module> modules, boolean mavenized) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    for (Module m : modules) {
      if (mavenized) {
        m.setOption(getMavenizedModuleOptionName(), "true");
      }
      else {
        m.clearOption(getMavenizedModuleOptionName());
      }
    }
  }

  private String getMavenizedModuleOptionName() {
    return getComponentName() + ".isMavenModule";
  }

  @TestOnly
  public void resetManagedFilesAndProfilesInTests(List<VirtualFile> files, List<String> profiles) {
    myWatcher.resetManagedFilesAndProfilesInTests(files, profiles);
  }

  public void addManagedFilesWithProfiles(List<VirtualFile> files, List<String> profiles) {
    if (!isInitialized()) {
      initNew(files, profiles);
    }
    else {
      myWatcher.addManagedFilesWithProfiles(files, profiles);
    }
  }

  public void addManagedFiles(List<VirtualFile> files) {
    addManagedFilesWithProfiles(files, Collections.<String>emptyList());
  }

  public void removeManagedFiles(List<VirtualFile> files) {
    myWatcher.removeManagedFiles(files);
  }

  public boolean isManagedFile(VirtualFile f) {
    if (!isInitialized()) return false;
    return myProjectsTree.isManagedFile(f);
  }

  public List<String> getActiveProfiles() {
    if (!isInitialized()) return Collections.emptyList();
    return myProjectsTree.getActiveProfiles();
  }

  public List<String> getAvailableProfiles() {
    if (!isInitialized()) return Collections.emptyList();
    return myProjectsTree.getAvailableProfiles();
  }

  public void setActiveProfiles(List<String> profiles) {
    myWatcher.setActiveProfiles(profiles);
  }

  public boolean hasProjects() {
    if (!isInitialized()) return false;
    return myProjectsTree.hasProjects();
  }

  public List<MavenProject> getProjects() {
    if (!isInitialized()) return Collections.emptyList();
    return myProjectsTree.getProjects();
  }

  public List<MavenProject> getNonIgnoredProjects() {
    if (!isInitialized()) return Collections.emptyList();
    return myProjectsTree.getNonIgnoredProjects();
  }

  public List<VirtualFile> getProjectsFiles() {
    if (!isInitialized()) return Collections.emptyList();
    return myProjectsTree.getProjectsFiles();
  }

  public MavenProject findProject(VirtualFile f) {
    if (!isInitialized()) return null;
    return myProjectsTree.findProject(f);
  }

  public MavenProject findProject(MavenId id) {
    if (!isInitialized()) return null;
    return myProjectsTree.findProject(id);
  }

  public MavenProject findProject(MavenArtifact artifact) {
    if (!isInitialized()) return null;
    return myProjectsTree.findProject(artifact);
  }

  public MavenProject findProject(Module module) {
    VirtualFile f = findPomFile(module, new MavenModelsProvider() {
      public Module[] getModules() {
        throw new UnsupportedOperationException();
      }

      public VirtualFile[] getContentRoots(Module module) {
        return ModuleRootManager.getInstance(module).getContentRoots();
      }
    });
    return f == null ? null : findProject(f);
  }

  public MavenProject findContainingProject(VirtualFile file) {
    if (!isInitialized()) return null;
    Module module = ProjectRootManager.getInstance(myProject).getFileIndex().getModuleForFile(file);
    return module == null ? null : findProject(module);
  }

  private VirtualFile findPomFile(Module module, MavenModelsProvider modelsProvider) {
    for (VirtualFile root : modelsProvider.getContentRoots(module)) {
      final VirtualFile virtualFile = root.findChild(MavenConstants.POM_XML);
      if (virtualFile != null) {
        return virtualFile;
      }
    }
    return null;
  }

  public MavenProject findAggregator(MavenProject module) {
    if (!isInitialized()) return null;
    return myProjectsTree.findAggregator(module);
  }

  public List<MavenProject> getModules(MavenProject aggregator) {
    if (!isInitialized()) return Collections.emptyList();
    return myProjectsTree.getModules(aggregator);
  }

  public List<String> getIgnoredFilesPaths() {
    if (!isInitialized()) return Collections.emptyList();
    return myProjectsTree.getIgnoredFilesPaths();
  }

  public void setIgnoredFilesPaths(List<String> paths) {
    if (!isInitialized()) return;
    myProjectsTree.setIgnoredFilesPaths(paths);
  }

  public boolean getIgnoredState(MavenProject project) {
    if (!isInitialized()) return false;
    return myProjectsTree.getIgnoredState(project);
  }

  public void setIgnoredState(List<MavenProject> projects, boolean ignored) {
    if (!isInitialized()) return;
    myProjectsTree.setIgnoredState(projects, ignored);
  }

  public List<String> getIgnoredFilesPatterns() {
    if (!isInitialized()) return Collections.emptyList();
    return myProjectsTree.getIgnoredFilesPatterns();
  }

  public void setIgnoredFilesPatterns(List<String> patterns) {
    if (!isInitialized()) return;
    myProjectsTree.setIgnoredFilesPatterns(patterns);
  }

  public boolean isIgnored(MavenProject project) {
    if (!isInitialized()) return false;
    return myProjectsTree.isIgnored(project);
  }

  public Set<MavenRemoteRepository> getRemoteRepositories() {
    Set<MavenRemoteRepository> result = new THashSet<MavenRemoteRepository>();
    for (MavenProject each : getProjects()) {
      for (MavenRemoteRepository eachRepository : each.getRemoteRepositories()) {
        result.add(eachRepository);
      }
    }
    return result;
  }

  @TestOnly
  public MavenProjectsTree getProjectsTreeForTests() {
    return myProjectsTree;
  }

  private void scheduleUpdateAllProjects() {
    doScheduleUpdateProjects(null, false);
  }

  public void forceUpdateProjects(Collection<MavenProject> projects) {
    doScheduleUpdateProjects(projects, true);
  }

  public void forceUpdateAllProjectsOrFindAllAvailablePomFiles() {
    if (!isMavenizedProject()) {
      addManagedFiles(collectAllAvailablePomFiles());
    }
    doScheduleUpdateProjects(null, true);
  }

  private void doScheduleUpdateProjects(final Collection<MavenProject> projects, final boolean force) {
    // read when postStartupActivitias start
    MavenUtil.runWhenInitialized(myProject, new DumbAwareRunnable() {
      public void run() {
        MavenProjectsProcessorReadingTask task;
        if (projects == null) {
          task = new MavenProjectsProcessorReadingTask(force, myProjectsTree, getGeneralSettings());
        }
        else {
          task = new MavenProjectsProcessorReadingTask(MavenUtil.collectFiles(projects),
                                                       Collections.EMPTY_LIST,
                                                       force,
                                                       myProjectsTree,
                                                       getGeneralSettings());
        }
        myReadingProcessor.scheduleTask(task);
      }
    });
  }

  private void scheduleResolve(final Collection<MavenProject> projects) {
    runWhenFullyOpen(new Runnable() {
      public void run() {
        for (MavenProject each : projects) {
          myResolvingProcessor.scheduleTask(new MavenProjectsProcessorResolvingTask(each,
                                                                                    myProjectsTree,
                                                                                    getGeneralSettings()));
        }
      }
    });
  }

  @TestOnly
  public void scheduleResolveInTests(Collection<MavenProject> projects) {
    scheduleResolve(projects);
  }

  @TestOnly
  public void scheduleResolveAllInTests() {
    scheduleResolve(getProjects());
  }

  public void scheduleFoldersResolving(final Collection<MavenProject> projects) {
    runWhenFullyOpen(new Runnable() {
      public void run() {
        for (MavenProject each : projects) {
          myFoldersResolvingProcessor.scheduleTask(new MavenProjectsProcessorFoldersResolvingTask(each,
                                                                                                  getImportingSettings(),
                                                                                                  myProjectsTree));
        }
      }
    });
  }

  public void scheduleFoldersResolvingForAllProjects() {
    scheduleFoldersResolving(getProjects());
  }

  private void schedulePluginsResolving(final MavenProject project, final org.apache.maven.project.MavenProject nativeMavenProject) {
    runWhenFullyOpen(new Runnable() {
      public void run() {
        myPluginsResolvingProcessor.scheduleTask(new MavenProjectsProcessorPluginsResolvingTask(project,
                                                                                                nativeMavenProject,
                                                                                                myProjectsTree));
      }
    });
  }

  public void scheduleArtifactsDownloading(final Collection<MavenProject> projects) {
    runWhenFullyOpen(new Runnable() {
      public void run() {
        for (MavenProject each : projects) {
          myArtifactsDownloadingProcessor.scheduleTask(new MavenProjectsProcessorArtifactsDownloadingTask(each, myProjectsTree));
        }
      }
    });
  }

  public void scheduleArtifactsDownloadingForAllProjects() {
    scheduleArtifactsDownloading(getProjects());
  }

  private void scheduleImport(Pair<MavenProject, MavenProjectChanges> projectWithChanges) {
    scheduleImport(Collections.singletonList(projectWithChanges));
  }

  private void scheduleImport(List<Pair<MavenProject, MavenProjectChanges>> projectsWithChanges) {
    scheduleForNextImport(projectsWithChanges);
    scheduleImport();
  }

  private void scheduleImport(boolean importModuleGroupsRequired) {
    synchronized (myImportingDataLock) {
      myImportModuleGroupsRequired = importModuleGroupsRequired;
    }
    scheduleImport();
  }

  private void scheduleImport() {
    runWhenFullyOpen(new Runnable() {
      public void run() {
        myImportingQueue.queue(new Update(MavenProjectsManager.this) {
          public void run() {
            importProjects();
          }
        });
      }
    });
  }

  private void scheduleForNextImport(Pair<MavenProject, MavenProjectChanges> projectWithChanges) {
    scheduleForNextImport(Collections.singletonList(projectWithChanges));
  }

  private void scheduleForNextImport(List<Pair<MavenProject, MavenProjectChanges>> projectsWithChanges) {
    synchronized (myImportingDataLock) {
      for (Pair<MavenProject, MavenProjectChanges> each : projectsWithChanges) {
        MavenProjectChanges changes = each.second.mergedWith(myProjectsToImport.get(each.first));
        myProjectsToImport.put(each.first, changes);
      }
    }
  }

  private void runWhenFullyOpen(final Runnable runnable) {
    if (isNoBackgroundMode()) {
      runnable.run();
      return;
    }

    final Ref<Runnable> wrapper = new Ref<Runnable>();
    wrapper.set(new Runnable() {
      public void run() {
        if (!StartupManagerEx.getInstanceEx(myProject).postStartupActivityPassed()) {
          mySchedulesQueue.queue(new Update(MavenProjectsManager.this) {
            public void run() {
              wrapper.get().run();
            }
          });
          return;
        }
        runnable.run();
      }
    });
    MavenUtil.runWhenInitialized(myProject, wrapper.get());
  }

  @TestOnly
  public void scheduleImportInTests(List<VirtualFile> projectFiles) {
    for (VirtualFile each : projectFiles) {
      MavenProject project = findProject(each);
      if (project != null) {
        scheduleImport(Pair.create(project, MavenProjectChanges.ALL));
      }
    }
  }

  private void schedulePostImportTasts(List<MavenProjectsProcessorTask> postTasks) {
    for (MavenProjectsProcessorTask each : postTasks) {
      myPostProcessor.scheduleTask(each);
    }
  }

  private void unscheduleAllTasks(List<MavenProject> projects) {
    for (MavenProject each : projects) {
      MavenProjectsProcessorEmptyTask dummyTask = new MavenProjectsProcessorEmptyTask(each);

      synchronized (myImportingDataLock) {
        myProjectsToImport.remove(each);
      }

      myResolvingProcessor.removeTask(dummyTask);
      myPluginsResolvingProcessor.removeTask(dummyTask);
      myFoldersResolvingProcessor.removeTask(dummyTask);
      myArtifactsDownloadingProcessor.removeTask(dummyTask);
      myPostProcessor.removeTask(dummyTask);
    }
  }

  @TestOnly
  public void unscheduleAllTasksInTests() {
    unscheduleAllTasks(getProjects());
  }

  public void waitForReadingCompletion() {
    waitForTasksCompletion(Collections.<MavenProjectsProcessor>emptyList());
  }

  public void waitForResolvingCompletion() {
    waitForTasksCompletion(myResolvingProcessor);
  }

  public void waitForFoldersResolvingCompletion() {
    waitForTasksCompletion(myFoldersResolvingProcessor);
  }

  public void waitForPluginsResolvingCompletion() {
    waitForTasksCompletion(myPluginsResolvingProcessor);
  }

  public void waitForArtifactsDownloadingCompletion() {
    waitForTasksCompletion(Arrays.asList(myResolvingProcessor, myArtifactsDownloadingProcessor));
  }

  public void waitForPostImportTasksCompletion() {
    myPostProcessor.waitForCompletion();
  }

  private void waitForTasksCompletion(MavenProjectsProcessor processor) {
    waitForTasksCompletion(Collections.singletonList(processor));
  }

  private void waitForTasksCompletion(List<MavenProjectsProcessor> processors) {
    FileDocumentManager.getInstance().saveAllDocuments();

    myReadingProcessor.waitForCompletion();
    for (MavenProjectsProcessor each : processors) {
      each.waitForCompletion();
    }
  }

  public void flushPendingImportRequestsInTests() {
    myImportingQueue.flush();
  }

  public void updateProjectTargetFolders() {
    updateProjectFolders(true);
  }

  private void updateProjectFolders(final boolean targetFoldersOnly) {
    MavenUtil.invokeLater(myProject, new Runnable() {
      public void run() {
        MavenFoldersImporter.updateProjectFolders(myProject, targetFoldersOnly);
        VirtualFileManager.getInstance().refresh(false);
      }
    });
  }

  public List<Module> importProjects() {
    return importProjects(new MavenDefaultModifiableModelsProvider(myProject));
  }

  public List<Module> importProjects(final MavenModifiableModelsProvider modelsProvider) {
    final Map<MavenProject, MavenProjectChanges> projectsToImportWithChanges;
    final boolean importModuleGroupsRequired;
    synchronized (myImportingDataLock) {
      projectsToImportWithChanges = new THashMap<MavenProject, MavenProjectChanges>(myProjectsToImport);
      myProjectsToImport.clear();
      importModuleGroupsRequired = myImportModuleGroupsRequired;
    }

    long before = System.currentTimeMillis();

    final Ref<MavenProjectImporter> importer = new Ref<MavenProjectImporter>();
    final Ref<List<MavenProjectsProcessorTask>> postTasks = new Ref<List<MavenProjectsProcessorTask>>();

    final Runnable r = new Runnable() {
      public void run() {
        importer.set(new MavenProjectImporter(myProject,
                                              myProjectsTree,
                                              getFileToModuleMapping(modelsProvider),
                                              projectsToImportWithChanges,
                                              importModuleGroupsRequired,
                                              modelsProvider,
                                              getImportingSettings()));
        postTasks.set(importer.get().importProject());
      }
    };

    // called from wizard or ui
    if (ApplicationManager.getApplication().isDispatchThread()) {
      r.run();
    }
    else {
      MavenUtil.runInBackground(myProject, ProjectBundle.message("maven.project.importing"), false, new MavenTask() {
        public void run(MavenProgressIndicator indicator) throws MavenProcessCanceledException {
          r.run();
        }
      }).waitFor();
    }

    long importTime = System.currentTimeMillis() - before;
    //System.out.println("Import/Commit time: " + importTime + "/" + modelsProvider.getCommitTime() + " ms");

    VirtualFileManager.getInstance().refresh(isNormalProject());
    schedulePostImportTasts(postTasks.get());

    // do not block user too often
    myImportingQueue.restartTimer();

    return importer.get().getCreatedModules();
  }

  private Map<VirtualFile, Module> getFileToModuleMapping(MavenModelsProvider modelsProvider) {
    Map<VirtualFile, Module> result = new THashMap<VirtualFile, Module>();
    for (Module each : modelsProvider.getModules()) {
      VirtualFile f = findPomFile(each, modelsProvider);
      if (f != null) result.put(f, each);
    }
    return result;
  }

  private List<VirtualFile> collectAllAvailablePomFiles() {
    List<VirtualFile> result = new ArrayList<VirtualFile>(getFileToModuleMapping(new MavenDefaultModelsProvider(myProject)).keySet());

    VirtualFile pom = myProject.getBaseDir().findChild(MavenConstants.POM_XML);
    if (pom != null) result.add(pom);

    return result;
  }

  public MavenDomDependency addDependency(final MavenProject mavenProject, final MavenId id) {
    final MavenArtifact[] artifact = new MavenArtifact[1];

    try {
      MavenUtil.run(myProject, "Downloading dependency...", new MavenTask() {
        public void run(MavenProgressIndicator indicator) throws MavenProcessCanceledException {
          artifact[0] = myProjectsTree.downloadArtifact(mavenProject, id, myEmbeddersManager, new SoutMavenConsole(), indicator);
        }
      });
    }
    catch (MavenProcessCanceledException ignore) {
      return null;
    }

    VirtualFile file = mavenProject.getFile();
    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);

    MavenDomDependency result = new WriteCommandAction<MavenDomDependency>(myProject, "Add Maven Dependency", psiFile) {
      protected void run(Result<MavenDomDependency> result) throws Throwable {
        MavenDomProjectModel model = MavenDomUtil.getMavenDomProjectModel(myProject, mavenProject.getFile());
        MavenDomDependency domDependency = model.getDependencies().addDependency();
        domDependency.getGroupId().setStringValue(artifact[0].getGroupId());
        domDependency.getArtifactId().setStringValue(artifact[0].getArtifactId());
        domDependency.getVersion().setStringValue(artifact[0].getVersion());

        mavenProject.addDependency(artifact[0]);
        result.setResult(domDependency);
      }
    }.execute().getResultObject();

    scheduleImport(Pair.create(mavenProject, MavenProjectChanges.DEPENDENCIES));

    return result;
  }

  public void addManagerListener(Listener listener) {
    myManagerListeners.add(listener);
  }

  public void addProjectsTreeListener(MavenProjectsTree.Listener listener) {
    myProjectsTreeDispatcher.addListener(listener);
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

  public interface Listener {
    void activated();
  }
}
