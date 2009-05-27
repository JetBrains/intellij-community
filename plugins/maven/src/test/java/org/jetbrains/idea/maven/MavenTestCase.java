package org.jetbrains.idea.maven;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import junit.framework.TestCase;
import org.jetbrains.idea.maven.embedder.MavenConsole;
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

public abstract class MavenTestCase extends TestCase {
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
    new WriteAction() {
      protected void run(Result result) throws Throwable {
        MavenTestCase.super.runTest();
      }
    }.executeSilently().throwException();
  }

  protected MavenGeneralSettings getMavenGeneralSettings() {
    return MavenProjectsManager.getInstance(myProject).getGeneralSettings();
  }

  protected MavenImportingSettings getMavenImporterSettings() {
    return MavenProjectsManager.getInstance(myProject).getImportingSettings();
  }

  protected MavenDownloadingSettings getMavenDownloadingSettings() {
    return MavenProjectsManager.getInstance(myProject).getDownloadingSettings();
  }

  protected String getRepositoryPath() {
    String path = getRepositoryFile().getPath();
    return FileUtil.toSystemIndependentName(path);
  }

  protected File getRepositoryFile() {
    return getMavenGeneralSettings().getEffectiveLocalRepository();
  }

  protected void setRepositoryPath(String path) {
    getMavenGeneralSettings().setLocalRepository(path);
  }

  protected String getProjectPath() {
    return myProjectRoot.getPath();
  }

  protected String getParentPath() {
    return myProjectRoot.getParent().getPath();
  }

  protected void updateSettingsXml(String content) throws IOException {
    updateSettingsXmlFully(createSettingsXmlContent(content));
  }

  protected void updateSettingsXmlFully(String content) throws IOException {
    File ioFile = new File(myDir, "settings.xml");
    ioFile.createNewFile();
    VirtualFile f = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile);
    setFileContent(f, content);
    getMavenGeneralSettings().setMavenSettingsFile(f.getPath());
  }

  protected void deleteSettingsXml() throws IOException {
    VirtualFile f = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myDir, "settings.xml"));
    if (f != null) f.delete(this);
  }

  private String createSettingsXmlContent(String content) {
    return "<settings>" +
           content +
           "<mirrors>" +
           "  <mirror>" +
           "    <id>Nexus</id>" +
           "    <name>Nexus Public Mirror</name>" +
           "    <url>http://maven.labs.intellij.net:8081/nexus/content/groups/public/</url>" +
           "    <mirrorOf>*</mirrorOf>" +
           "  </mirror>" +
           "</mirrors>" +
           "</settings>";
  }

  protected void restoreSettingsFile() throws IOException {
    updateSettingsXml("");
  }

  protected Module createModule(String name) throws IOException {
    VirtualFile f = createProjectSubFile(name + "/" + name + ".iml");
    Module module = ModuleManager.getInstance(myProject).newModule(f.getPath(), StdModuleTypes.JAVA);
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

  protected void createProfilesXmlOldStyle(String xml) throws IOException {
    createProfilesFile(myProjectRoot, xml, true);
  }

  protected void createProfilesXmlNewStyle(String xml) throws IOException {
    createProfilesFile(myProjectRoot, xml, false);
  }

  protected void createProfilesXmlOldStyle(String relativePath, String xml) throws IOException {
    createProfilesFile(createProjectSubDir(relativePath), xml, true);
  }

  private void createProfilesFile(VirtualFile dir, String xml, boolean oldStyle) throws IOException {
    VirtualFile f = dir.findChild("profiles.xml");
    if (f == null) {
      f = dir.createChildData(null, "profiles.xml");
    }
    setFileContent(f, createValidProfiles(xml, oldStyle));
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
    file.setBinaryContent(content.getBytes(), -1, file.getTimeStamp() + 1);
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
      assertTrue(s, expectedElement.equals(actualElement));
    }
  }

  protected boolean ignore() {
    System.out.println("Ignored: " + getClass().getSimpleName() + "." + getName());
    return true;
  }
}
