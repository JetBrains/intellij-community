/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package org.jetbrains.idea.maven;

import com.intellij.compiler.CompilerManagerImpl;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.compiler.impl.ModuleCompileScope;
import com.intellij.compiler.impl.TranslatingCompilerFilesMonitor;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import org.jetbrains.idea.maven.project.MavenException;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.runner.MavenExecutor;
import org.jetbrains.idea.maven.runner.MavenExternalExecutor;
import org.jetbrains.idea.maven.runner.MavenRunnerParameters;
import org.jetbrains.idea.maven.runner.MavenRunnerSettings;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class MavenImportingTestCase extends MavenTestCase {
  protected MavenProjectsTree myProjectsTree;
  protected MavenProjectsManager myProjectsManager;
  private List<String> myProfilesList;

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    myProjectsManager = MavenProjectsManager.getInstance(myProject);
    removeFromLocalRepository("test");
  }

  @Override
  protected void tearDown() throws Exception {
    Messages.setTestDialog(TestDialog.DEFAULT);
    myProjectsManager.projectClosed();
    removeFromLocalRepository("test");
    super.tearDown();
  }

  protected void assertModules(String... expectedNames) {
    Module[] actual = ModuleManager.getInstance(myProject).getModules();
    List<String> actualNames = new ArrayList<String>();

    for (Module m : actual) {
      actualNames.add(m.getName());
    }

    assertUnorderedElementsAreEqual(actualNames, expectedNames);
  }

  protected void assertContentRoots(String moduleName, String... expectedRoots) {
    List<String> actual = new ArrayList<String>();
    for (ContentEntry e : getContentRoots(moduleName)) {
      actual.add(e.getUrl());
    }

    for (int i = 0; i < expectedRoots.length; i++) {
      expectedRoots[i] = VfsUtil.pathToUrl(expectedRoots[i]);
    }

    assertUnorderedElementsAreEqual(actual, expectedRoots);
  }

  protected void assertSources(String moduleName, String... expectedSources) {
    doAssertContentFolders(moduleName, true, false, expectedSources);
  }

  protected void assertTestSources(String moduleName, String... expectedSources) {
    doAssertContentFolders(moduleName, true, true, expectedSources);
  }

  protected void assertExcludes(String moduleName, String... expectedExcludes) {
    doAssertContentFolders(moduleName, false, false, expectedExcludes);
  }

  protected void assertContentRootExcludes(String moduleName, String contentRoot, String... expectedExcudes) {
    doAssertContentFolders(getContentRoot(moduleName, contentRoot), false, false, expectedExcudes);
  }

  private void doAssertContentFolders(String moduleName, boolean isSource, boolean isTest, String... expected) {
    doAssertContentFolders(getContentRoot(moduleName), isSource, isTest, expected);
  }

  private void doAssertContentFolders(ContentEntry e, boolean isSource, boolean isTest, String... expected) {
    List<String> actual = new ArrayList<String>();
    for (ContentFolder f : isSource ? e.getSourceFolders() : e.getExcludeFolders()) {
      if (isSource && (isTest != ((SourceFolder)f).isTestSource())) continue;

      String rootUrl = e.getUrl();
      String folderUrl = f.getUrl();

      if (folderUrl.startsWith(rootUrl)) {
        int lenght = rootUrl.length() + 1;
        folderUrl = folderUrl.substring(Math.min(lenght, folderUrl.length()));
      }

      actual.add(folderUrl);
    }

    assertUnorderedElementsAreEqual(actual, expected);
  }

  protected void assertModuleOutput(String moduleName, String output, String testOutput) {
    CompilerModuleExtension e = getCompilerExtension(moduleName);

    assertFalse(e.isCompilerOutputPathInherited());
    assertEquals(output, getAbsolutePath(e.getCompilerOutputUrl()));
    assertEquals(testOutput, getAbsolutePath(e.getCompilerOutputUrlForTests()));
  }

  private String getAbsolutePath(String path) {
    path = VfsUtil.urlToPath(path);
    path = PathUtil.getCanonicalPath(path);
    return FileUtil.toSystemIndependentName(path);
  }

  protected void assertProjectOutput(String module) {
    assertTrue(getCompilerExtension(module).isCompilerOutputPathInherited());
  }

  private CompilerModuleExtension getCompilerExtension(String module) {
    ModuleRootManager m = getRootManager(module);
    return CompilerModuleExtension.getInstance(m.getModule());
  }

  protected void assertModuleLibDep(String moduleName, String depName) {
    assertModuleLibDep(moduleName, depName, null);
  }

  protected void assertModuleLibDep(String moduleName, String depName, String path) {
    assertModuleLibDep(moduleName, depName, path, null, null);
  }

  protected void assertModuleLibDep(String moduleName, String depName, String path, String sourcePath, String javadocPath) {
    LibraryOrderEntry lib = getModuleLibDep(moduleName, depName);

    assertModuleLibDepPath(lib, OrderRootType.CLASSES, path);
    assertModuleLibDepPath(lib, OrderRootType.SOURCES, sourcePath);
    assertModuleLibDepPath(lib, JavadocOrderRootType.getInstance(), javadocPath);
  }

  private void assertModuleLibDepPath(LibraryOrderEntry lib, OrderRootType type, String path) {
    if (path == null) return;

    String[] urls = lib.getUrls(type);
    assertEquals(1, urls.length);
    assertEquals(path, urls[0]);
  }

  protected void assertModuleLibDepClassesValidity(String moduleName, String depName, boolean areValid) {
    LibraryOrderEntry lib = getModuleLibDep(moduleName, depName);

    String jarUrl = lib.getUrls(OrderRootType.CLASSES)[0];
    assertEquals(areValid, lib.getLibrary().isValid(jarUrl, OrderRootType.CLASSES));
  }

  private LibraryOrderEntry getModuleLibDep(String moduleName, String depName) {
    LibraryOrderEntry lib = null;

    for (OrderEntry e : getRootManager(moduleName).getOrderEntries()) {
      if (e instanceof LibraryOrderEntry && e.getPresentableName().equals(depName)) {
        lib = (LibraryOrderEntry)e;
      }
    }
    assertNotNull("library dependency not found: " + depName, lib);
    return lib;
  }

  protected void assertModuleLibDeps(String moduleName, String... expectedDeps) {
    assertModuleDeps(moduleName, LibraryOrderEntry.class, expectedDeps);
  }

  protected void assertExportedModuleDeps(String moduleName, String... expectedDeps) {
    final List<String> actual = new ArrayList<String>();

    getRootManager(moduleName).processOrder(new RootPolicy<Object>() {
      @Override
      public Object visitModuleOrderEntry(ModuleOrderEntry e, Object value) {
        if (e.isExported()) actual.add(e.getModuleName());
        return null;
      }

      @Override
      public Object visitLibraryOrderEntry(LibraryOrderEntry e, Object value) {
        if (e.isExported()) actual.add(e.getLibraryName());
        return null;
      }
    }, null);

    assertOrderedElementsAreEqual(actual, expectedDeps);
  }

  protected void assertModuleModuleDeps(String moduleName, String... expectedDeps) {
    assertModuleDeps(moduleName, ModuleOrderEntry.class, expectedDeps);
  }

  private void assertModuleDeps(String moduleName, Class clazz, String... expectedDeps) {
    List<String> actual = new ArrayList<String>();

    for (OrderEntry e : getRootManager(moduleName).getOrderEntries()) {
      if (clazz.isInstance(e)) {
        actual.add(e.getPresentableName());
      }
    }

    assertOrderedElementsAreEqual(actual, expectedDeps);
  }

  public void assertProjectLibraries(String... expectedNames) {
    List<String> actualNames = new ArrayList<String>();
    for (Library each : ProjectLibraryTable.getInstance(myProject).getLibraries()) {
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

  protected Module getModule(String name) {
    Module m = ModuleManager.getInstance(myProject).findModuleByName(name);
    assertNotNull("Module " + name + " not found", m);
    return m;
  }

  private ContentEntry getContentRoot(String moduleName) {
    ContentEntry[] ee = getContentRoots(moduleName);
    List<String> roots = new ArrayList<String>();
    for (ContentEntry e : ee) {
      roots.add(e.getUrl());
    }

    String message = "Several content roots found: [" + StringUtil.join(roots, ", ") + "]";
    assertEquals(message, 1, ee.length);

    return ee[0];
  }

  private ContentEntry getContentRoot(String moduleName, String path) {
    for (ContentEntry e : getContentRoots(moduleName)) {
      if (e.getUrl().equals(VfsUtil.pathToUrl(path))) return e;
    }
    throw new AssertionError("content root not found");
  }

  public ContentEntry[] getContentRoots(String moduleName) {
    return getRootManager(moduleName).getContentEntries();
  }

  private ModuleRootManager getRootManager(String module) {
    return ModuleRootManager.getInstance(getModule(module));
  }

  protected void importProject(String xml) throws IOException, MavenException {
    createProjectPom(xml);
    importProject();
  }

  protected void importProject() throws MavenException {
    importProjectWithProfiles();
  }

  protected void importProjectWithProfiles(String... profiles) throws MavenException {
    doImportProjects(Collections.singletonList(myProjectPom), profiles);
  }

  protected void importProject(VirtualFile file) throws MavenException {
    importProjects(file);
  }

  protected void importProjects(VirtualFile... files) throws MavenException {
    doImportProjects(Arrays.asList(files));
  }

  private void doImportProjects(List<VirtualFile> files, String... profiles) {
    myProfilesList = Arrays.asList(profiles);

    initProjectsManager(false);
    myProjectsManager.resetManagedFilesAndProfilesInTests(files, myProfilesList);
    myProjectsManager.waitForResolvingCompletion();
    // todo replace with myMavenProjectsManager.flushPendingImportRequestsInTests();
    myProjectsManager.scheduleImportInTests(files);
    myProjectsManager.importProjects();
  }

  protected void initProjectsManager(boolean enableEventHandling) {
    myProjectsManager.initForTests();
    myProjectsTree = myProjectsManager.getProjectsTreeForTests();
    if (enableEventHandling) myProjectsManager.listenForExternalChanges();
  }

  protected void waitForReadingCompletion() {
    myProjectsManager.waitForReadingCompletion();
  }

  protected void resolveDependenciesAndImport() {
    myProjectsManager.waitForResolvingCompletionAndImport();
  }

  protected void resolveFoldersAndImport() {
    myProjectsManager.scheduleFoldersResolving();
    myProjectsManager.waitForFoldersResolvingCompletionAndImport();
  }

  protected void resolvePlugins() {
    myProjectsManager.waitForPluginsResolvingCompletion();
  }

  protected void downloadArtifacts() {
    myProjectsManager.scheduleArtifactsDownloading();
    myProjectsManager.waitForArtifactsDownloadingCompletion();
  }

  protected void performPostImportTasks() {
    myProjectsManager.waitForPostImportTasksCompletion();
  }

  protected void executeGoal(String relativePath, String goal) {
    VirtualFile dir = myProjectRoot.findFileByRelativePath(relativePath);

    MavenRunnerParameters rp = new MavenRunnerParameters(true, dir.getPath(), Arrays.asList(goal), null);
    MavenRunnerSettings rs = new MavenRunnerSettings();
    MavenExecutor e = new MavenExternalExecutor(rp, getMavenGeneralSettings(), rs, NULL_MAVEN_CONSOLE);

    e.execute(new EmptyProgressIndicator());
  }

  protected void removeFromLocalRepository(String relativePath) throws IOException {
    FileUtil.delete(new File(getRepositoryPath(), relativePath));
  }

  protected Sdk setupJdkForModule(String moduleName) {
    ModifiableRootModel m = ModuleRootManager.getInstance(getModule(moduleName)).getModifiableModel();
    Sdk sdk = createJdk("Java 1.5");
    m.setSdk(sdk);
    m.commit();
    return sdk;
  }

  protected Sdk createJdk(String versionName) {
    File file;
    try {
      file = FileUtil.createTempFile(myDir, "mockJdk", null, false);
      file.mkdirs();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    String oldValue = System.setProperty("idea.testingFramework.mockJDK", file.getPath());
    Sdk sdk;
    try {
      sdk = JavaSdkImpl.getMockJdk(versionName);
    }
    finally {
      if (oldValue == null) {
        System.clearProperty("idea.testingFramework.mockJDK");
      }
      else {
        System.setProperty("idea.testingFramework.mockJDK", oldValue);
      }
    }
    return sdk;
  }

  protected void compileModules(String... moduleNames) {
    List<Module> modules = new ArrayList<Module>();
    for (String each : moduleNames) {
      setupJdkForModule(each);
      modules.add(getModule(each));
    }

    CompilerWorkspaceConfiguration.getInstance(myProject).CLEAR_OUTPUT_DIRECTORY = true;
    CompilerManagerImpl.testSetup();

    List<VirtualFile> roots = Arrays.asList(ProjectRootManager.getInstance(myProject).getContentRoots());
    TranslatingCompilerFilesMonitor.getInstance().scanSourceContent(myProject, roots, roots.size(), true);

    CompileScope scope = new ModuleCompileScope(myProject, modules.toArray(new Module[modules.size()]), false);

    CompilerManager.getInstance(myProject).make(scope, new CompileStatusNotification() {
      public void finished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
        assertFalse(aborted);
        assertEquals(collectMessages(compileContext, CompilerMessageCategory.ERROR), 0, errors);
        assertEquals(collectMessages(compileContext, CompilerMessageCategory.WARNING), 0, warnings);
      }
    });
  }

  private String collectMessages(CompileContext compileContext, CompilerMessageCategory messageType) {
    String result = "";
    for (CompilerMessage each : compileContext.getMessages(messageType)) {
      VirtualFile file = each.getVirtualFile();
      result += each.getMessage() + " FILE: " + (file == null ? "null" : file.getPath()) + "\n";
    }
    return result;
  }

  protected AtomicInteger configConfirmationForYesAnswer() {
    final AtomicInteger counter = new AtomicInteger();
    Messages.setTestDialog(new TestDialog() {
      public int show(String message) {
        counter.set(counter.get() + 1);
        return 0;
      }
    });
    return counter;
  }

  protected AtomicInteger configConfirmationForNoAnswer() {
    final AtomicInteger counter = new AtomicInteger();
    Messages.setTestDialog(new TestDialog() {
      public int show(String message) {
        counter.set(counter.get() + 1);
        return 1;
      }
    });
    return counter;
  }
}
