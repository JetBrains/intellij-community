// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework;

import com.intellij.execution.wsl.WSLDistribution;
import com.intellij.execution.wsl.WslDistributionManager;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.rt.execution.junit.FileComparisonFailure;
import com.intellij.testFramework.*;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.indices.MavenIndicesManager;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.server.MavenServerConnector;
import org.jetbrains.idea.maven.server.MavenServerConnectorImpl;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.jetbrains.idea.maven.server.RemotePathTransformerFactory;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;

public abstract class MavenTestCase extends UsefulTestCase {
  protected static final String MAVEN_COMPILER_PROPERTIES = """
    <properties>
            <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
            <maven.compiler.source>1.7</maven.compiler.source>
            <maven.compiler.target>1.7</maven.compiler.target>
    </properties>
    """;
  protected static final MavenConsole NULL_MAVEN_CONSOLE = new NullMavenConsole();
  private MavenProgressIndicator myProgressIndicator;
  private MavenEmbeddersManager myEmbeddersManager;
  private WSLDistribution myWSLDistribution;
  protected RemotePathTransformerFactory.Transformer myPathTransformer;

  private File ourTempDir;

  protected IdeaProjectTestFixture myTestFixture;

  @NotNull
  protected Project myProject;

  protected File myDir;
  protected VirtualFile myProjectRoot;

  protected VirtualFile myProjectPom;
  protected List<VirtualFile> myAllPoms = new ArrayList<>();


  @Override
  protected void setUp() throws Exception {
    super.setUp();

    setUpFixtures();
    myProject = myTestFixture.getProject();
    myPathTransformer = RemotePathTransformerFactory.createForProject(myProject);
    setupWsl();
    ensureTempDirCreated();

    myDir = new File(ourTempDir, getTestName(false));
    FileUtil.ensureExists(myDir);


    myProgressIndicator = new MavenProgressIndicator(myProject, new EmptyProgressIndicator(ModalityState.NON_MODAL), null);

    MavenWorkspaceSettingsComponent.getInstance(myProject).loadState(new MavenWorkspaceSettings());

    String home = getTestMavenHome();
    if (home != null) {
      getMavenGeneralSettings().setMavenHome(home);
    }

    getMavenGeneralSettings().setAlwaysUpdateSnapshots(true);

    MavenUtil.cleanAllRunnables();

    EdtTestUtil.runInEdtAndWait(() -> {
      restoreSettingsFile();

      try {
        WriteAction.run(this::setUpInWriteAction);
      }
      catch (Throwable e) {
        try {
          tearDown();
        }
        catch (Exception e1) {
          e1.printStackTrace();
        }
        throw new RuntimeException(e);
      }
    });
  }

  private void setupWsl() {
    String wslMsId = System.getProperty("wsl.distribution.name");
    if (wslMsId == null) return;
    List<WSLDistribution> distributions = WslDistributionManager.getInstance().getInstalledDistributions();
    if (distributions.isEmpty()) throw new IllegalStateException("no WSL distributions configured!");
    myWSLDistribution = distributions.stream().filter(it -> wslMsId.equals(it.getMsId())).findFirst()
      .orElseThrow(() -> new IllegalStateException("Distribution " + wslMsId + " was not found"));
    String jdkPath = System.getProperty("wsl.jdk.path");
    if (jdkPath == null) {
      jdkPath = "/usr/lib/jvm/java-11-openjdk-amd64";
    }

    Sdk wslSdk = getWslSdk(myWSLDistribution.getWindowsPath(jdkPath));
    WriteAction.runAndWait(() -> ProjectRootManagerEx.getInstanceEx(myProject).setProjectSdk(wslSdk));
    assertTrue(new File(myWSLDistribution.getWindowsPath(myWSLDistribution.getUserHome())).isDirectory());
  }

  protected void waitForMavenUtilRunnablesComplete() {
    PlatformTestUtil.waitWithEventsDispatching(() -> "Waiting for MavenUtils runnables completed" + MavenUtil.getUncompletedRunnables(),
                                               () -> MavenUtil.noUncompletedRunnables(), 15);
  }

