/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package org.jetbrains.idea.maven;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.MavenCore;
import org.jetbrains.idea.maven.events.MavenEventsHandler;
import org.jetbrains.idea.maven.navigator.PomTreeStructure;
import org.jetbrains.idea.maven.navigator.PomTreeViewSettings;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.repo.MavenRepository;
import org.jetbrains.idea.maven.state.MavenProjectsState;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public abstract class ImportingTestCase extends IdeaTestCase {
  private File dir;
  private File repoDir;

  private VirtualFile projectRoot;
  private VirtualFile projectPom;

  private List<VirtualFile> poms = new ArrayList<VirtualFile>();
  protected MavenImporterPreferences prefs;
  protected MavenProjectModel projectModel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          init();
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  private void init() throws Exception {
    initDirs();
    configMaven();
  }

  private void initDirs() throws IOException {
    dir = createTempDirectory();

    repoDir = new File(dir, "local");
    repoDir.mkdirs();

    File projectDir = new File(dir, "project");
    projectDir.mkdirs();
    projectRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(projectDir);
  }

  private void configMaven() {
    myProject.getComponent(MavenCore.class).getState().setLocalRepository(repoDir.getPath());

    MavenWorkspacePreferencesComponent c = myProject.getComponent(MavenWorkspacePreferencesComponent.class);
    prefs = c.getState().myImporterPreferences;
  }

  @Override
  protected void setUpModule() {
  }

  @Override
  protected void tearDown() throws Exception {
    Messages.setTestDialog(TestDialog.DEFAULT);
    super.tearDown();
  }

  protected String getProjectPath() {
    return projectRoot.getPath();
  }

  protected String getParentPath() {
    return projectRoot.getParent().getPath();
  }

  protected String getRepositoryPath() {
    return FileUtil.toSystemIndependentName(repoDir.getPath());
  }

  protected PomTreeStructure.RootNode createMavenTree() {
    PomTreeStructure s = new PomTreeStructure(myProject, myProject.getComponent(MavenProjectsState.class),
                                              myProject.getComponent(MavenRepository.class),
                                              myProject.getComponent(MavenEventsHandler.class)) {
      {
        for (VirtualFile pom : poms) {
          this.root.addUnder(new PomNode(pom));
        }
      }

      protected PomTreeViewSettings getTreeViewSettings() {
        return new PomTreeViewSettings();
      }

      protected void updateTreeFrom(@Nullable SimpleNode node) {
      }
    };
    return (PomTreeStructure.RootNode)s.getRootElement();
  }

  protected void assertModules(String... expectedNames) {
    Module[] actual = ModuleManager.getInstance(myProject).getModules();
    List<String> actualNames = new ArrayList<String>();

    for (Module m : actual) {
      actualNames.add(m.getName());
    }

    assertElementsAreEqual(expectedNames, actualNames);
  }

  protected void assertContentRoots(String moduleName, String... expectedRoots) {
    List<String> actual = new ArrayList<String>();
    for (ContentEntry e : getContentRoots(moduleName)) {
      actual.add(e.getUrl());
    }

    for (int i = 0; i < expectedRoots.length; i++) {
      expectedRoots[i] = VfsUtil.pathToUrl(expectedRoots[i]);
    }

    assertElementsAreEqual(expectedRoots, actual);
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

    assertElementsAreEqual(expected, actual);
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

  protected void assertModuleLibDep(String moduleName, String depName, String path) {
    assertModuleLibDep(moduleName, depName, path, null, null);
  }

  protected void assertModuleLibDep(String moduleName, String depName, String path, String sourcePath, String javadocPath) {
    LibraryOrderEntry lib = null;

    for (OrderEntry e : getRootManager(moduleName).getOrderEntries()) {
      if (e instanceof LibraryOrderEntry && e.getPresentableName().equals(depName)) {
        lib = (LibraryOrderEntry)e;
      }
    }
    assertNotNull(lib);
    assertModuleLibDepPath(lib, OrderRootType.CLASSES, path);
    assertModuleLibDepPath(lib, OrderRootType.SOURCES, sourcePath);
    assertModuleLibDepPath(lib, JavadocOrderRootType.INSTANCE, javadocPath);
  }

  private void assertModuleLibDepPath(LibraryOrderEntry lib, OrderRootType type, String path) {
    if (path == null) return;

    String[] urls = lib.getUrls(type);
    assertEquals(1, urls.length);
    assertEquals(path, urls[0]);
  }

  protected void assertModuleLibDeps(String moduleName, String... expectedDeps) {
    assertModuleDeps(moduleName, LibraryOrderEntry.class, expectedDeps);
  }

  protected void assertExportedModuleLibDeps(String moduleName, String... expectedDeps) {
    final List<String> actual = new ArrayList<String>();

    getRootManager(moduleName).processOrder(new JavaRootPolicy<Object>() {
      @Override
      public Object visitLibraryOrderEntry(LibraryOrderEntry e, Object value) {
        if (e.isExported()) actual.add(e.getLibraryName());
        return null;
      }
    }, null);

    assertElementsAreEqual(expectedDeps, actual);
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

    assertElementsAreEqual(expectedDeps, actual);
  }

  protected <T, U> void assertElementsAreEqual(T[] expected, Collection<U> actual) {
    String s = "\nexpected: " + Arrays.asList(expected) + "\nactual: " + actual;
    assertEquals(s, expected.length, actual.size());
    for (T eachExpected : expected) {
      boolean found = false;
      for (U eachActual : actual) {
        if (eachExpected.equals(eachActual)) {
          found = true;
          break;
        }
      }
      assertTrue(s, found);
    }
  }

  protected Module getModule(String name) {
    Module m = ModuleManager.getInstance(myProject).findModuleByName(name);
    assertNotNull("Module " + name + " not found", m);
    return m;
  }

  private ContentEntry getContentRoot(String moduleName) {
    ContentEntry[] ee = getContentRoots(moduleName);
    assertEquals(1, ee.length);
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

  protected void createProjectPom(String xml) throws IOException {
    projectPom = createPomFile(projectRoot, xml);
  }

  protected void updateProjectPom(String xml) throws IOException {
    setFileContent(projectPom, xml);
  }

  protected void createModulePom(String relativePath, String xml) throws IOException {
    createPomFile(createProjectSubDir(relativePath), xml);
  }

  protected void updateModulePom(String relativePath, String xml) throws IOException {
    setFileContent(projectRoot.findFileByRelativePath(relativePath + "/pom.xml"), xml);
  }

  private VirtualFile createPomFile(VirtualFile dir, String xml) throws IOException {
    VirtualFile f = dir.createChildData(null, "pom.xml");
    poms.add(f);
    return setFileContent(f, xml);
  }

  private VirtualFile setFileContent(VirtualFile f, String xml) throws IOException {
    f.setBinaryContent(createValidPom(xml).getBytes());
    return f;
  }

  private String createValidPom(String xml) {
    return "<?xml version=\"1.0\"?>" +
           "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"" +
           "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" +
           "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">" +
           "  <modelVersion>4.0.0</modelVersion>" +
           xml +
           "</project>";
  }

  protected void createProfilesXml(String xml) throws IOException {
    VirtualFile f = projectRoot.createChildData(null, "profiles.xml");
    f.setBinaryContent(createValidProfiles(xml).getBytes());
  }

  private String createValidProfiles(String xml) {
    return "<?xml version=\"1.0\"?>" +
           "<profiles>" +
           xml +
           "</profiles>";
  }

  protected VirtualFile createProjectSubDir(String relativePath) {
    File f = new File(getProjectPath(), relativePath);
    f.mkdirs();
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);
  }

  protected VirtualFile createProjectSubFile(String relativePath) throws IOException {
    File f = new File(getProjectPath(), relativePath);
    f.getParentFile().mkdirs();
    f.createNewFile();
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);
  }

  protected void importProject(String xml) throws IOException {
    createProjectPom(xml);
    importProject();
  }

  protected void importProjectUnsafe(String xml) throws IOException, MavenException {
    createProjectPom(xml);
    importProjectWithProfilesUnsafe();
  }

  protected void importProject() {
    try {
      importProjectWithProfiles();
    }
    catch (MavenException e) {
      throw new RuntimeException(e);
    }
  }

  protected void importProjectWithProfiles(String... profiles) throws MavenException {
    importProjectWithProfilesUnsafe(profiles);
  }

  private void importProjectWithProfilesUnsafe(String... profiles) throws MavenException {
    try {
      List<VirtualFile> files = Collections.singletonList(projectPom);
      List<String> profilesList = Arrays.asList(profiles);

      MavenImportProcessor p = new MavenImportProcessor(myProject);
      p.createMavenProjectModel(files, new HashMap<VirtualFile, Module>(), profilesList, new Progress());
      p.createMavenToIdeaMapping();
      p.resolve(myProject, profilesList);
      p.commit(myProject, profilesList);
      projectModel = p.getMavenProjectModel();
    }
    catch (CanceledException e) {
      throw new RuntimeException(e);
    }
  }

  protected void putArtifactInLocalRepository(String groupId, String artefactId, String version, String timestamp, String build) {
    String rawXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><metadata>" +
                 "  <groupId>%s</groupId>" +
                 "  <artifactId>%s</artifactId>" +
                 "  <version>%s</version>" +
                 "  <versioning>" +
                 "    <snapshot>" +
                 "      <timestamp>%s</timestamp>" +
                 "      <buildNumber>%s</buildNumber>" +
                 "    </snapshot>" +
                 "    <lastUpdated>0000000000000</lastUpdated>" +
                 "  </versioning>" +
                 "</metadata>";

    String xml = String.format(rawXml, groupId, artefactId, version, timestamp, build);

    String currentDate = new SimpleDateFormat("yyyy-MM-dd HH\\:mm\\:ss +0300").format(new Date());
    String prop = "internal.maven-metadata-internal.xml.lastUpdated=" + currentDate;

    String dir = groupId + "/" + artefactId + "/" + version + "/";

    String xmlName = dir + "/maven-metadata-internal.xml";
    String propName = dir + "/resolver-status.properties";

    writeFile(new File(repoDir, xmlName), xml);
    writeFile(new File(repoDir, propName), prop);
  }

  private void writeFile(File f, String string) {
    try {
      f.getParentFile().mkdirs();
      f.createNewFile();
      FileWriter w = new FileWriter(f);
      try {
        w.write(string);
      }
      finally {
        w.close();
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
