package org.jetbrains.idea.maven;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import junit.framework.TestCase;
import org.jetbrains.idea.maven.core.MavenCore;
import org.jetbrains.idea.maven.core.MavenCoreSettings;
import org.jetbrains.idea.maven.project.MavenImporterSettings;
import org.jetbrains.idea.maven.state.MavenProjectsManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public abstract class MavenTestCase extends TestCase {
  protected IdeaProjectTestFixture myTestFixture;
  protected TempDirTestFixture myTempDirFixture;

  protected Project myProject;

  protected File myDir;
  protected VirtualFile myProjectRoot;

  protected VirtualFile myProjectPom;
  protected List<VirtualFile> myAllPoms = new ArrayList<VirtualFile>();

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    setUpCommonFixtures();

    myProject = myTestFixture.getProject();

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

  protected void setUpCommonFixtures() throws Exception {
    myTempDirFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture();
    myTempDirFixture.setUp();

    myTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder().getFixture();
    myTestFixture.setUp();
  }

  protected void setUpInWriteAction() throws Exception {
    myDir = new File(myTempDirFixture.getTempDirPath());

    File projectDir = new File(myDir, "project");
    projectDir.mkdirs();
    myProjectRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(projectDir);
  }

  @Override
  protected void tearDown() throws Exception {
    tearDownCommonFixtures();
    super.tearDown();
  }

  protected void tearDownCommonFixtures() throws Exception {
    myTestFixture.tearDown();
    myTempDirFixture.tearDown();
  }

  @Override
  protected void runTest() throws Throwable {
    new WriteAction() {
      protected void run(Result result) throws Throwable {
        MavenTestCase.super.runTest();
      }
    }.executeSilently().throwException();
  }

  protected MavenCore getMavenCore() {
    return MavenCore.getInstance(myProject);
  }

  protected MavenCoreSettings getMavenCoreSettings() {
    return getMavenCore().getState();
  }

  protected MavenImporterSettings getMavenImporterSettings() {
    return MavenProjectsManager.getInstance(myProject).getImporterSettings();
  }

  protected String getRepositoryPath() {
    String path = getMavenCoreSettings().getEffectiveLocalRepository().getPath();
    return FileUtil.toSystemIndependentName(path);
  }

  protected void setRepositoryPath(String path) {
    getMavenCoreSettings().setLocalRepository(path);
  }

  protected String getProjectPath() {
    return myProjectRoot.getPath();
  }

  protected String getParentPath() {
    return myProjectRoot.getParent().getPath();
  }

  protected void createProjectPom(String xml) throws IOException {
    myProjectPom = createPomFile(myProjectRoot, xml);
  }

  protected void updateProjectPom(String xml) throws IOException {
    setFileContent(myProjectPom, xml);
  }

  protected VirtualFile createModulePom(String relativePath, String xml) throws IOException {
    return createPomFile(createProjectSubDir(relativePath), xml);
  }

  protected void updateModulePom(String relativePath, String xml) throws IOException {
    setFileContent(myProjectRoot.findFileByRelativePath(relativePath + "/pom.xml"), xml);
  }

  private VirtualFile createPomFile(VirtualFile dir, String xml) throws IOException {
    VirtualFile f = dir.createChildData(null, "pom.xml");
    myAllPoms.add(f);
    return setFileContent(f, xml);
  }

  private VirtualFile setFileContent(VirtualFile f, String xml) throws IOException {
    f.setBinaryContent(createValidPom(xml).getBytes());
    return f;
  }

  protected String createValidPom(String xml) {
    return "<?xml version=\"1.0\"?>" +
           "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"" +
           "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" +
           "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">" +
           "  <modelVersion>4.0.0</modelVersion>" +
           xml +
           "</project>";
  }

  protected void createProfilesXml(String xml) throws IOException {
    VirtualFile f = myProjectRoot.createChildData(null, "profiles.xml");
    f.setBinaryContent(createValidProfiles(xml).getBytes());
  }

  private String createValidProfiles(String xml) {
    return "<?xml version=\"1.0\"?>" +
           "<profiles>" +
           xml +
           "</profiles>";
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

  protected <T, U> void assertUnorderedElementsAreEqual(Collection<U> actual, T... expected) {
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

  protected <T, U> void assertOrderedElementsAreEqual(Collection<U> actual, T... expected) {
    String s = "\nexpected: " + Arrays.asList(expected) + "\nactual: " + actual;
    assertEquals(s, expected.length, actual.size());

    List<U> actualList = new ArrayList<U>(actual);
    for (int i = 0; i < expected.length; i++) {
      T expectedElement = expected[i];
      U actualElement = actualList.get(i);
      assertEquals(s, expectedElement, actualElement);
    }
  }
}
