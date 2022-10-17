// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework;

import com.intellij.application.options.CodeStyle;
import com.intellij.compiler.CompilerTestUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.testFramework.CodeStyleSettingsTracker;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.RunAll;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.idea.maven.buildtool.MavenImportSpec;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;
import org.jetbrains.idea.maven.importing.MavenProjectImporter;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.project.importing.*;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.reposearch.DependencySearchService;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;
import org.junit.Assume;

import java.io.File;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.testFramework.PlatformTestUtil.waitForPromise;

public abstract class MavenImportingTestCase extends MavenTestCase {
  protected MavenProjectResolver myProjectResolver;
  protected MavenProjectsManager myProjectsManager;
  private CodeStyleSettingsTracker myCodeStyleSettingsTracker;
  protected boolean isNewImportingProcess;
  protected MavenReadContext myReadContext;
  protected MavenResolvedContext myResolvedContext;
  protected MavenImportedContext myImportedContext;
  protected MavenImportingResult myImportingResult;
  protected MavenSourcesGeneratedContext mySourcesGeneratedContext;
  protected MavenPluginResolvedContext myPluginResolvedContext;

  private final Set<String> FAILED_IN_MASTER =
    ContainerUtil.set("MavenProjectsManagerTest.testUpdatingProjectsWhenMovingModuleFile",
                      "MavenProjectsManagerTest.testUpdatingProjectsWhenAbsentManagedProjectFileAppears",
                      "MavenProjectsManagerTest.testAddingManagedFileAndChangingAggregation",
                      "MavenProjectsManagerWatcherTest.testChangeConfigInOurProjectShouldCallUpdatePomFile",
                      "MavenProjectsManagerWatcherTest.testIncrementalAutoReload");

  @Override
  protected void setUp() throws Exception {
    Assume.assumeFalse(FAILED_IN_MASTER.contains(getClass().getSimpleName() + "." + getName()));

    VfsRootAccess.allowRootAccess(getTestRootDisposable(), PathManager.getConfigPath());

    super.setUp();

    myCodeStyleSettingsTracker = new CodeStyleSettingsTracker(this::getCurrentCodeStyleSettings);

    File settingsFile =
      MavenWorkspaceSettingsComponent.getInstance(myProject).getSettings().generalSettings.getEffectiveGlobalSettingsIoFile();
    if (settingsFile != null) {
      VfsRootAccess.allowRootAccess(getTestRootDisposable(), settingsFile.getAbsolutePath());
    }
    isNewImportingProcess = Registry.is("maven.linear.import");
  }

  @Override
  protected void tearDown() throws Exception {
    RunAll.runAll(
      () -> WriteAction.runAndWait(() -> JavaAwareProjectJdkTableImpl.removeInternalJdkInTests()),
      () -> TestDialogManager.setTestDialog(TestDialog.DEFAULT),
      () -> removeFromLocalRepository("test"),
      () -> CompilerTestUtil.deleteBuildSystemDirectory(myProject),
      () -> {
        myProjectsManager = null;
        myProjectResolver = null;
      },
      () -> super.tearDown(),
      () -> {
        if (myCodeStyleSettingsTracker != null) {
          myCodeStyleSettingsTracker.checkForSettingsDamage();
        }
      }
    );
  }

  @Override
  protected boolean useDirectoryBasedProjectFormat() {
    return true;
  }

  public boolean isWorkspaceImport() {
    return MavenProjectImporter.isImportToWorkspaceModelEnabled(myProject);
  }

  public boolean supportModuleGroups() {
    return !isWorkspaceImport()
           && !MavenProjectImporter.isLegacyImportToTreeStructureEnabled(myProject);
  }

  public boolean supportsKeepingManualChanges() {
    return !isWorkspaceImport();
  }

  public boolean supportsImportOfNonExistingFolders() {
    return isWorkspaceImport();
  }

  public boolean supportsKeepingModulesFromPreviousImport() {
    return !isWorkspaceImport()
           && !MavenProjectImporter.isLegacyImportToTreeStructureEnabled(myProject);
  }