  @Override
  protected void runBare(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    LoggedErrorProcessor.executeWith(new LoggedErrorProcessor() {
      @Override
      public @NotNull Set<Action> processError(@NotNull String category, @NotNull String message, String @NotNull [] details, @Nullable Throwable t) {
        boolean intercept = t != null && (
          StringUtil.notNullize(t.getMessage()).contains("The network name cannot be found") && message.contains("Couldn't read shelf information") ||
          "JDK annotations not found".equals(t.getMessage()) && "#com.intellij.openapi.projectRoots.impl.JavaSdkImpl".equals(category));
        return intercept ? Action.NONE : Action.ALL;
      }
    }, () -> super.runBare(testRunnable));
  }

  private Sdk getWslSdk(String jdkPath) {
    Sdk sdk = ContainerUtil.find(ProjectJdkTable.getInstance().getAllJdks(), it -> jdkPath.equals(it.getHomePath()));
    ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();
    for (Sdk existingSdk : jdkTable.getAllJdks()) {
      if (existingSdk == sdk) return sdk;
    }
    Sdk newSdk = JavaSdk.getInstance().createJdk("Wsl JDK For Tests", jdkPath);
    WriteAction.runAndWait(() -> jdkTable.addJdk(newSdk, myProject));
    return newSdk;
  }


  @Override
  protected void tearDown() throws Exception {
    String basePath = myProject.getBasePath();
    new RunAll(
      () -> {
        MavenProgressIndicator.MavenProgressTracker mavenProgressTracker =
          myProject.getServiceIfCreated(MavenProgressIndicator.MavenProgressTracker.class);
        if (mavenProgressTracker != null) {
          mavenProgressTracker.assertProgressTasksCompleted();
        }
      },
      () -> MavenServerManager.getInstance().shutdown(true),
      () -> tearDownEmbedders(),
      () -> checkAllMavenConnectorsDisposed(),
      () -> MavenArtifactDownloader.awaitQuiescence(100, TimeUnit.SECONDS),
      () -> myProject = null,
      () -> {
        Project defaultProject = ProjectManager.getInstance().getDefaultProject();
        MavenIndicesManager mavenIndicesManager = defaultProject.getServiceIfCreated(MavenIndicesManager.class);
        if (mavenIndicesManager != null) {
          Disposer.dispose(mavenIndicesManager);
        }
      },
      () -> EdtTestUtil.runInEdtAndWait(() -> tearDownFixtures()),
      () -> deleteDirOnTearDown(myDir),
      () -> {
        if (myWSLDistribution != null) {
          deleteDirOnTearDown(new File(basePath));
        }
      },
      () -> super.tearDown()
    ).run();
  }

  private void tearDownEmbedders() {
    MavenProjectsManager manager = MavenProjectsManager.getInstanceIfCreated(myProject);
    if (manager == null) return;
    manager.getEmbeddersManager().releaseInTests();
  }


  private void checkAllMavenConnectorsDisposed() {
    assertEmpty("all maven connectors should be disposed", MavenServerManager.getInstance().getAllConnectors());
  }

  private void ensureTempDirCreated() throws IOException {
    if (ourTempDir != null) return;

    if (myWSLDistribution == null) {
      ourTempDir = new File(FileUtil.getTempDirectory(), "mavenTests");
    }
    else {
      ourTempDir = new File(myWSLDistribution.getWindowsPath("/tmp"), "mavenTests");
    }

    FileUtil.delete(ourTempDir);
    FileUtil.ensureExists(ourTempDir);
  }

