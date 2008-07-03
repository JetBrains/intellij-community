/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package org.jetbrains.idea.maven;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.events.MavenEventsHandler;
import org.jetbrains.idea.maven.navigator.MavenTreeStructure;
import org.jetbrains.idea.maven.navigator.PomTreeViewSettings;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.indices.MavenPluginsRepository;
import org.jetbrains.idea.maven.runner.MavenRunnerSettings;
import org.jetbrains.idea.maven.runner.executor.MavenEmbeddedExecutor;
import org.jetbrains.idea.maven.runner.executor.MavenRunnerParameters;
import org.jetbrains.idea.maven.state.MavenProjectsManager;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class MavenImportingTestCase extends MavenTestCase {
  protected MavenProjectsTree myMavenTree;
  protected MavenProjectsManager myMavenProjectsManager;
  private List<String> myProfilesList;

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    myMavenProjectsManager = MavenProjectsManager.getInstance(myProject);
    removeFromLocalRepository("test");
  }

  @Override
  protected void tearDown() throws Exception {
    removeFromLocalRepository("test");
    super.tearDown();
  }

  protected MavenImportSettings getMavenImporterSettings() {
    return myMavenProjectsManager.getImportSettings();
  }

  protected MavenTreeStructure.RootNode createMavenTree() {
    MavenTreeStructure s = new MavenTreeStructure(myProject,
                                              myMavenProjectsManager,
                                              MavenPluginsRepository.getInstance(myProject),
                                              myProject.getComponent(MavenEventsHandler.class)) {
      {
        for (MavenProjectModel each : myMavenProjectsManager.getProjects()) {
          this.root.addUnder(new MavenTreeStructure.PomNode(each));
        }
      }

      protected PomTreeViewSettings getTreeViewSettings() {
        return new PomTreeViewSettings();
      }

      protected void updateTreeFrom(@Nullable SimpleNode node) {
      }
    };
    return (MavenTreeStructure.RootNode)s.getRootElement();
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

  private ContentEntry[] getContentRoots(String moduleName) {
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

  protected void importSeveralProjects(VirtualFile... files) throws MavenException {
    doImportProjects(Arrays.asList(files));
  }

  private void doImportProjects(List<VirtualFile> files, String... profiles) throws MavenException {
    myProfilesList = Arrays.asList(profiles);

    myMavenProjectsManager.doInitComponent(true);
    myMavenProjectsManager.setManagedFiles(files);
    myMavenProjectsManager.setActiveProfiles(myProfilesList);
    myMavenProjectsManager.reimport();
    myMavenTree = myMavenProjectsManager.getMavenProjectTree();
  }

  protected void resolveProject() throws MavenException, CanceledException {
    myMavenProjectsManager.resolve();
  }

  protected void generateSources() throws MavenException, CanceledException {
    myMavenProjectsManager.updateFolders();
  }

  protected void executeGoal(String relativePath, String goal) {
    VirtualFile pom = myProjectRoot.findFileByRelativePath(relativePath + "/pom.xml");

    MavenRunnerParameters rp = new MavenRunnerParameters(pom.getPath(), Arrays.asList(goal), null);
    MavenRunnerSettings rs = new MavenRunnerSettings();
    MavenEmbeddedExecutor e = new MavenEmbeddedExecutor(rp, getMavenCoreSettings(), rs);

    e.execute(new ArrayList<MavenProject>(), new EmptyProgressIndicator());
  }

  protected void removeFromLocalRepository(String relativePath) throws IOException {
    FileUtil.delete(new File(getRepositoryPath(), relativePath));
  }
}