  public boolean supportsLegacyKeepingFoldersFromPreviousImport() {
    return !isWorkspaceImport();
  }

  public boolean supportsKeepingFacetsFromPreviousImport() {
    return !isWorkspaceImport();
  }

  public boolean supportsCreateAggregatorOption() {
    return !isWorkspaceImport()
           && !MavenProjectImporter.isLegacyImportToTreeStructureEnabled(myProject);
  }

  protected void stopMavenImportManager() {
    if (!isNewImportingProcess) return;
    MavenImportingManager manager = MavenImportingManager.getInstance(myProject);
    manager.forceStopImport();
    if (!manager.isImportingInProgress()) return;
    Promise p = MavenImportingManager.getInstance(myProject).getImportFinishPromise();
  }

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    myProjectsManager = MavenProjectsManager.getInstance(myProject);
    removeFromLocalRepository("test");
  }

  protected String mn(String parent, String moduleName) {
    if (MavenProjectImporter.isLegacyImportToTreeStructureEnabled(myProject)) {
      return parent + "." + moduleName;
    }
    return moduleName;
  }

  protected void assertModules(String... expectedNames) {
    Module[] actual = ModuleManager.getInstance(myProject).getModules();
    List<String> actualNames = new ArrayList<>();

    for (Module m : actual) {
      actualNames.add(m.getName());
    }

    assertUnorderedElementsAreEqual(actualNames, expectedNames);
  }

  protected void assertRootProjects(String... expectedNames) {
    List<MavenProject> rootProjects = myProjectsManager.getProjectsTree().getRootProjects();
    List<String> actualNames = ContainerUtil.map(rootProjects, it -> it.getMavenId().getArtifactId());
    assertUnorderedElementsAreEqual(actualNames, expectedNames);
  }

  protected void assertContentRoots(String moduleName, String... expectedRoots) {
    List<String> actual = new ArrayList<>();
    for (ContentEntry e : getContentRoots(moduleName)) {
      actual.add(e.getUrl());
    }
    assertUnorderedPathsAreEqual(actual, ContainerUtil.map(expectedRoots, root -> VfsUtilCore.pathToUrl(root)));
  }

  protected void assertGeneratedSources(String moduleName, String... expectedSources) {
    ContentEntry contentRoot = getContentRoot(moduleName);
    List<ContentFolder> folders = new ArrayList<>();
    for (SourceFolder folder : contentRoot.getSourceFolders(JavaSourceRootType.SOURCE)) {
      JavaSourceRootProperties properties = folder.getJpsElement().getProperties(JavaSourceRootType.SOURCE);
      assertNotNull(properties);
      if (properties.isForGeneratedSources()) {
        folders.add(folder);
      }
    }
    doAssertContentFolders(contentRoot, folders, expectedSources);
  }

  protected void assertSources(String moduleName, String... expectedSources) {
    doAssertContentFolders(moduleName, JavaSourceRootType.SOURCE, expectedSources);
  }

  protected void assertContentRootSources(String moduleName, String contentRoot, String... expectedSources) {
    ContentEntry root = getContentRoot(moduleName, contentRoot);
    doAssertContentFolders(root, root.getSourceFolders(JavaSourceRootType.SOURCE), expectedSources);
  }

  protected void assertResources(String moduleName, String... expectedSources) {
    doAssertContentFolders(moduleName, JavaResourceRootType.RESOURCE, expectedSources);
  }

  protected void assertContentRootResources(String moduleName, String contentRoot, String... expectedSources) {
    ContentEntry root = getContentRoot(moduleName, contentRoot);
    doAssertContentFolders(root, root.getSourceFolders(JavaResourceRootType.RESOURCE), expectedSources);
  }

  protected void assertTestSources(String moduleName, String... expectedSources) {
    doAssertContentFolders(moduleName, JavaSourceRootType.TEST_SOURCE, expectedSources);
  }

  protected void assertContentRootTestSources(String moduleName, String contentRoot, String... expectedSources) {
    ContentEntry root = getContentRoot(moduleName, contentRoot);
    doAssertContentFolders(root, root.getSourceFolders(JavaSourceRootType.TEST_SOURCE), expectedSources);
  }

  protected void assertTestResources(String moduleName, String... expectedSources) {
    doAssertContentFolders(moduleName, JavaResourceRootType.TEST_RESOURCE, expectedSources);
  }

  protected void assertContentRootTestResources(String moduleName, String contentRoot, String... expectedSources) {
    ContentEntry root = getContentRoot(moduleName, contentRoot);
    doAssertContentFolders(root, root.getSourceFolders(JavaResourceRootType.TEST_RESOURCE), expectedSources);
  }

  protected void assertExcludes(String moduleName, String... expectedExcludes) {
    ContentEntry contentRoot = getContentRoot(moduleName);
    doAssertContentFolders(contentRoot, Arrays.asList(contentRoot.getExcludeFolders()), expectedExcludes);
  }

  protected void assertContentRootExcludes(String moduleName, String contentRoot, String... expectedExcudes) {
    ContentEntry root = getContentRoot(moduleName, contentRoot);
    doAssertContentFolders(root, Arrays.asList(root.getExcludeFolders()), expectedExcudes);
  }

  protected void doAssertContentFolders(String moduleName, @NotNull JpsModuleSourceRootType<?> rootType, String... expected) {
    ContentEntry contentRoot = getContentRoot(moduleName);
    doAssertContentFolders(contentRoot, contentRoot.getSourceFolders(rootType), expected);
  }

  protected MavenProjectsTree getProjectsTree() {
    return myProjectsManager.getProjectsTree();
  }

  private static void doAssertContentFolders(ContentEntry e,
                                             final List<? extends ContentFolder> folders,
                                             String... expected) {
    List<String> actual = new ArrayList<>();
    for (ContentFolder f : folders) {
      String rootUrl = e.getUrl();
      String folderUrl = f.getUrl();

      if (folderUrl.startsWith(rootUrl)) {
        int length = rootUrl.length() + 1;
        folderUrl = folderUrl.substring(Math.min(length, folderUrl.length()));
      }

      actual.add(folderUrl);
    }

    assertSameElements("Unexpected list of folders in content root " + e.getUrl(),
                       actual, Arrays.asList(expected));
  }

  protected void assertModuleOutput(String moduleName, String output, String testOutput) {
    CompilerModuleExtension e = getCompilerExtension(moduleName);

    assertFalse(e.isCompilerOutputPathInherited());
    assertEquals(output, getAbsolutePath(e.getCompilerOutputUrl()));
    assertEquals(testOutput, getAbsolutePath(e.getCompilerOutputUrlForTests()));
  }

  private static @NotNull String getAbsolutePath(@Nullable String path) {
    return path == null ? "" : FileUtil.toSystemIndependentName(FileUtil.toCanonicalPath(VirtualFileManager.extractPath(path)));
  }

  protected void assertProjectOutput(String module) {
    assertTrue(getCompilerExtension(module).isCompilerOutputPathInherited());
  }

  protected CompilerModuleExtension getCompilerExtension(String module) {
    ModuleRootManager m = getRootManager(module);
    return CompilerModuleExtension.getInstance(m.getModule());
  }

  protected void assertModuleLibDep(String moduleName, String depName) {
    assertModuleLibDep(moduleName, depName, null);
  }

  protected void assertModuleLibDep(String moduleName, String depName, String classesPath) {
    assertModuleLibDep(moduleName, depName, classesPath, null, null);
  }

  protected void assertModuleLibDep(String moduleName, String depName, String classesPath, String sourcePath, String javadocPath) {
    LibraryOrderEntry lib = getModuleLibDep(moduleName, depName);

    assertModuleLibDepPath(lib, OrderRootType.CLASSES, classesPath == null ? null : Collections.singletonList(classesPath));
    assertModuleLibDepPath(lib, OrderRootType.SOURCES, sourcePath == null ? null : Collections.singletonList(sourcePath));
    assertModuleLibDepPath(lib, JavadocOrderRootType.getInstance(), javadocPath == null ? null : Collections.singletonList(javadocPath));
  }

  protected void assertModuleLibDep(String moduleName,
                                    String depName,
                                    List<String> classesPaths,
                                    List<String> sourcePaths,
                                    List<String> javadocPaths) {
    LibraryOrderEntry lib = getModuleLibDep(moduleName, depName);

    assertModuleLibDepPath(lib, OrderRootType.CLASSES, classesPaths);
    assertModuleLibDepPath(lib, OrderRootType.SOURCES, sourcePaths);
    assertModuleLibDepPath(lib, JavadocOrderRootType.getInstance(), javadocPaths);
  }

  private static void assertModuleLibDepPath(LibraryOrderEntry lib, OrderRootType type, List<String> paths) {
    if (paths == null) return;
    assertUnorderedPathsAreEqual(Arrays.asList(lib.getRootUrls(type)), paths);
    // also check the library because it may contain slight different set of urls (e.g. with duplicates)
    assertUnorderedPathsAreEqual(Arrays.asList(lib.getLibrary().getUrls(type)), paths);
  }

  protected void assertModuleLibDepScope(String moduleName, String depName, DependencyScope scope) {
    LibraryOrderEntry dep = getModuleLibDep(moduleName, depName);
    assertEquals(scope, dep.getScope());
  }

  private LibraryOrderEntry getModuleLibDep(String moduleName, String depName) {
    return getModuleDep(moduleName, depName, LibraryOrderEntry.class);
  }

  protected void assertModuleLibDeps(String moduleName, String... expectedDeps) {
    assertModuleDeps(moduleName, LibraryOrderEntry.class, expectedDeps);
  }

  protected void assertExportedDeps(String moduleName, String... expectedDeps) {
    final List<String> actual = new ArrayList<>();

    getRootManager(moduleName).orderEntries().withoutSdk().withoutModuleSourceEntries().exportedOnly().process(new RootPolicy<>() {
      @Override
      public Object visitModuleOrderEntry(@NotNull ModuleOrderEntry e, Object value) {
        actual.add(e.getModuleName());
        return null;
      }

      @Override
      public Object visitLibraryOrderEntry(@NotNull LibraryOrderEntry e, Object value) {
        actual.add(e.getLibraryName());
        return null;
      }
    }, null);

    assertOrderedElementsAreEqual(actual, expectedDeps);
  }

  protected void assertModuleModuleDeps(String moduleName, String... expectedDeps) {
    assertModuleDeps(moduleName, ModuleOrderEntry.class, expectedDeps);
  }

  private void assertModuleDeps(String moduleName, Class clazz, String... expectedDeps) {
    assertOrderedElementsAreEqual(collectModuleDepsNames(moduleName, clazz), expectedDeps);
  }

  protected void assertModuleModuleDepScope(String moduleName, String depName, DependencyScope scope) {
    ModuleOrderEntry dep = getModuleModuleDep(moduleName, depName);
    assertEquals(scope, dep.getScope());
  }

  private ModuleOrderEntry getModuleModuleDep(String moduleName, String depName) {
    return getModuleDep(moduleName, depName, ModuleOrderEntry.class);
  }

  private List<String> collectModuleDepsNames(String moduleName, Class clazz) {
    List<String> actual = new ArrayList<>();

    for (OrderEntry e : getRootManager(moduleName).getOrderEntries()) {
      if (clazz.isInstance(e)) {
        actual.add(e.getPresentableName());
      }
    }
    return actual;
  }

  private <T> T getModuleDep(String moduleName, String depName, Class<T> clazz) {
    T dep = null;

    for (OrderEntry e : getRootManager(moduleName).getOrderEntries()) {
      if (clazz.isInstance(e) && e.getPresentableName().equals(depName)) {
        dep = (T)e;
      }
    }
    assertNotNull("Dependency not found: " + depName
                  + "\namong: " + collectModuleDepsNames(moduleName, clazz),
                  dep);
    return dep;
  }

  public void assertProjectLibraries(String... expectedNames) {
    List<String> actualNames = new ArrayList<>();
    for (Library each : LibraryTablesRegistrar.getInstance().getLibraryTable(myProject).getLibraries()) {
      String name = each.getName();
      actualNames.add(name == null ? "<unnamed>" : name);
    }
    assertUnorderedElementsAreEqual(actualNames, expectedNames);
  }

  protected void assertModuleGroupPath(String moduleName, String... expected) {
    assertModuleGroupPath(moduleName, false, expected);
  }

  protected void assertModuleGroupPath(String moduleName, boolean groupWasManuallyAdded, String... expected) {
    boolean moduleGroupsSupported = supportModuleGroups() || groupWasManuallyAdded && supportsKeepingManualChanges();

    String[] path = ModuleManager.getInstance(myProject).getModuleGroupPath(getModule(moduleName));

    if (!moduleGroupsSupported || expected.length == 0) {
      assertNull(path);
    }
    else {
      assertNotNull(path);
      assertOrderedElementsAreEqual(Arrays.asList(path), expected);
    }
  }

  protected Module getModule(final String name) {
    Module m = ReadAction.compute(() -> ModuleManager.getInstance(myProject).findModuleByName(name));
    assertNotNull("Module " + name + " not found", m);
    return m;
  }

  protected void assertMavenizedModule(final String name) {
    assertTrue(MavenProjectsManager.getInstance(myProject).isMavenizedModule(getModule(name)));
  }

  protected void assertNotMavenizedModule(final String name) {
    assertFalse(MavenProjectsManager.getInstance(myProject).isMavenizedModule(getModule(name)));
  }

  private ContentEntry getContentRoot(String moduleName) {
    ContentEntry[] ee = getContentRoots(moduleName);
    List<String> roots = new ArrayList<>();
    for (ContentEntry e : ee) {
      roots.add(e.getUrl());
    }

    String message = "Several content roots found: [" + StringUtil.join(roots, ", ") + "]";
    assertEquals(message, 1, ee.length);

    return ee[0];
  }

  private ContentEntry getContentRoot(String moduleName, String path) {
    ContentEntry[] roots = getContentRoots(moduleName);
    for (ContentEntry e : roots) {
      if (e.getUrl().equals(VfsUtilCore.pathToUrl(path))) return e;
    }
    throw new AssertionError("content root not found in module " + moduleName + ":" +
                             "\nExpected root: " + path +
                             "\nExisting roots:" +
                             "\n" + StringUtil.join(roots, it -> " * " + it.getUrl(), "\n"));
  }

  public ContentEntry[] getContentRoots(String moduleName) {
    return getRootManager(moduleName).getContentEntries();
  }

  public ModuleRootManager getRootManager(String module) {
    return ModuleRootManager.getInstance(getModule(module));
  }

  protected void importProject(@NotNull @Language(value = "XML", prefix = "<project>", suffix = "</project>") String xml) {
    createProjectPom(xml);
    importProject();
  }

  protected void importProject() {
    importProjectWithProfiles();
  }

  protected void importProjectWithErrors() {
    doImportProjects(Collections.singletonList(myProjectPom), false);
  }

  protected void importProjectWithProfiles(String... profiles) {
    doImportProjects(Collections.singletonList(myProjectPom), true, profiles);
  }

  protected void importProject(VirtualFile file) {
    importProjects(file);
  }

  protected void importProjects(VirtualFile... files) {
    doImportProjects(Arrays.asList(files), true);
  }

  protected void importProjectsWithErrors(VirtualFile... files) {
    doImportProjects(Arrays.asList(files), false);
  }

  protected void setIgnoredFilesPathForNextImport(@NotNull List<String> paths) {
    myProjectsManager.setIgnoredFilesPaths(paths);
  }

  protected void setIgnoredPathPatternsForNextImport(@NotNull List<String> patterns) {
    myProjectsManager.setIgnoredFilesPatterns(patterns);
  }

  protected void doImportProjects(final List<VirtualFile> files, boolean failOnReadingError, String... profiles) {
    if (isNewImportingProcess) {
      importViaNewFlow(files, failOnReadingError, Collections.emptyList(), profiles);
    }
    else {
      doImportProjectsLegacyWay(files, failOnReadingError, Collections.emptyList(), profiles);
    }
  }


  protected final void importViaNewFlow(final List<VirtualFile> files, boolean failOnReadingError,
                                        List<String> disabledProfiles, String... profiles) {

    myProjectsManager.initForTests();
    myProjectsManager.getProjectsTree().setExplicitProfiles(new MavenExplicitProfiles(Arrays.asList(profiles), disabledProfiles));
    MavenImportingManager importingManager = MavenImportingManager.getInstance(myProject);
    myImportingResult = importingManager.openProjectAndImport(
      new FilesList(files),
      getMavenImporterSettings(),
      getMavenGeneralSettings(),
      MavenImportSpec.EXPLICIT_IMPORT
    );
    Promise<MavenImportFinishedContext> promise = myImportingResult.getFinishPromise().onSuccess(p -> {
      Throwable t = p.getError();
      if (t != null) {
        if (t instanceof RuntimeException) throw (RuntimeException)t;
        throw new RuntimeException(t);
      }
      myImportedContext = p.getContext();
      myReadContext = myImportedContext.getReadContext();
      myResolvedContext = myImportedContext.getResolvedContext();
    });

    try {
      PlatformTestUtil.waitForPromise(promise);
    }
    catch (AssertionError e) {
      if (promise.getState() == Promise.State.PENDING) {
        fail("Current state is: " + importingManager.getCurrentContext());
      }
      else {
        throw new RuntimeException(e);
      }
    }
    


    /*Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() -> { // we have not use EDT for import in tests

      MavenImportFlow flow = new MavenImportFlow();
      MavenInitialImportContext initialImportContext =
        flow.prepareNewImport(myProject, getMavenProgressIndicator(),
                              new FilesList(files),
                              getMavenGeneralSettings(),
                              getMavenImporterSettings(),
                              Arrays.asList(profiles),
                              disabledProfiles);
      myProjectsManager.initForTests();
      myReadContext = flow.readMavenFiles(initialImportContext, myIgnorePaths, myIgnorePatterns);
      if (failOnReadingError) {
        assertFalse("Failed to import Maven project: " + myReadContext.collectProblems(), myReadContext.hasReadingProblems());
      }

      myResolvedContext = flow.resolveDependencies(myReadContext);
      mySourcesGeneratedContext = flow.resolveFolders(myResolvedContext);
      flow.resolvePlugins(myResolvedContext);
      myImportedContext = flow.commitToWorkspaceModel(myResolvedContext);
      myProjectsTree = myReadContext.getProjectsTree();
      flow.updateProjectManager(myReadContext);
      flow.configureMavenProject(myImportedContext);
    });
    PlatformTestUtil.waitForFuture(future, 60_000);*/
  }

  protected void doImportProjectsLegacyWay(final List<VirtualFile> files, boolean failOnReadingError,
                                  List<String> disabledProfiles, String... profiles) {
    assertFalse(ApplicationManager.getApplication().isWriteAccessAllowed());
    initProjectsManager(false);

    readProjects(files, disabledProfiles, profiles);

    ApplicationManager.getApplication().invokeAndWait(() -> {
      myProjectsManager.scheduleImportInTests(files);
      myProjectsManager.importProjects();
    });

    Promise<?> promise = myProjectsManager.waitForImportCompletion();
    PlatformTestUtil.waitForPromise(promise);


    if (failOnReadingError) {
      for (MavenProject each : myProjectsManager.getProjectsTree().getProjects()) {
        assertFalse("Failed to import Maven project: " + each.getProblems(), each.hasReadingProblems());
      }
    }
  }

  protected void waitForImportCompletion() {
    edt(() -> waitForPromise(myProjectsManager.waitForImportCompletion(), 60_000));
  }

  protected void readProjects(List<VirtualFile> files, String... profiles) {
    readProjects(files, Collections.emptyList(), profiles);
  }

  protected void readProjects(List<VirtualFile> files, List<String> disabledProfiles, String... profiles) {
    if (isNewImportingProcess) {
      MavenImportFlow flow = new MavenImportFlow();
      MavenInitialImportContext initialImportContext =
        flow.prepareNewImport(myProject,
                              new FilesList(files),
                              getMavenGeneralSettings(),
                              getMavenImporterSettings(),
                              Arrays.asList(profiles),
                              disabledProfiles);
      myProjectsManager.initForTests();
      myReadContext = flow.readMavenFiles(initialImportContext, getMavenProgressIndicator());
      flow.updateProjectManager(myReadContext);
    }
    else {
      myProjectsManager.resetManagedFilesAndProfilesInTests(files, new MavenExplicitProfiles(Arrays.asList(profiles), disabledProfiles));
      waitForImportCompletion();
    }
  }

  protected void updateProjectsAndImport(VirtualFile... files) {
    if (isNewImportingProcess) {
      importViaNewFlow(Arrays.asList(files), true, Collections.emptyList());
    }
    else {
      readProjects(files);
      myProjectsManager.performScheduledImportInTests();
    }
  }

  protected void initProjectsManager(boolean enableEventHandling) {
    myProjectsManager.initForTests();
    myProjectResolver = new MavenProjectResolver(myProjectsManager.getProjectsTree());
    if (enableEventHandling) {
      myProjectsManager.enableAutoImportInTests();
    }
  }

  protected void scheduleResolveAll() {
    myProjectsManager.scheduleResolveAllInTests();
  }

  protected void waitForReadingCompletion() {
    if (isNewImportingProcess) {
      return;
    }
    ApplicationManager.getApplication().invokeAndWait(() -> {
      try {
        myProjectsManager.waitForReadingCompletion();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }

  protected void readProjects() {
    readProjects(myProjectsManager.getProjectsFiles());
  }

  protected void readProjects(VirtualFile... files) {
    List<MavenProject> projects = new ArrayList<>();
    for (VirtualFile each : files) {
      projects.add(myProjectsManager.findProject(each));
    }
    myProjectsManager.forceUpdateProjects(projects);
    waitForReadingCompletion();
  }

  protected void resolveDependenciesAndImport() {
    if (isNewImportingProcess) {
      importProject();
      return;
    }

    ApplicationManager.getApplication().invokeAndWait(() -> {
      myProjectsManager.waitForResolvingCompletion();
      myProjectsManager.performScheduledImportInTests();
    });
  }

  protected void resolveFoldersAndImport() {

    myProjectsManager.scheduleFoldersResolveForAllProjects();
    myProjectsManager.waitForFoldersResolvingCompletion();
    if (isNewImportingProcess) {
      importProject();
    }
    else {
      ApplicationManager.getApplication().invokeAndWait(() -> myProjectsManager.performScheduledImportInTests());
    }
  }

  protected void resolvePlugins() {
    if (isNewImportingProcess) {
      assertNotNull(myResolvedContext);
      AsyncPromise<MavenPluginResolvedContext> promise = new AsyncPromise<>();
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        try {
          promise.setResult(new MavenImportFlow().resolvePlugins(myResolvedContext));
        }
        catch (Exception e) {
          promise.setError(e);
        }
      });
      PlatformTestUtil.waitForPromise(promise);
      myPluginResolvedContext = promise.get();
      return;
    }

    myProjectsManager.waitForPluginsResolvingCompletion();
  }

  protected void downloadArtifacts() {
    downloadArtifacts(myProjectsManager.getProjects(), null);
  }

  protected MavenArtifactDownloader.DownloadResult downloadArtifacts(Collection<MavenProject> projects,
                                                                     List<MavenArtifact> artifacts) {
    if (isNewImportingProcess) {
      return downloadArtifactAndWaitForResult(projects, artifacts);
    }
    final MavenArtifactDownloader.DownloadResult[] unresolved = new MavenArtifactDownloader.DownloadResult[1];

    AsyncPromise<MavenArtifactDownloader.DownloadResult> result = new AsyncPromise<>();
    result.onSuccess(unresolvedArtifacts -> unresolved[0] = unresolvedArtifacts);

    myProjectsManager.scheduleArtifactsDownloading(projects, artifacts, true, true, result);
    myProjectsManager.waitForArtifactsDownloadingCompletion();
    return unresolved[0];
  }

  @NotNull
  private MavenArtifactDownloader.DownloadResult downloadArtifactAndWaitForResult(Collection<MavenProject> projects,
                                                                                  List<MavenArtifact> artifacts) {
    AsyncPromise<MavenArtifactDownloader.DownloadResult> promise = new AsyncPromise<>();
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        promise.setResult(new MavenImportFlow().downloadSpecificArtifacts(myProject, projects, artifacts, true, true,
                                                                          new MavenProgressIndicator(myProject, null)));
      }
      catch (Throwable e) {
        promise.setError(e);
      }
    });
    PlatformTestUtil.waitForPromise(promise);
    return promise.get();
  }

  protected void performPostImportTasks() {
    /*if (isNewImportingProcess) {
      assertNotNull(myImportedContext);
      new MavenImportFlow().runPostImportTasks(myImportedContext);
      return;
    }*/
    myProjectsManager.waitForPostImportTasksCompletion();
  }

  protected void executeGoal(String relativePath, String goal) throws Exception {
    VirtualFile dir = myProjectRoot.findFileByRelativePath(relativePath);

    MavenRunnerParameters rp =
      new MavenRunnerParameters(true, dir.getPath(), (String)null, Collections.singletonList(goal), Collections.emptyList());
    MavenRunnerSettings rs = new MavenRunnerSettings();
    Semaphore wait = new Semaphore(1);
    wait.acquire();
    MavenRunner.getInstance(myProject).run(rp, rs, () -> {
      wait.release();
    });
    boolean tryAcquire = wait.tryAcquire(10, TimeUnit.SECONDS);
    assertTrue("Maven execution failed", tryAcquire);
  }

  protected void removeFromLocalRepository(String relativePath) {
    if (SystemInfo.isWindows) {
      MavenServerManager.getInstance().shutdown(true);
    }
    FileUtil.delete(new File(getRepositoryPath(), relativePath));
  }

  protected void setupJdkForModules(String... moduleNames) {
    for (String each : moduleNames) {
      setupJdkForModule(each);
    }
  }

  protected Sdk setupJdkForModule(final String moduleName) {
    final Sdk sdk = JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
    ModuleRootModificationUtil.setModuleSdk(getModule(moduleName), sdk);
    return sdk;
  }

  protected static Sdk createJdk() {
    return IdeaTestUtil.getMockJdk17();
  }

  protected static AtomicInteger configConfirmationForYesAnswer() {
    final AtomicInteger counter = new AtomicInteger();
    TestDialogManager.setTestDialog(message -> {
      counter.getAndIncrement();
      return Messages.YES;
    });
    return counter;
  }

  protected static AtomicInteger configConfirmationForNoAnswer() {
    final AtomicInteger counter = new AtomicInteger();
    TestDialogManager.setTestDialog(message -> {
      counter.getAndIncrement();
      return Messages.NO;
    });
    return counter;
  }

  private CodeStyleSettings getCurrentCodeStyleSettings() {
    if (CodeStyleSchemes.getInstance().getCurrentScheme() == null) return CodeStyle.createTestSettings();
    return CodeStyle.getSettings(myProject);
  }
}