  protected void setUpFixtures() throws Exception {
    String wslMsId = System.getProperty("wsl.distribution.name");

    boolean isDirectoryBasedProject = useDirectoryBasedProjectFormat();
    if (wslMsId != null) {
      Path path = TemporaryDirectory
        .generateTemporaryPath(FileUtil.sanitizeFileName(getName(), false), Paths.get("\\\\wsl$\\" + wslMsId + "\\tmp"));
      myTestFixture =
        IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName(), path, isDirectoryBasedProject).getFixture();
    }
    else {
      myTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName(), isDirectoryBasedProject).getFixture();
    }

    myTestFixture.setUp();
  }

  protected boolean useDirectoryBasedProjectFormat() {
    return false;
  }

  protected void setUpInWriteAction() throws Exception {
    File projectDir = new File(myDir, "project");
    projectDir.mkdirs();
    myProjectRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(projectDir);
  }

  protected MavenProgressIndicator getMavenProgressIndicator() {
    return myProgressIndicator;
  }

  protected static void deleteDirOnTearDown(File dir) {
    FileUtil.delete(dir);
    // cannot use reliably the result of the com.intellij.openapi.util.io.FileUtil.delete() method
    // because com.intellij.openapi.util.io.FileUtilRt.deleteRecursivelyNIO() does not honor this contract
    if (dir.exists()) {
      System.err.println("Cannot delete " + dir);
      //printDirectoryContent(myDir);
      dir.deleteOnExit();
    }
  }

  private static void printDirectoryContent(File dir) {
    File[] files = dir.listFiles();
    if (files == null) return;

    for (File file : files) {
      System.out.println(file.getAbsolutePath());

      if (file.isDirectory()) {
        printDirectoryContent(file);
      }
    }
  }

  protected void tearDownFixtures() throws Exception {
    try {
      myTestFixture.tearDown();
    }
    finally {
      myTestFixture = null;
    }
  }

  @Override
  protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    try {
      super.runTestRunnable(testRunnable);
    }
    catch (Throwable throwable) {
      if (ExceptionUtil.causedBy(throwable, HeadlessException.class)) {
        printIgnoredMessage("Doesn't work in Headless environment");
      }
      throw throwable;
    }
  }

  protected static String getRoot() {
    if (SystemInfo.isWindows) return "c:";
    return "";
  }

  protected static String getEnvVar() {
    if (SystemInfo.isWindows) {
      return "TEMP";
    }
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
    getMavenGeneralSettings().setLocalRepository(path);
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

  protected static String pathFromBasedir(VirtualFile root, String relPath) {
    return FileUtil.toSystemIndependentName(root.getPath() + "/" + relPath);
  }

  protected VirtualFile updateSettingsXml(String content) throws IOException {
    return updateSettingsXmlFully(createSettingsXmlContent(content));
  }

  protected VirtualFile updateSettingsXmlFully(@NonNls @Language("XML") String content) throws IOException {
    File ioFile = new File(myDir, "settings.xml");
    ioFile.createNewFile();
    VirtualFile f = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile);
    setFileContent(f, content, true);
    getMavenGeneralSettings().setUserSettingsFile(f.getPath());
    return f;
  }

  protected void deleteSettingsXml() throws IOException {
    WriteCommandAction.writeCommandAction(myProject).run(() -> {
      VirtualFile f = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myDir, "settings.xml"));
      if (f != null) f.delete(this);
    });
  }

  private static String createSettingsXmlContent(String content) {
    return "<settings>" +
           content +
           "</settings>\r\n";
  }

  protected void restoreSettingsFile() throws IOException {
    updateSettingsXml("");
  }

  protected Module createModule(String name) {
    return createModule(name, StdModuleTypes.JAVA);
  }

  protected Module createModule(final String name, final ModuleType type) {
    try {
      return WriteCommandAction.writeCommandAction(myProject).compute(() -> {
        VirtualFile f = createProjectSubFile(name + "/" + name + ".iml");
        Module module = ModuleManager.getInstance(myProject).newModule(f.getPath(), type.getId());
        PsiTestUtil.addContentRoot(module, f.getParent());
        return module;
      });
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected VirtualFile createProjectPom(@NotNull @Language(value = "XML", prefix = "<project>", suffix = "</project>") String xml) {
    return myProjectPom = createPomFile(myProjectRoot, xml);
  }

  protected VirtualFile createModulePom(String relativePath,
                                        @Language(value = "XML", prefix = "<project>", suffix = "</project>") String xml) {
    return createPomFile(createProjectSubDir(relativePath), xml);
  }

  protected VirtualFile createPomFile(final VirtualFile dir,
                                      @Language(value = "XML", prefix = "<project>", suffix = "</project>") String xml) {
    VirtualFile f = dir.findChild("pom.xml");
    if (f == null) {
      try {
        f = WriteAction.computeAndWait(() -> {
          VirtualFile res = dir.createChildData(null, "pom.xml");
          return res;
        });
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      myAllPoms.add(f);
    }
    setPomContent(f, xml);
    return f;
  }

  @NonNls
  @Language(value = "XML")
  public static String createPomXml(@NonNls @Language(value = "XML", prefix = "<project>", suffix = "</project>") String xml) {
    return "<?xml version=\"1.0\"?>" +
           "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"" +
           "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" +
           "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">" +
           "  <modelVersion>4.0.0</modelVersion>" +
           xml +
           "</project>";
  }

  protected VirtualFile createProfilesXmlOldStyle(String xml) {
    return createProfilesFile(myProjectRoot, xml, true);
  }

  protected VirtualFile createProfilesXmlOldStyle(String relativePath, String xml) {
    return createProfilesFile(createProjectSubDir(relativePath), xml, true);
  }

  protected VirtualFile createProfilesXml(String xml) {
    return createProfilesFile(myProjectRoot, xml, false);
  }

  protected VirtualFile createProfilesXml(String relativePath, String xml) {
    return createProfilesFile(createProjectSubDir(relativePath), xml, false);
  }

  private static VirtualFile createProfilesFile(VirtualFile dir, String xml, boolean oldStyle) {
    return createProfilesFile(dir, createValidProfiles(xml, oldStyle));
  }

  protected VirtualFile createFullProfilesXml(String content) {
    return createProfilesFile(myProjectRoot, content);
  }

  protected VirtualFile createFullProfilesXml(String relativePath, String content) {
    return createProfilesFile(createProjectSubDir(relativePath), content);
  }

  private static VirtualFile createProfilesFile(final VirtualFile dir, String content) {
    VirtualFile f = dir.findChild("profiles.xml");
    if (f == null) {
      try {
        f = WriteAction.computeAndWait(() -> {
          VirtualFile res = dir.createChildData(null, "profiles.xml");
          return res;
        });
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    setFileContent(f, content, true);
    return f;
  }

  @Language("XML")
  private static String createValidProfiles(String xml, boolean oldStyle) {
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
    WriteCommandAction.writeCommandAction(myProject).run(() -> {
      VirtualFile f = myProjectRoot.findChild("profiles.xml");
      if (f != null) f.delete(this);
    });
  }

  protected void createStdProjectFolders() {
    createStdProjectFolders("");
  }

  protected void createStdProjectFolders(String subdir) {
    if (!subdir.isEmpty()) subdir += "/";
    createProjectSubDirs(subdir + "src/main/java",
                         subdir + "src/main/resources",
                         subdir + "src/test/java",
                         subdir + "src/test/resources");
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
    setFileContent(file, content, false);
    return file;
  }

  protected static void setPomContent(VirtualFile file, @Language(value = "XML", prefix = "<project>", suffix = "</project>") String xml) {
    setFileContent(file, createPomXml(xml), true);
  }

  private static void setFileContent(final VirtualFile file, final String content, final boolean advanceStamps) {
    try {
      WriteAction.runAndWait(() -> {
        if (advanceStamps) {
          file.setBinaryContent(content.getBytes(StandardCharsets.UTF_8), -1, file.getTimeStamp() + 4000);
        }
        else {
          file.setBinaryContent(content.getBytes(StandardCharsets.UTF_8), file.getModificationStamp(), file.getTimeStamp());
        }
      });
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected static <T, U> void assertOrderedElementsAreEqual(Collection<U> actual, Collection<T> expected) {
    assertOrderedElementsAreEqual(actual, expected.toArray());
  }

  protected static <T> void assertUnorderedElementsAreEqual(@NotNull Collection<T> actual, @NotNull Collection<T> expected) {
    assertSameElements(actual, expected);
  }

  protected static void assertUnorderedPathsAreEqual(Collection<String> actual, Collection<String> expected) {
    assertEquals(new SetWithToString<>(CollectionFactory.createFilePathSet(expected)),
                 new SetWithToString<>(CollectionFactory.createFilePathSet(actual)));
  }

  protected static <T> void assertUnorderedElementsAreEqual(T[] actual, T... expected) {
    assertUnorderedElementsAreEqual(Arrays.asList(actual), expected);
  }

  protected static <T> void assertUnorderedElementsAreEqual(Collection<T> actual, T... expected) {
    assertUnorderedElementsAreEqual(actual, Arrays.asList(expected));
  }

  protected static <T, U> void assertOrderedElementsAreEqual(Collection<U> actual, T... expected) {
    String s = "\nexpected: " + Arrays.asList(expected) + "\nactual: " + new ArrayList<>(actual);
    assertEquals(s, expected.length, actual.size());

    List<U> actualList = new ArrayList<>(actual);
    for (int i = 0; i < expected.length; i++) {
      T expectedElement = expected[i];
      U actualElement = actualList.get(i);
      assertEquals(s, expectedElement, actualElement);
    }
  }

  protected static <T> void assertContain(Collection<? extends T> actual, T... expected) {
    List<T> expectedList = Arrays.asList(expected);
    assertTrue("expected: " + expectedList + "\n" + "actual: " + actual.toString(), actual.containsAll(expectedList));
  }

  protected static <T> void assertDoNotContain(List<T> actual, T... expected) {
    List<T> actualCopy = new ArrayList<>(actual);
    actualCopy.removeAll(Arrays.asList(expected));
    assertEquals(actual.toString(), actualCopy.size(), actual.size());
  }

  protected static void assertUnorderedLinesWithFile(String filePath, String expectedText) {
    try {
      assertSameLinesWithFile(filePath, expectedText);
    }
    catch (FileComparisonFailure e) {
      String expected = e.getExpected();
      String actual = e.getActual();
      assertUnorderedElementsAreEqual(expected.split("\n"), actual.split("\n"));
    }
  }

  protected boolean ignore() {
    //printIgnoredMessage(null);
    return false;
  }

  protected boolean hasMavenInstallation() {
    boolean result = getTestMavenHome() != null;
    if (!result) printIgnoredMessage("Maven installation not found");
    return result;
  }

  protected static MavenServerConnector ensureConnected(MavenServerConnector connector) {
    assertTrue("Connector is Dummy!", connector instanceof MavenServerConnectorImpl);
    long timeout = TimeUnit.SECONDS.toMillis(10);
    long start = System.currentTimeMillis();
    while (connector.getState() == MavenServerConnectorImpl.State.STARTING) {
      if (System.currentTimeMillis() > start + timeout) {
        throw new RuntimeException("Server connector not connected in 10 seconds");
      }
      EdtTestUtil.runInEdtAndWait(() -> {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
      });
    }
    assertTrue(connector.checkConnected());
    return connector;
  }

  private void printIgnoredMessage(String message) {
    String toPrint = "Ignored";
    if (message != null) {
      toPrint += ", because " + message;
    }
    toPrint += ": " + getClass().getSimpleName() + "." + getName();
    System.out.println(toPrint);
  }

  protected <R, E extends Throwable> R runWriteAction(@NotNull ThrowableComputable<R, E> computable) throws E {
    return WriteCommandAction.writeCommandAction(myProject).compute(computable);
  }

  protected <E extends Throwable> void runWriteAction(@NotNull ThrowableRunnable<E> runnable) throws E {
    WriteCommandAction.writeCommandAction(myProject).run(runnable);
  }

  private static String getTestMavenHome() {
    return System.getProperty("idea.maven.test.home");
  }

  protected DataContext createTestDataContext(VirtualFile pomFile) {
    final DataContext defaultContext = DataManager.getInstance().getDataContext();
    return dataId -> {
      if (CommonDataKeys.PROJECT.is(dataId)) {
        return myProject;
      }
      if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
        return new VirtualFile[]{pomFile};
      }
      return defaultContext.getData(dataId);
    };
  }

  private static class SetWithToString<T> extends AbstractSet<T> {

    private final Set<T> myDelegate;

    SetWithToString(@NotNull Set<T> delegate) {
      myDelegate = delegate;
    }

    @Override
    public int size() {
      return myDelegate.size();
    }

    @Override
    public boolean contains(Object o) {
      return myDelegate.contains(o);
    }

    @NotNull
    @Override
    public Iterator<T> iterator() {
      return myDelegate.iterator();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
      return myDelegate.containsAll(c);
    }

    @Override
    public boolean equals(Object o) {
      return myDelegate.equals(o);
    }

    @Override
    public int hashCode() {
      return myDelegate.hashCode();
    }
  }
}
