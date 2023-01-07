// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing;

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper;
import org.jetbrains.idea.maven.importing.workspaceModel.WorkspaceProjectImporterKt;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public class MiscImportingTest extends MavenMultiVersionImportingTestCase {
  private MavenEventsTestHelper myEventsTestHelper = new MavenEventsTestHelper();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myEventsTestHelper.setUp(myProject);
  }

  @Override
  protected void tearDown() throws Exception {
    myEventsTestHelper.tearDown();
    super.tearDown();
  }

  @Test
  public void testRestarting() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<name>1</name>");

    assertModules("project");
    assertEquals("1", getProjectsTree().getRootProjects().get(0).getName());

    MavenServerManager.getInstance().shutdown(true);

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<name>2</name>");

    assertModules("project");
    assertEquals("2", getProjectsTree().getRootProjects().get(0).getName());
  }

  @Test
  public void testFallbackToSlowWorkspaceCommit() {
    Assume.assumeTrue(isWorkspaceImport());

    try {
      WorkspaceProjectImporterKt.setWORKSPACE_IMPORTER_SKIP_FAST_APPLY_ATTEMPTS_ONCE(true);
      importProject("<groupId>test</groupId>" +
                    "<artifactId>project</artifactId>" +
                    "<version>1</version>" +
                    "<name>1</name>");

      assertModules("project");

      // make sure the logic in WorkspaceProjectImporter worked as expected
      assertFalse(WorkspaceProjectImporterKt.getWORKSPACE_IMPORTER_SKIP_FAST_APPLY_ATTEMPTS_ONCE());
    }
    finally {
      WorkspaceProjectImporterKt.setWORKSPACE_IMPORTER_SKIP_FAST_APPLY_ATTEMPTS_ONCE(false);
    }
  }

  @Test
  public void testDoNotFailOnInvalidMirrors() throws Exception {
    updateSettingsXmlFully("<settings>" +
                           "<mirrors>" +
                           "  <mirror>" +
                           "  </mirror>" +
                           "  <mirror>" +
                           "    <id/>" +
                           "    <url/>" +
                           "    <mirrorOf/>" +
                           "  </mirror>" +
                           "  <mirror>" +
                           "    <id/>" +
                           "    <url>foo</url>" +
                           "    <mirrorOf>*</mirrorOf>" +
                           "  </mirror>" +
                           "  <mirror>" +
                           "    <id>foo</id>" +
                           "    <url/>" +
                           "    <mirrorOf>*</mirrorOf>" +
                           "  </mirror>" +
                           "</mirrors>" +
                           "</settings>");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertModules("project");
  }

  @Test
  public void testImportingAllAvailableFilesIfNotInitialized() {
    createModule("m1");
    createModule("m2");
    createProjectSubDirs("m1/src/main/java",
                         "m2/src/main/java");

    createModulePom("m1",
                    "<groupId>test</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>1</version>");

    createModulePom("m2",
                    "<groupId>test</groupId>" +
                    "<artifactId>m2</artifactId>" +
                    "<version>1</version>");

    assertSources("m1");
    assertSources("m2");

    assertFalse(myProjectsManager.isMavenizedProject());
    myProjectsManager.forceUpdateAllProjectsOrFindAllAvailablePomFiles();
    waitForReadingCompletion();
    resolveDependenciesAndImport();

    assertSources("m1", "src/main/java");
    assertSources("m2", "src/main/java");
  }

  @Test
  public void testImportingFiresRootChangesOnlyOnce() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    myEventsTestHelper.assertRootsChanged(1);
    myEventsTestHelper.assertWorkspaceModelChanges(isWorkspaceImport() ? 1 : 2);
  }

  @Test
  public void testDoRootChangesOnProjectReimportWhenNothingChanges() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "  </dependency>" +
                  "</dependencies>");

    myEventsTestHelper.assertRootsChanged(1);
    myEventsTestHelper.assertWorkspaceModelChanges(isWorkspaceImport() ? 1 : 2);

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "  </dependency>" +
                  "</dependencies>");

    myEventsTestHelper.assertRootsChanged(isWorkspaceImport() ? 0 : 1);
    myEventsTestHelper.assertWorkspaceModelChanges(isWorkspaceImport() ? 0 : 1);
  }

  @Test
  public void testResolvingFiresRootChangesOnlyOnce() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    myEventsTestHelper.assertRootsChanged(1);
    myEventsTestHelper.assertWorkspaceModelChanges(isWorkspaceImport() ? 1 : 2);
  }

  @Test
  public void testDoNotRecreateModulesBeforeResolution() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    Module m = getModule("project");
    resolveDependenciesAndImport();

    assertSame(m, getModule("project"));
  }

  @Test
  public void testTakingProxySettingsIntoAccount() throws Exception {
    MavenCustomRepositoryHelper helper = new MavenCustomRepositoryHelper(myDir, "local1");
    setRepositoryPath(helper.getTestDataPath("local1"));

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "  </dependency>" +
                  "</dependencies>");

    removeFromLocalRepository("junit");

    scheduleResolveAll(); // force resolving
    resolveDependenciesAndImport();

    File jarFile = new File(getRepositoryFile(), "junit/junit/4.0/junit-4.0.jar");
    assertTrue(jarFile.exists());

    myProjectsManager.listenForExternalChanges();

    updateSettingsXml("<proxies>" +
                      " <proxy>" +
                      "    <id>my</id>" +
                      "    <active>true</active>" +
                      "    <protocol>http</protocol>" +
                      "    <host>invalid.host.in.intellij.net</host>" +
                      "    <port>3128</port>" +
                      "  </proxy>" +
                      "</proxies>");

    removeFromLocalRepository("junit");
    assertFalse(jarFile.exists());

    try {
      scheduleResolveAll(); // force resolving
      resolveDependenciesAndImport();
    }
    finally {
      // LightweightHttpWagon does not clear settings if they were not set before a proxy was configured.
      System.clearProperty("http.proxyHost");
      System.clearProperty("http.proxyPort");
    }
    assertFalse(jarFile.exists());

    restoreSettingsFile();

    scheduleResolveAll(); // force resolving
    resolveDependenciesAndImport();
    assertTrue(jarFile.exists());
  }

  @Test
  public void testMavenExtensionsAreLoadedAndAfterProjectsReadIsCalled() throws Exception {
    try {
      MavenCustomRepositoryHelper helper = new MavenCustomRepositoryHelper(myDir, "plugins");
      setRepositoryPath(helper.getTestDataPath("plugins"));
      getMavenGeneralSettings().setWorkOffline(true);

      importProject("<groupId>test</groupId>" +
                    "<artifactId>project</artifactId>" +
                    "<version>1</version>" +
                    "" +
                    "<build>" +
                    "  <extensions>" +
                    "    <extension>" +
                    "      <groupId>intellij.test</groupId>" +
                    "      <artifactId>maven-extension</artifactId>" +
                    "      <version>1.0</version>" +
                    "    </extension>" +
                    "  </extensions>" +
                    "</build>");

      List<MavenProject> projects = getProjectsTree().getProjects();
      assertEquals(1, projects.size());
      MavenProject mavenProject = projects.get(0);
      assertEquals("Name for test:project generated by MyMavenExtension.", mavenProject.getFinalName());

      PlatformTestUtil.assertPathsEqual(myProjectPom.getPath(), mavenProject.getProperties().getProperty("workspace-info"));
    }
    finally {
      MavenServerManager.getInstance().shutdown(true);  // to unlock files
    }
  }

  @Test
  public void testExceptionsFromMavenExtensionsAreReportedAsProblems() throws Exception {
    MavenCustomRepositoryHelper helper = new MavenCustomRepositoryHelper(myDir, "plugins");
    setRepositoryPath(helper.getTestDataPath("plugins"));
    getMavenGeneralSettings().setWorkOffline(true);

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<description>throw!</description>" +
                     "" +
                     "<build>" +
                     "  <extensions>" +
                     "    <extension>" +
                     "      <groupId>intellij.test</groupId>" +
                     "      <artifactId>maven-extension</artifactId>" +
                     "      <version>1.0</version>" +
                     "    </extension>" +
                     "  </extensions>" +
                     "</build>");
    importProjectWithErrors();

    List<MavenProject> projects = getProjectsTree().getProjects();
    assertEquals(1, projects.size());
    MavenProject mavenProject = projects.get(0);
    assertEquals(mavenProject.getProblems().toString(), 1, mavenProject.getProblems().size());
    assertEquals("throw!", mavenProject.getProblems().get(0).getDescription());
  }

  @Test
  public void testCheckingIfModuleIsNotDisposedBeforeCommitOnImport() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<packaging>pom</packaging>" +

                  "<modules>" +
                  "  <module>m1</module>" +
                  "  <module>m2</module>" +
                  "</modules>");

    createModulePom("m1",
                    "<groupId>test</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>1</version>");

    createModulePom("m2",
                    "<groupId>test</groupId>" +
                    "<artifactId>m2</artifactId>" +
                    "<version>1</version>");

    importProject();
    assertModules("project", "m1", "m2");

    myProjectsManager.scheduleImportInTests(myProjectsManager.getProjectsFiles());
    myProjectsManager.importProjects(new IdeModifiableModelsProviderImpl(myProject) {
      @Override
      public void commit() {
        ModifiableModuleModel model = ModuleManager.getInstance(myProject).getModifiableModel();
        model.disposeModule(model.findModuleByName("m1"));
        model.disposeModule(model.findModuleByName("m2"));
        model.commit();
        super.commit();
      }
    });
  }

  @Test
  public void testUserPropertiesCanBeCustomizedByMavenImporters() {
    Disposable disposable = Disposer.newDisposable();
    try {
      ExtensionTestUtil.maskExtensions(MavenImporter.EXTENSION_POINT_NAME,
                                       Collections.<MavenImporter>singletonList(new NameSettingMavenImporter("name-from-properties")),
                                       disposable);
      importProject("<groupId>test</groupId>" +
                    "<artifactId>project</artifactId>" +
                    "<version>1</version>" +
                    "<name>${myName}</name>");
    }
    finally {
      Disposer.dispose(disposable);
    }

    MavenProject project = myProjectsManager.findProject(new MavenId("test", "project", "1"));
    assertNotNull(project);
    assertEquals("name-from-properties", project.getName());
  }

  private static class NameSettingMavenImporter extends MavenImporter {
    private final String myName;

    NameSettingMavenImporter(String name) {
      super("gid", "id");
      myName = name;
    }

    @Override
    public void customizeUserProperties(Project project, MavenProject mavenProject, Properties properties) {
      properties.setProperty("myName", myName);
    }

    @Override
    public boolean isApplicable(MavenProject mavenProject) {
      return true;
    }
  }
}
