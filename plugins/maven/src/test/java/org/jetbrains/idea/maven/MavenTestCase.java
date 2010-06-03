/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public abstract class MavenTestCase extends UsefulTestCase {
  protected static final MavenConsole NULL_MAVEN_CONSOLE = new NullMavenConsole();
  protected static final MavenProgressIndicator EMPTY_MAVEN_PROCESS = new MavenProgressIndicator(new EmptyProgressIndicator());

  private static File ourTempDir;

  protected IdeaProjectTestFixture myTestFixture;

  protected Project myProject;

  protected File myDir;
  protected VirtualFile myProjectRoot;

  protected VirtualFile myProjectPom;
  protected List<VirtualFile> myAllPoms = new ArrayList<VirtualFile>();

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    ensureTempDirCreated();

    myDir = FileUtil.createTempFile(ourTempDir, "test", "", false);
    myDir.mkdirs();

    setUpFixtures();

    myProject = myTestFixture.getProject();

    MavenWorkspaceSettingsComponent.getInstance(myProject).loadState(new MavenWorkspaceSettings());

    String home = getTestMavenHome();
    if (home != null) {
      getMavenGeneralSettings().setMavenHome(home);
    }

    restoreSettingsFile();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          setUpInWriteAction();
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  private void ensureTempDirCreated() {
    if (ourTempDir != null) return;

    ourTempDir = new File(FileUtil.getTempDirectory(), "mavenTests");
    FileUtil.delete(ourTempDir);
    ourTempDir.mkdirs();
  }

  protected void setUpFixtures() throws Exception {
    myTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder().getFixture();
    myTestFixture.setUp();
  }

  protected void setUpInWriteAction() throws Exception {
    File projectDir = new File(myDir, "project");
    projectDir.mkdirs();
    myProjectRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(projectDir);
  }

  @Override
  protected void tearDown() throws Exception {
    myProject = null;
    tearDownFixtures();
    if (!FileUtil.delete(myDir)) {
      System.out.println("Cannot delete " + myDir);
      myDir.deleteOnExit();
    }

    resetClassFields(getClass());
    super.tearDown();
  }

  protected void tearDownFixtures() throws Exception {
    myTestFixture.tearDown();
    myTestFixture = null;
  }

  private void resetClassFields(final Class<?> aClass) {
    if (aClass == null) return;

    final Field[] fields = aClass.getDeclaredFields();
    for (Field field : fields) {
      final int modifiers = field.getModifiers();
      if ((modifiers & Modifier.FINAL) == 0
          && (modifiers & Modifier.STATIC) == 0
          && !field.getType().isPrimitive()) {
        field.setAccessible(true);
        try {
          field.set(this, null);
        }
        catch (IllegalAccessException e) {
          e.printStackTrace();
        }
      }
    }

    if (aClass == MavenTestCase.class) return;
    resetClassFields(aClass.getSuperclass());
  }

  @Override
  protected void runTest() throws Throwable {
    if (runInWriteAction()) {
      new WriteAction() {
        protected void run(Result result) throws Throwable {
          MavenTestCase.super.runTest();
        }
      }.executeSilently().throwException();
    }
    else {
      MavenTestCase.super.runTest();
    }
  }

  protected boolean runInWriteAction() {
    return true;
  }

  protected String getRoot() {
    if (SystemInfo.isWindows) return "c:";
    return "";
  }

  protected static String getEnvVar() {
    if (SystemInfo.isWindows) return "TEMP";
    else if (SystemInfo.isLinux) return "HOME";
    return "TMPDIR";
  }

  protected MavenGeneralSettings getMavenGeneralSettings() {
    return MavenProjectsManager.getInstance(myProject).getGeneralSettings();
  }

  protected MavenImportingSettings getMavenImporterSettings() {
    return MavenProjectsManager.getInstance(myProject).getImportingSettings();
  }

  protected String getRepositoryPath() {
    String path = getRepositoryFile().getPath();
    return FileUtil.toSystemIndependentName(path);
  }

  protected File getRepositoryFile() {
    return getMavenGeneralSettings().getEffectiveLocalRepository();
  }

  protected void setRepositoryPath(String path) {
    getMavenGeneralSettings().setOverridenLocalRepository(path);
  }

  protected String getProjectPath() {
    return myProjectRoot.getPath();
  }

  protected String getParentPath() {
    return myProjectRoot.getParent().getPath();
  }

  protected String pathFromBasedir(String relPath) {
    return pathFromBasedir(myProjectRoot, relPath);
  }

  protected String pathFromBasedir(VirtualFile root, String relPath) {
    return FileUtil.toSystemIndependentName(root.getPath() + "/" + relPath);
  }

  protected VirtualFile updateSettingsXml(String content) throws IOException {
    return updateSettingsXmlFully(createSettingsXmlContent(content));
  }

  protected VirtualFile updateSettingsXmlFully(String content) throws IOException {
    File ioFile = new File(myDir, "settings.xml");
    ioFile.createNewFile();
    VirtualFile f = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile);
    setFileContent(f, content);
    getMavenGeneralSettings().setMavenSettingsFile(f.getPath());
    return f;
  }

  protected void deleteSettingsXml() throws IOException {
    VirtualFile f = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myDir, "settings.xml"));
    if (f != null) f.delete(this);
  }

  private String createSettingsXmlContent(String content) {
    String mirror = System.getProperty("idea.maven.test.mirror",
                                       "http://maven.labs.intellij.net:8081/nexus/content/groups/public/");
    return "<settings>" +
           content +
           "<mirrors>" +
           "  <mirror>" +
           "    <id>Nexus</id>" +
           "    <url>" + mirror + "</url>" +
           "    <mirrorOf>*</mirrorOf>" +
           "  </mirror>" +
           "</mirrors>" +
           "</settings>";
  }

  protected void restoreSettingsFile() throws IOException {
    updateSettingsXml("");
  }

  protected Module createModule(String name) throws IOException {
    return createModule(name, StdModuleTypes.JAVA);
  }

  protected Module createModule(String name, ModuleType type) throws IOException {
    VirtualFile f = createProjectSubFile(name + "/" + name + ".iml");
    Module module = ModuleManager.getInstance(myProject).newModule(f.getPath(), type);
    ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    model.addContentEntry(f.getParent());
    model.commit();
    return module;
  }

  protected void createProjectPom(String xml) throws IOException {
    myProjectPom = createPomFile(myProjectRoot, xml);
  }

  protected VirtualFile createModulePom(String relativePath, String xml) throws IOException {
    return createPomFile(createProjectSubDir(relativePath), xml);
  }

  protected VirtualFile createPomFile(VirtualFile dir, String xml) throws IOException {
    VirtualFile f = dir.findChild("pom.xml");
    if (f == null) {
      f = dir.createChildData(null, "pom.xml");
      myAllPoms.add(f);
    }
    setFileContent(f, createPomXml(xml));
    return f;
  }

  protected String createPomXml(String xml) {
    return "<?xml version=\"1.0\"?>" +
           "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"" +
           "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" +
           "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">" +
           "  <modelVersion>4.0.0</modelVersion>" +
           xml +
           "</project>";
  }

  protected VirtualFile createProfilesXmlOldStyle(String xml) throws IOException {
    return createProfilesFile(myProjectRoot, xml, true);
  }

  protected VirtualFile createProfilesXmlOldStyle(String relativePath, String xml) throws IOException {
    return createProfilesFile(createProjectSubDir(relativePath), xml, true);
  }

  protected VirtualFile createProfilesXml(String xml) throws IOException {
    return createProfilesFile(myProjectRoot, xml, false);
  }

  protected VirtualFile createProfilesXml(String relativePath, String xml) throws IOException {
    return createProfilesFile(createProjectSubDir(relativePath), xml, false);
  }

  private VirtualFile createProfilesFile(VirtualFile dir, String xml, boolean oldStyle) throws IOException {
    return createProfilesFile(dir, createValidProfiles(xml, oldStyle));
  }

  protected VirtualFile createFullProfilesXml(String content) throws IOException {
    return createProfilesFile(myProjectRoot, content);
  }

  protected VirtualFile createFullProfilesXml(String relativePath, String content) throws IOException {
    return createProfilesFile(createProjectSubDir(relativePath), content);
  }

  private VirtualFile createProfilesFile(VirtualFile dir, String content) throws IOException {
    VirtualFile f = dir.findChild("profiles.xml");
    if (f == null) {
      f = dir.createChildData(null, "profiles.xml");
    }
    setFileContent(f, content);
    return f;
  }

  private String createValidProfiles(String xml, boolean oldStyle) {
    if (oldStyle) {
      return "<?xml version=\"1.0\"?>" +
             "<profiles>" +
             xml +
             "</profiles>";
    }
    return "<?xml version=\"1.0\"?>" +
           "<profilesXml>" +
           "<profiles>" +
           xml +
           "</profiles>" +
           "</profilesXml>";
  }

  protected void deleteProfilesXml() throws IOException {
    VirtualFile f = myProjectRoot.findChild("profiles.xml");
    if (f != null) f.delete(this);
  }

  protected void createStdProjectFolders() {
    createProjectSubDirs("src/main/java",
                         "src/main/resources",
                         "src/test/java",
                         "src/test/resources");
  }

  protected void createProjectSubDirs(String... relativePaths) {
    for (String path : relativePaths) {
      createProjectSubDir(path);
    }
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

  protected VirtualFile createProjectSubFile(String relativePath, String content) throws IOException {
    VirtualFile file = createProjectSubFile(relativePath);
    setFileContent(file, content);
    return file;
  }

  private void setFileContent(VirtualFile file, String content) throws IOException {
    file.setBinaryContent(content.getBytes(), file.getModificationStamp() + 4000, file.getTimeStamp() + 4000);
  }

  protected void assertPathEquals(String expected, String actual) {
    if (expected != null) expected = FileUtil.toSystemIndependentName(expected);
    if (actual != null) actual = FileUtil.toSystemIndependentName(actual);
    assertEquals(expected, actual);
  }

  protected <T, U> void assertOrderedElementsAreEqual(Collection<U> actual, Collection<T> expected) {
    assertOrderedElementsAreEqual(actual, expected.toArray());
  }

  protected <T, U> void assertUnorderedElementsAreEqual(Collection<U> actual, Collection<T> expected) {
    assertUnorderedElementsAreEqual(actual, expected.toArray());
  }

  protected <T, U> void assertUnorderedElementsAreEqual(U[] actual, T... expected) {
    assertUnorderedElementsAreEqual(Arrays.asList(actual), expected);
  }

  protected <T, U> void assertUnorderedElementsAreEqual(Collection<U> actual, T... expected) {
    String s = "\nexpected: " + Arrays.asList(expected) + "\nactual: " + new ArrayList<U>(actual);
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

  protected <T, U> void assertOrderedElementsAreEqual(Collection<U> actual, T... expected) {
    String s = "\nexpected: " + Arrays.asList(expected) + "\nactual: " + new ArrayList<U>(actual);
    assertEquals(s, expected.length, actual.size());

    List<U> actualList = new ArrayList<U>(actual);
    for (int i = 0; i < expected.length; i++) {
      T expectedElement = expected[i];
      U actualElement = actualList.get(i);
      assertTrue(s, expectedElement.equals(actualElement));
    }
  }

  protected <T> void assertContain(List<? extends T> actual, T... expected) {
    List<T> expectedList = Arrays.asList(expected);
    assertTrue("expected: " + expectedList + "\n" + "actual: " + actual.toString(), actual.containsAll(expectedList));
  }

  protected <T> void assertDoNotContain(List<T> actual, T... expected) {
    List<T> actualCopy = new ArrayList<T>(actual);
    actualCopy.removeAll(Arrays.asList(expected));
    assertTrue(actual.toString(), actualCopy.size() == actual.size());
  }

  protected boolean ignore() {
    System.out.println("Ignored: " + getClass().getSimpleName() + "." + getName());
    return true;
  }

  protected boolean hasMavenInstallation() {
    boolean result = getTestMavenHome() != null;
    if (!result) System.out.println("Ignored, because Maven installation not found: " + getClass().getSimpleName() + "." + getName());
    return result;
  }

  private String getTestMavenHome() {
    return System.getProperty("idea.maven.test.home");
  }
}
