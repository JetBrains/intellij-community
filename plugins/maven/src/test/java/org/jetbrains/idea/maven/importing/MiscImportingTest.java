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
package org.jetbrains.idea.maven.importing;

import com.intellij.ProjectTopics;
import com.intellij.idea.Bombed;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.model.MavenProjectProblem;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.jetbrains.idea.maven.server.MavenServerManagerHelper;

import java.io.File;
import java.util.Calendar;
import java.util.List;

public class MiscImportingTest extends MavenImportingTestCase {
  private int beforeRootsChangedCount;
  private int rootsChangedCount;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myProject.getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void beforeRootsChange(ModuleRootEvent event) {
        beforeRootsChangedCount++;
      }

      @Override
      public void rootsChanged(ModuleRootEvent event) {
        rootsChangedCount++;
      }
    });
  }

  public void testRestarting() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<name>1</name>");

    assertModules("project");
    assertEquals("1", myProjectsTree.getRootProjects().get(0).getName());

    MavenServerManager.getInstance().shutdown(true);

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<name>2</name>");

    assertModules("project");
    assertEquals("2", myProjectsTree.getRootProjects().get(0).getName());
  }

  public void testDoNotFailOnInvalidMirrors() throws Exception {
    updateSettingsXmlFully("<settings>" +
                           "<mirrors>" +
                           "  <mirror>" +
                           "  </mirror>" +
                           "  <mirror>" +
                           "    <id></id>" +
                           "    <url></url>" +
                           "    <mirrorOf></mirrorOf>" +
                           "  </mirror>" +
                           "  <mirror>" +
                           "    <id></id>" +
                           "    <url>foo</url>" +
                           "    <mirrorOf>*</mirrorOf>" +
                           "  </mirror>" +
                           "  <mirror>" +
                           "    <id>foo</id>" +
                           "    <url></url>" +
                           "    <mirrorOf>*</mirrorOf>" +
                           "  </mirror>" +
                           "</mirrors>" +
                           "</settings>");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertModules("project");
  }

  public void testImportingAllAvailableFilesIfNotInitialized() throws Exception {
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
    myProjectsManager.performScheduledImportInTests();

    assertSources("m1", "src/main/java");
    assertSources("m2", "src/main/java");
  }

  public void testImportingFiresRootChangesOnlyOnce() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertRootsChanged(1);
  }

  public void testResolvingFiresRootChangesOnlyOnce() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertRootsChanged(1);
  }

  public void testImportingWithLibrariesAndFacetsFiresRootChangesOnlyOnce() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<packaging>war</packaging>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "  </dependency>" +
                  "  <dependency>" +
                  "    <groupId>jmock</groupId>" +
                  "    <artifactId>jmock</artifactId>" +
                  "    <version>1.0.0</version>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertRootsChanged(1);
  }

  public void testFacetsDoNotFireRootsChanges() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<packaging>war</packaging>");

    assertRootsChanged(1);
  }

  public void testDoNotRecreateModulesBeforeResolution() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    Module m = getModule("project");
    resolveDependenciesAndImport();

    assertSame(m, getModule("project"));
  }

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

    // valid password is 'fg3W9' (see http://www.jetbrains.net/confluence/display/JBINT/HTTP+Proxy+with+authorization)
    updateSettingsXml("<proxies>" +
                      " <proxy>" +
                      "    <id>my</id>" +
                      "    <active>true</active>" +
                      "    <protocol>http</protocol>" +
                      "    <host>proxy-auth-test.labs.intellij.net</host>" +
                      "    <port>3128</port>" +
                      "    <username>user1</username>" +
                      "    <password>invalid</password>" +
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

  public void testClearUnresolvedPluginsAfterPluginResolution() throws Exception {
    File repo = new File(myDir, "repo");
    setRepositoryPath(repo.getPath());

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "" +
                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <artifactId>maven-surefire-plugin</artifactId>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    List<MavenProjectProblem> problems = myProjectsTree.getRootProjects().get(0).getProblems();
    assertTrue(problems.size() > 0);

    for (MavenProjectProblem problem : problems) {
      assertTrue(problem.getDescription(), problem.getDescription().contains("Unresolved plugin"));
    }

    resolvePlugins();

    assertEquals(0, myProjectsTree.getRootProjects().get(0).getProblems().size());
  }

  @Bombed(year = 2014, month = Calendar.JUNE, day = 25, user = "sergey.evdokimov")
  public void testMavenExtensionsAreLoadedAndAfterProjectsReadIsCalled() throws Exception {
    MavenCustomRepositoryHelper helper = new MavenCustomRepositoryHelper(myDir, "plugins");
    setRepositoryPath(helper.getTestDataPath("plugins"));
    getMavenGeneralSettings().setWorkOffline(true);
    MavenServerManagerHelper.connectToMaven3Server();

    importProjectWithMaven3("<groupId>test</groupId>" +
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

    List<MavenProject> projects = myProjectsTree.getProjects();
    assertEquals(1, projects.size());
    MavenProject mavenProject = projects.get(0);
    assertEquals("Name for test:project generated by MyMavenExtension.", mavenProject.getFinalName());

    assertEquals(myProjectPom.getPath().replace('\\', '/'), mavenProject.getProperties().getProperty("workspace-info"));
  }

  @Bombed(year = 2014, month = Calendar.JUNE, day = 25, user = "sergey.evdokimov")
  public void testExceptionsFromMavenExtensionsAreReportedAsProblems() throws Exception {
    MavenCustomRepositoryHelper helper = new MavenCustomRepositoryHelper(myDir, "plugins");
    setRepositoryPath(helper.getTestDataPath("plugins"));
    getMavenGeneralSettings().setWorkOffline(true);

    importProjectWithMaven3("<groupId>test</groupId>" +
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

    List<MavenProject> projects = myProjectsTree.getProjects();
    assertEquals(1, projects.size());
    MavenProject mavenProject = projects.get(0);
    assertEquals(mavenProject.getProblems().toString(), 1, mavenProject.getProblems().size());
    assertEquals("throw!", mavenProject.getProblems().get(0).getDescription());
  }

  public void testCheckingIfModuleIsNotDisposedBeforeCommitOnImport() throws Exception {
    if (ignore()) return;

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

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
    myProjectsManager.importProjects(new MavenDefaultModifiableModelsProvider(myProject) {
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

  private void assertRootsChanged(int count) {
    assertEquals(count, rootsChangedCount);
    assertEquals(rootsChangedCount, beforeRootsChangedCount);
  }

  private void importProjectWithMaven3(String xml) throws Exception {
    MavenServerManagerHelper.connectToMaven3Server();
    try {
      importProject(xml);
    }
    finally {
      MavenServerManagerHelper.disconnectFromServer();
    }
  }
}
