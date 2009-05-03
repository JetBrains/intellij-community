package org.jetbrains.idea.maven.project;

import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.EventDispatcher;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.importing.DefaultMavenModuleModelsProvider;
import org.jetbrains.idea.maven.importing.MavenFoldersConfigurator;
import org.jetbrains.idea.maven.importing.MavenModuleModelsProvider;
import org.jetbrains.idea.maven.importing.MavenProjectImporter;
import org.jetbrains.idea.maven.runner.SoutMavenConsole;
import org.jetbrains.idea.maven.utils.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

@State(name = "MavenProjectsManager", storages = {@Storage(id = "default", file = "$PROJECT_FILE$")})
public class MavenProjectsManager extends SimpleProjectComponent implements PersistentStateComponent<MavenProjectsManagerState>,
                                                                            SettingsSavingComponent {
  private static final int IMPORT_DELAY = 1000;

  private final Project myProject;
  private final AtomicBoolean isInitialized = new AtomicBoolean();

  private MavenProjectsManagerState myState = new MavenProjectsManagerState();

  private MavenEmbeddersManager myEmbeddersManager;

  private MavenProjectsTree myProjectsTree;
  private MavenProjectsManagerWatcher myWatcher;

  private MavenProjectsProcessor myReadingProcessor;
  private MavenProjectsProcessor myQuickResolvingProcessor;
  private MavenProjectsProcessor myResolvingProcessor;
  private MavenProjectsProcessor myPluginDownloadingProcessor;
  private MavenProjectsProcessor myFoldersUpdatingProcessor;
  private MavenProjectsProcessor myArtifactsDownloadingProcessor;
  private MavenProjectsProcessor myPostProcessor;

  private MergingUpdateQueue myImportingQueue;
  private final Set<MavenProject> myImportingQueueProjects = new THashSet<MavenProject>();

  private Pattern myIgnoredFilesPatternCache;

  private final EventDispatcher<MavenProjectsTree.Listener> myProjectsTreeDispatcher = EventDispatcher.create(MavenProjectsTree.Listener.class);
  private final List<Listener> myManagerListeners = ContainerUtil.createEmptyCOWList();

  public static MavenProjectsManager getInstance(Project p) {
    return p.getComponent(MavenProjectsManager.class);
  }

  public MavenProjectsManager(Project project) {
    myProject = project;
  }

  public MavenProjectsManagerState getState() {
    if (isInitialized()) {
      myState.originalFiles = myProjectsTree.getManagedFilesPaths();
      myState.activeProfiles = myProjectsTree.getActiveProfiles();
    }
    return myState;
  }

  public void loadState(MavenProjectsManagerState state) {
    myState = state;
    myIgnoredFilesPatternCache = null;
  }

  public MavenGeneralSettings getGeneralSettings() {
    return getWorkspaceSettings().generalSettings;
  }

  public MavenImportingSettings getImportingSettings() {
    return getWorkspaceSettings().importingSettings;
  }

  public MavenDownloadingSettings getDownloadingSettings() {
    return getWorkspaceSettings().downloadingSettings;
  }

  private MavenWorkspaceSettings getWorkspaceSettings() {
    return MavenWorkspaceSettingsComponent.getInstance(myProject).getState();
  }

  @Override
  public void initComponent() {
    if (isUnitTestMode()) return;

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
    myState.originalFiles = ContainerUtil.map(files, new Function<VirtualFile, String>() {
      public String fun(VirtualFile file) {
        return file.getPath();
      }
    });
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
    listenForProjectsTreeChanges();

    scheduleReadAllProjects();

    if (isUnitTestMode()) return;

    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(new Runnable() {
      public void run() {
        listenForExternalChanges();
        fireActivated();
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

    myProjectsTree.resetManagedFilesPathsAndProfiles(myState.originalFiles, myState.activeProfiles);
    myProjectsTree.addListener(myProjectsTreeDispatcher.getMulticaster());
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
    File dir = MavenUtil.getPluginSystemDir("Projects/" + myProject.getLocationHash());
    dir.mkdirs();
    return new File(dir, "tree.dat");
  }

  private void initWorkers() {
    myEmbeddersManager = new MavenEmbeddersManager(getGeneralSettings());

    myReadingProcessor = new MavenProjectsProcessor(myProject, ProjectBundle.message("maven.reading"), myEmbeddersManager);
    myQuickResolvingProcessor = new MavenProjectsProcessor(myProject, ProjectBundle.message("maven.resolving"), myEmbeddersManager);
    myResolvingProcessor = new MavenProjectsProcessor(myProject, ProjectBundle.message("maven.resolving"), myEmbeddersManager);
    myPluginDownloadingProcessor = new MavenProjectsProcessor(myProject,
                                                              ProjectBundle.message("maven.downloading.plugins"),
                                                              myEmbeddersManager);
    myFoldersUpdatingProcessor = new MavenProjectsProcessor(myProject, ProjectBundle.message("maven.updating.folders"), myEmbeddersManager);
    myArtifactsDownloadingProcessor = new MavenProjectsProcessor(myProject, ProjectBundle.message("maven.downloading"), myEmbeddersManager);
    myPostProcessor = new MavenProjectsProcessor(myProject, ProjectBundle.message("maven.post.processing"), myEmbeddersManager);

    myWatcher = new MavenProjectsManagerWatcher(myProject, myProjectsTree, getGeneralSettings(), myReadingProcessor, myEmbeddersManager);

    myImportingQueue = new MergingUpdateQueue(getClass().getName() + ": Importing queue",
                                              IMPORT_DELAY, true, MergingUpdateQueue.ANY_COMPONENT);
    MavenUserAwareUpdatingQueueHelper.attachTo(myImportingQueue);
  }

  private void listenForProjectsTreeChanges() {
    myProjectsTree.addListener(new MavenProjectsTree.ListenerAdapter() {
      public void projectsRead(List<MavenProject> projects) {
        for (MavenProject each : projects) {
          scheduleResolvingTasks(each);
          scheduleFoldersUpdating(each);
        }
        scheduleImport(projects);
      }

      public void projectResolved(boolean quickResolve, MavenProject project, org.apache.maven.project.MavenProject nativeMavenProject) {
        if (!project.isValid()) return;
        if (quickResolve) {
          schedulePluginDownloading(project, nativeMavenProject);
        }
        scheduleImport(project);
      }

      public void projectRemoved(MavenProject project) {
        unscheduleAllTasks(project);
      }
    });
  }

  public void listenForExternalChanges() {
    myWatcher.start();
  }

  @Override
  public void projectClosed() {
    if (!isInitialized.getAndSet(false)) return;

    Disposer.dispose(myImportingQueue);

    myWatcher.stop();

    myReadingProcessor.cancelAndStop();
    myQuickResolvingProcessor.cancelAndStop();
    myResolvingProcessor.cancelAndStop();
    myPluginDownloadingProcessor.cancelAndStop();
    myFoldersUpdatingProcessor.cancelAndStop();
    myArtifactsDownloadingProcessor.cancelAndStop();
    myPostProcessor.cancelAndStop();

    myEmbeddersManager.release();
  }

  private boolean isUnitTestMode() {
    return ApplicationManager.getApplication().isUnitTestMode();
  }

  private boolean isInitialized() {
    return isInitialized.get();
  }

  public boolean isMavenizedProject() {
    return isInitialized();
  }

  public boolean isMavenizedModule(Module m) {
    return "true".equals(m.getOptionValue(getMavenizedModuleOptionName()));
  }

  public void setMavenizedModules(List<Module> modules, boolean mavenized) {
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
    Set<String> result = new THashSet<String>();
    for (MavenProject each : getProjects()) {
      result.addAll(each.getProfilesIds());
    }
    return new ArrayList<String>(result);
  }

  public void setActiveProfiles(List<String> profiles) {
    myWatcher.setActiveProfiles(profiles);
  }

  public List<MavenProject> getProjects() {
    if (!isInitialized()) return Collections.emptyList();
    return myProjectsTree.getProjects();
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

  public MavenProject findProject(Module module) {
    VirtualFile f = findPomFile(module);
    return f == null ? null : findProject(f);
  }

  private VirtualFile findPomFile(Module module) {
    for (VirtualFile root : ModuleRootManager.getInstance(module).getContentRoots()) {
      final VirtualFile virtualFile = root.findChild(MavenConstants.POM_XML);
      if (virtualFile != null) {
        return virtualFile;
      }
    }
    return null;
  }

  public boolean isModuleOf(MavenProject parentNode, MavenProject moduleNode) {
    if (!isInitialized()) return false;
    return myProjectsTree.isModuleOf(parentNode, moduleNode);
  }

  @TestOnly
  public MavenProjectsTree getProjectsTreeForTests() {
    return myProjectsTree;
  }

  private void scheduleReadAllProjects() {
    myReadingProcessor.scheduleTask(new MavenProjectsProcessorReadingTask(myProject, myProjectsTree, getGeneralSettings()));
  }

  private void scheduleResolvingTasks(final MavenProject project) {
    MavenUtil.invokeInDispatchThread(myProject, new Runnable() {
      public void run() {
        myQuickResolvingProcessor.scheduleTask(new MavenProjectsProcessorResolvingTask(true,
                                                                                       project,
                                                                                       myProjectsTree,
                                                                                       getGeneralSettings()));
        myResolvingProcessor.scheduleTask(new MavenProjectsProcessorResolvingTask(false,
                                                                                  project,
                                                                                  myProjectsTree,
                                                                                  getGeneralSettings()));
      }
    });
  }

  private void scheduleFoldersUpdating(final MavenProject project) {
    MavenUtil.invokeInDispatchThread(myProject, new Runnable() {
      public void run() {
        myFoldersUpdatingProcessor.scheduleTask(new MavenProjectsProcessorFoldersUpdatingTask(project,
                                                                                              getImportingSettings(),
                                                                                              myProjectsTree));
      }
    });
  }

  private void schedulePluginDownloading(final MavenProject project, final org.apache.maven.project.MavenProject nativeMavenProject) {
    MavenUtil.invokeInDispatchThread(myProject, new Runnable() {
      public void run() {
        myPluginDownloadingProcessor.scheduleTask(new MavenProjectsProcessorPluginDownloadingTask(project,
                                                                                                  nativeMavenProject,
                                                                                                  myProjectsTree));
      }
    });
  }

  public void scheduleArtifactsDownloading() {
    for (MavenProject each : getProjects()) {
      scheduleArtifactsDownloading(each);
    }
  }

  private void scheduleArtifactsDownloading(MavenProject project) {
    myArtifactsDownloadingProcessor.scheduleTask(new MavenProjectsProcessorArtifactsDownloadingTask(project,
                                                                                                    myProjectsTree,
                                                                                                    getDownloadingSettings()));
  }

  private void scheduleImport(MavenProject project) {
    scheduleImport(Collections.singletonList(project));
  }

  private void scheduleImport(List<MavenProject> projects) {
    synchronized (myImportingQueueProjects) {
      myImportingQueueProjects.addAll(projects);
    }

    if (!isUnitTestMode()) {
      myImportingQueue.queue(new Update(this) {
        public void run() {
          importProjects();
        }
      });
    }
  }

  @TestOnly
  public void scheduleImportInTests(List<VirtualFile> projectFiles) {
    for (VirtualFile each : projectFiles) {
      MavenProject project = findProject(each);
      if (project != null) {
        scheduleImport(project);
      }
    }
  }

  private void schedulePostImportTasts(List<MavenProjectsProcessorPostConfigurationTask> postTasks) {
    for (MavenProjectsProcessorPostConfigurationTask each : postTasks) {
      myPostProcessor.scheduleTask(each);
    }
  }

  private void unscheduleAllTasks(MavenProject project) {
    MavenProjectsProcessorEmptyTask dummyTask = new MavenProjectsProcessorEmptyTask(project);

    myImportingQueueProjects.remove(project);

    myQuickResolvingProcessor.removeTask(dummyTask);
    myResolvingProcessor.removeTask(dummyTask);
    myPluginDownloadingProcessor.removeTask(dummyTask);
    myFoldersUpdatingProcessor.removeTask(dummyTask);
    myArtifactsDownloadingProcessor.removeTask(dummyTask);
    myPostProcessor.removeTask(dummyTask);
  }

  public void waitForReadingCompletion() {
    waitForTasksCompletionAndDo(Collections.<MavenProjectsProcessor>emptyList(), null);
  }

  public void waitForQuickResolvingCompletion() {
    waitForTasksCompletionAndDo(myQuickResolvingProcessor, null);
  }

  public void waitForQuickResolvingCompletionAndImport() {
    waitForTasksCompletionAndImport(myQuickResolvingProcessor);
  }

  public void waitForResolvingCompletionAndImport() {
    waitForTasksCompletionAndImport(myResolvingProcessor);
  }

  public void waitForFoldersUpdatingCompletionAndImport() {
    waitForTasksCompletionAndDo(myFoldersUpdatingProcessor, new Runnable() {
      public void run() {
        updateProjectFolders(false);
      }
    });
  }

  public void waitForPluginsDownloadingCompletion() {
    waitForTasksCompletionAndDo(myPluginDownloadingProcessor, null);
  }

  public void waitForArtifactsDownloadingCompletion() {
    waitForTasksCompletionAndDo(Arrays.asList(myQuickResolvingProcessor, myArtifactsDownloadingProcessor), null);
  }

  public void waitForPostImportTasksCompletion() {
    myPostProcessor.waitForCompletion();
  }

  public void waitForTasksCompletionAndImport(MavenProjectsProcessor processor) {
    waitForTasksCompletionAndDo(processor, new Runnable() {
      public void run() {
        MavenUtil.invokeInDispatchThread(myProject, new Runnable() {
          public void run() {
            importProjects();
          }
        });
      }
    });
  }

  public void waitForTasksCompletionAndDo(MavenProjectsProcessor processor, Runnable runnable) {
    waitForTasksCompletionAndDo(Collections.singletonList(processor), runnable);
  }

  public void waitForTasksCompletionAndDo(List<MavenProjectsProcessor> processors, Runnable runnable) {
    FileDocumentManager.getInstance().saveAllDocuments();

    myReadingProcessor.waitForCompletion();
    for (MavenProjectsProcessor each : processors) {
      each.waitForCompletion();
    }

    if (runnable != null) runnable.run();
  }

  public void updateProjectTargetFolders() {
    updateProjectFolders(true);
  }

  private void updateProjectFolders(final boolean targetFoldersOnly) {
    MavenUtil.invokeInDispatchThread(myProject, new Runnable() {
      public void run() {
        MavenFoldersConfigurator.updateProjectFolders(myProject, targetFoldersOnly);
        VirtualFileManager.getInstance().refresh(false);
      }
    });
  }

  public void findAndImportAllAvailablePomFiles() {
    addManagedFiles(collectAllAvailablePomFiles());
    waitForQuickResolvingCompletionAndImport();
  }

  public List<Module> importProjects() {
    return importProjects(new DefaultMavenModuleModelsProvider(myProject));
  }

  public List<Module> importProjects(final MavenModuleModelsProvider moduleModelsProvider) {
    List<Module> result = new WriteAction<List<Module>>() {
      protected void run(Result<List<Module>> result) throws Throwable {
        Set<MavenProject> projectsToImport;
        synchronized (myImportingQueueProjects) {
          projectsToImport = new THashSet<MavenProject>(myImportingQueueProjects);
          myImportingQueueProjects.clear();
        }

        MavenProjectImporter importer = new MavenProjectImporter(myProject,
                                                                 myProjectsTree,
                                                                 getFileToModuleMapping(),
                                                                 projectsToImport,
                                                                 moduleModelsProvider,
                                                                 getImportingSettings());

        schedulePostImportTasts(importer.importProject());
        result.setResult(importer.getCreatedModules());
      }
    }.execute().getResultObject();
    VirtualFileManager.getInstance().refresh(false);
    return result;
  }

  private Map<VirtualFile, Module> getFileToModuleMapping() {
    Map<VirtualFile, Module> result = new THashMap<VirtualFile, Module>();
    for (Module each : ModuleManager.getInstance(myProject).getModules()) {
      VirtualFile f = findPomFile(each);
      if (f != null) result.put(f, each);
    }
    return result;
  }

  private List<VirtualFile> collectAllAvailablePomFiles() {
    List<VirtualFile> result = new ArrayList<VirtualFile>(getFileToModuleMapping().keySet());

    VirtualFile pom = myProject.getBaseDir().findChild(MavenConstants.POM_XML);
    if (pom != null) result.add(pom);

    return result;
  }

  public MavenDomDependency addDependency(final MavenProject mavenProject, final MavenId id) {
    final MavenArtifact[] artifact = new MavenArtifact[1];
    try {
      MavenProcess.run(myProject, "Downloading dependency...", new MavenProcess.MavenTask() {
        public void run(MavenProcess process) throws MavenProcessCanceledException {
          artifact[0] = myProjectsTree.downloadArtifact(mavenProject, id, myEmbeddersManager, new SoutMavenConsole(), process);
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
        result.setResult(mavenProject.addDependency(myProject, artifact[0]));
      }
    }.execute().getResultObject();

    return result;
  }

  public List<String> getIgnoredPathMasks() {
    return myState.ignoredPathMasks;
  }

  public void setIgnoredPathMasks(List<String> masks) {
    if (myState.ignoredPathMasks.equals(masks)) {
      return;
    }

    Collection<VirtualFile> oldIgnored = new HashSet<VirtualFile>();
    for (MavenProject each : getProjects()) {
      if (isIgnored(each)) {
        oldIgnored.add(each.getFile());
      }
    }

    myState.ignoredPathMasks.clear();
    myState.ignoredPathMasks.addAll(masks);
    myIgnoredFilesPatternCache = null;

    for (MavenProject each : getProjects()) {
      final boolean newIgnored = isIgnored(each);
      if (newIgnored != oldIgnored.contains(each.getFile())) {
        for (Listener listener : myManagerListeners) {
          listener.setIgnored(each, newIgnored);
        }
      }
    }
  }

  public boolean getIgnoredFlag(MavenProject n) {
    return isIgnoredIndividually(n.getFile().getPath());
  }

  public void setIgnoredFlag(MavenProject project, boolean on) {
    String path = project.getPath();

    if (on == isIgnoredIndividually(path)) {
      return;
    }

    if (on) {
      myState.ignoredFiles.add(path);
    }
    else {
      myState.ignoredFiles.remove(path);
    }

    for (Listener listener : myManagerListeners) {
      listener.setIgnored(project, on);
    }
  }

  public boolean isIgnored(MavenProject project) {
    final String path = project.getPath();
    return isIgnoredIndividually(path) || isIgnoredByMask(path);
  }

  private boolean isIgnoredIndividually(final String path) {
    return myState.ignoredFiles.contains(path);
  }

  private boolean isIgnoredByMask(String path) {
    if (myIgnoredFilesPatternCache == null) {
      myIgnoredFilesPatternCache = Pattern.compile(Strings.translateMasks(myState.ignoredPathMasks));
    }
    return myIgnoredFilesPatternCache.matcher(path).matches();
  }

  public File getLocalRepository() {
    return getGeneralSettings().getEffectiveLocalRepository();
  }

  public void addManagerListener(Listener listener) {
    myManagerListeners.add(listener);
  }

  public void addProjectsTreeListener(MavenProjectsTree.Listener listener) {
    myProjectsTreeDispatcher.addListener(listener);
  }

  public MavenProjectsProcessor.Handler getQuickResolvingProcessorHandler() {
    return myQuickResolvingProcessor.getHandler();
  }

  public MavenProjectsProcessor.Handler getResolvingProcessorHandler() {
    return myResolvingProcessor.getHandler();
  }

  public MavenProjectsProcessor.Handler getFoldersUpdatingProcessorHandler() {
    return myFoldersUpdatingProcessor.getHandler();
  }

  public MavenProjectsProcessor.Handler getPluginDownloadingProcessorHandler() {
    return myPluginDownloadingProcessor.getHandler();
  }

  private void fireActivated() {
    for (Listener each : myManagerListeners) {
      each.activated();
    }
  }

  public interface Listener {
    void activated();

    void setIgnored(MavenProject project, boolean on);
  }
}
