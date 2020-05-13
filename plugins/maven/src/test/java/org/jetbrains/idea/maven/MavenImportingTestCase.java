// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.externalSystem.test.ExternalSystemTestCase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.RunAll;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.idea.maven.execution.*;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class MavenImportingTestCase extends MavenTestCase {
  protected MavenProjectsTree myProjectsTree;
  protected MavenProjectResolver myProjectResolver;
  protected MavenProjectsManager myProjectsManager;

  @Override
  protected void setUp() throws Exception {
    VfsRootAccess.allowRootAccess(getTestRootDisposable(), PathManager.getConfigPath());

    super.setUp();

    File settingsFile = MavenWorkspaceSettingsComponent.getInstance(myProject).getSettings().generalSettings.getEffectiveGlobalSettingsIoFile();
    if (settingsFile != null) {
      VfsRootAccess.allowRootAccess(getTestRootDisposable(), settingsFile.getAbsolutePath());
    }
  }

  @Override
  protected void tearDown() throws Exception {
    new RunAll(
      () -> WriteAction.runAndWait(() -> JavaAwareProjectJdkTableImpl.removeInternalJdkInTests()),
      () -> Messages.setTestDialog(TestDialog.DEFAULT),
      () -> removeFromLocalRepository("test"),
      () -> ExternalSystemTestCase.deleteBuildSystemDirectory(),
      () -> myProjectsManager = null,
      () -> myProjectsTree = null,
      () -> myProjectResolver = null,
      () -> super.tearDown()
    ).run();
  }

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    myProjectsManager = MavenProjectsManager.getInstance(myProject);
    removeFromLocalRepository("test");
  }

  protected void assertModules(String... expectedNames) {
    Module[] actual = ModuleManager.getInstance(myProject).getModules();
    List<String> actualNames = new ArrayList<>();

    for (Module m : actual) {
      actualNames.add(m.getName());
    }

    assertUnorderedElementsAreEqual(actualNames, expectedNames);
  }

  protected void assertContentRoots(String moduleName, String... expectedRoots) {
    List<String> actual = new ArrayList<>();
    for (ContentEntry e : getContentRoots(moduleName)) {
      actual.add(e.getUrl());
    }

    for (int i = 0; i < expectedRoots.length; i++) {
      expectedRoots[i] = VfsUtilCore.pathToUrl(expectedRoots[i]);
    }

    assertUnorderedPathsAreEqual(actual, Arrays.asList(expectedRoots));
  }

  protected void assertSources(String moduleName, String... expectedSources) {
    doAssertContentFolders(moduleName, JavaSourceRootType.SOURCE, expectedSources);
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

  protected void assertResources(String moduleName, String... expectedSources) {
    doAssertContentFolders(moduleName, JavaResourceRootType.RESOURCE, expectedSources);
  }

  protected void assertTestSources(String moduleName, String... expectedSources) {
    doAssertContentFolders(moduleName, JavaSourceRootType.TEST_SOURCE, expectedSources);
  }

  protected void assertTestResources(String moduleName, String... expectedSources) {
    doAssertContentFolders(moduleName, JavaResourceRootType.TEST_RESOURCE, expectedSources);
  }

  protected void assertExcludes(String moduleName, String... expectedExcludes) {
    ContentEntry contentRoot = getContentRoot(moduleName);
    doAssertContentFolders(contentRoot, Arrays.asList(contentRoot.getExcludeFolders()), expectedExcludes);
  }

  protected void assertContentRootExcludes(String moduleName, String contentRoot, String... expectedExcudes) {
    ContentEntry root = getContentRoot(moduleName, contentRoot);
    doAssertContentFolders(root, Arrays.asList(root.getExcludeFolders()), expectedExcudes);
  }

  private void doAssertContentFolders(String moduleName, @NotNull JpsModuleSourceRootType<?> rootType, String... expected) {
    ContentEntry contentRoot = getContentRoot(moduleName);
    doAssertContentFolders(contentRoot, contentRoot.getSourceFolders(rootType), expected);
  }

  private static void doAssertContentFolders(ContentEntry e, final List<? extends ContentFolder> folders, String... expected) {
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

    assertOrderedElementsAreEqual(actual, Arrays.asList(expected));
  }

  protected void assertModuleOutput(String moduleName, String output, String testOutput) {
    CompilerModuleExtension e = getCompilerExtension(moduleName);

    assertFalse(e.isCompilerOutputPathInherited());
    assertEquals(output, getAbsolutePath(e.getCompilerOutputUrl()));
    assertEquals(testOutput, getAbsolutePath(e.getCompilerOutputUrlForTests()));
  }

  private static String getAbsolutePath(String path) {
    path = VfsUtil.urlToPath(path);
    path = PathUtil.getCanonicalPath(path);
    return FileUtil.toSystemIndependentName(path);
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

    getRootManager(moduleName).orderEntries().withoutSdk().withoutModuleSourceEntries().exportedOnly().process(new RootPolicy<Object>() {
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
    String[] path = ModuleManager.getInstance(myProject).getModuleGroupPath(getModule(moduleName));

    if (expected.length == 0) {
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
    for (ContentEntry e : getContentRoots(moduleName)) {
      if (e.getUrl().equals(VfsUtilCore.pathToUrl(path))) return e;
    }
    throw new AssertionError("content root not found");
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

  protected void doImportProjects(final List<VirtualFile> files, boolean failOnReadingError, String... profiles) {
    initProjectsManager(false);

    readProjects(files, profiles);

    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      myProjectsManager.waitForResolvingCompletion();
      myProjectsManager.scheduleImportInTests(files);
      myProjectsManager.importProjects();
    });

    if (failOnReadingError) {
      for (MavenProject each : myProjectsTree.getProjects()) {
        assertFalse("Failed to import Maven project: " + each.getProblems(), each.hasReadingProblems());
      }
    }
  }

  protected void readProjects(List<VirtualFile> files, String... profiles) {
    myProjectsManager.resetManagedFilesAndProfilesInTests(files, new MavenExplicitProfiles(Arrays.asList(profiles)));
    waitForReadingCompletion();
  }

  protected void updateProjectsAndImport(VirtualFile... files) {
    readProjects(files);
    myProjectsManager.performScheduledImportInTests();
  }

  protected void initProjectsManager(boolean enableEventHandling) {
    myProjectsManager.initForTests();
    myProjectsTree = myProjectsManager.getProjectsTreeForTests();
    myProjectResolver = new MavenProjectResolver(myProjectsTree);
    if (enableEventHandling) myProjectsManager.enableAutoImportInTests();
  }

  protected void scheduleResolveAll() {
    myProjectsManager.scheduleResolveAllInTests();
  }

  protected void waitForReadingCompletion() {
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
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
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      myProjectsManager.waitForResolvingCompletion();
      myProjectsManager.performScheduledImportInTests();
    });
  }

  protected void resolveFoldersAndImport() {
    myProjectsManager.scheduleFoldersResolveForAllProjects();
    myProjectsManager.waitForFoldersResolvingCompletion();
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> myProjectsManager.performScheduledImportInTests());
  }

  protected void resolvePlugins() {
    myProjectsManager.waitForPluginsResolvingCompletion();
  }

  protected void downloadArtifacts() {
    downloadArtifacts(myProjectsManager.getProjects(), null);
  }

  protected MavenArtifactDownloader.DownloadResult downloadArtifacts(Collection<MavenProject> projects,
                                                                     List<MavenArtifact> artifacts) {
    final MavenArtifactDownloader.DownloadResult[] unresolved = new MavenArtifactDownloader.DownloadResult[1];

    AsyncPromise<MavenArtifactDownloader.DownloadResult> result = new AsyncPromise<>();
    result.onSuccess(unresolvedArtifacts -> unresolved[0] = unresolvedArtifacts);

    myProjectsManager.scheduleArtifactsDownloading(projects, artifacts, true, true, result);
    myProjectsManager.waitForArtifactsDownloadingCompletion();

    return unresolved[0];
  }

  protected void performPostImportTasks() {
    myProjectsManager.waitForPostImportTasksCompletion();
  }

  protected void executeGoal(String relativePath, String goal) {
    VirtualFile dir = myProjectRoot.findFileByRelativePath(relativePath);

    MavenRunnerParameters rp = new MavenRunnerParameters(true, dir.getPath(), (String)null, Arrays.asList(goal), Collections.emptyList());
    MavenRunnerSettings rs = new MavenRunnerSettings();
    MavenExecutor e = new MavenExternalExecutor(myProject, rp, getMavenGeneralSettings(), rs, new SoutMavenConsole());

    e.execute(new EmptyProgressIndicator());
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
    Messages.setTestDialog(message -> {
      counter.getAndIncrement();
      return Messages.YES;
    });
    return counter;
  }

  protected static AtomicInteger configConfirmationForNoAnswer() {
    final AtomicInteger counter = new AtomicInteger();
    Messages.setTestDialog(message -> {
      counter.getAndIncrement();
      return Messages.NO;
    });
    return counter;
  }
}
