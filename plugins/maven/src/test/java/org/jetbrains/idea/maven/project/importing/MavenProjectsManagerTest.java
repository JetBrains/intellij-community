/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.project.importing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.FileContentUtil;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MavenProjectsManagerTest extends MavenImportingTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initProjectsManager(true);
    getMavenImporterSettings().setImportAutomatically(true);
  }

  public void testShouldReturnNullForUnprocessedFiles() {
    // this pom file doesn't belong to any of the modules, this is won't be processed
    // by MavenProjectProjectsManager and won't occur in its projects list.
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    // shouldn't throw
    assertNull(myProjectsManager.findProject(myProjectPom));
  }

  public void testUpdatingProjectsWhenAbsentManagedProjectFileAppears() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>parent</artifactId>" +
                  "<version>1</version>" +

                  "<modules>" +
                  "  <module>m</module>" +
                  "</modules>");

    assertEquals(1, myProjectsTree.getRootProjects().size());

    new WriteCommandAction.Simple(myProject) {
      @Override
      protected void run() throws Throwable {
        myProjectPom.delete(this);
      }
    }.execute().throwException();

    waitForReadingCompletion();

    assertEquals(0, myProjectsTree.getRootProjects().size());

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");
    waitForReadingCompletion();

    assertEquals(1, myProjectsTree.getRootProjects().size());
  }

  public void testUpdatingProjectsWhenRenaming() {
    VirtualFile p1 = createModulePom("project1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>project1</artifactId>" +
                                     "<version>1</version>");

    final VirtualFile p2 = createModulePom("project2",
                                           "<groupId>test</groupId>" +
                                           "<artifactId>project2</artifactId>" +
                                           "<version>1</version>");
    importProjects(p1, p2);

    assertEquals(2, myProjectsTree.getRootProjects().size());

    new WriteCommandAction.Simple(myProject) {
      @Override
      protected void run() throws Throwable {
        p2.rename(this, "foo.bar");
        waitForReadingCompletion();

        assertEquals(1, myProjectsTree.getRootProjects().size());

        p2.rename(this, "pom.xml");
      }
    }.execute().throwException();

    waitForReadingCompletion();

    assertEquals(2, myProjectsTree.getRootProjects().size());
  }

  public void testUpdatingProjectsWhenMoving() {
    VirtualFile p1 = createModulePom("project1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>project1</artifactId>" +
                                     "<version>1</version>");

    final VirtualFile p2 = createModulePom("project2",
                                           "<groupId>test</groupId>" +
                                           "<artifactId>project2</artifactId>" +
                                           "<version>1</version>");
    importProjects(p1, p2);

    final VirtualFile oldDir = p2.getParent();
    new WriteCommandAction.Simple(myProject) {
      @Override
      protected void run() throws Throwable {
        VirtualFile newDir = myProjectRoot.createChildDirectory(this, "foo");

        assertEquals(2, myProjectsTree.getRootProjects().size());

        p2.move(this, newDir);
        waitForReadingCompletion();

        assertEquals(1, myProjectsTree.getRootProjects().size());

        p2.move(this, oldDir);
      }
    }.execute().throwException();

    waitForReadingCompletion();

    assertEquals(2, myProjectsTree.getRootProjects().size());
  }

  public void testUpdatingProjectsWhenMovingModuleFile() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    final VirtualFile m = createModulePom("m1",
                                          "<groupId>test</groupId>" +
                                          "<artifactId>m</artifactId>" +
                                          "<version>1</version>");
    importProject();

    final VirtualFile oldDir = m.getParent();
    new WriteCommandAction.Simple(myProject) {
      @Override
      protected void run() throws Throwable {
        VirtualFile newDir = myProjectRoot.createChildDirectory(this, "m2");

        assertEquals(1, myProjectsTree.getRootProjects().size());
        assertEquals(1, myProjectsTree.getModules(myProjectsTree.getRootProjects().get(0)).size());

        m.move(this, newDir);
        waitForReadingCompletion();

        assertEquals(1, myProjectsTree.getModules(myProjectsTree.getRootProjects().get(0)).size());

        m.move(this, oldDir);
        waitForReadingCompletion();

        assertEquals(1, myProjectsTree.getModules(myProjectsTree.getRootProjects().get(0)).size());

        m.move(this, myProjectRoot.createChildDirectory(this, "xxx"));
      }
    }.execute().throwException();

    waitForReadingCompletion();

    assertEquals(0, myProjectsTree.getModules(myProjectsTree.getRootProjects().get(0)).size());
  }

  public void testUpdatingProjectsWhenAbsentModuleFileAppears() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>parent</artifactId>" +
                  "<version>1</version>" +

                  "<modules>" +
                  "  <module>m</module>" +
                  "</modules>");

    List<MavenProject> roots = myProjectsTree.getRootProjects();
    MavenProject parentNode = roots.get(0);

    assertNotNull(parentNode);
    assertTrue(myProjectsTree.getModules(roots.get(0)).isEmpty());

    VirtualFile m = createModulePom("m",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>m</artifactId>" +
                                    "<version>1</version>");
    waitForReadingCompletion();

    List<MavenProject> children = myProjectsTree.getModules(roots.get(0));
    assertEquals(1, children.size());
    assertEquals(m, children.get(0).getFile());
  }

  public void testAddingAndRemovingManagedFiles() {
    VirtualFile m1 = createModulePom("m1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m1</artifactId>" +
                                     "<version>1</version>");

    VirtualFile m2 = createModulePom("m2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m2</artifactId>" +
                                     "<version>1</version>");
    importProject(m1);

    assertUnorderedElementsAreEqual(myProjectsTree.getRootProjectsFiles(), m1);

    myProjectsManager.addManagedFiles(Arrays.asList(m2));
    waitForReadingCompletion();

    assertUnorderedElementsAreEqual(myProjectsTree.getRootProjectsFiles(), m1, m2);

    myProjectsManager.removeManagedFiles(Arrays.asList(m2));
    waitForReadingCompletion();
    assertUnorderedElementsAreEqual(myProjectsTree.getRootProjectsFiles(), m1);
  }

  public void testAddingAndRemovingManagedFilesAddsAndRemovesModules() {
    doTestAddingAndRemovingAddsAndRemovesModules(true);
  }

  public void testAddingAndRemovingManagedFilesAddsAndRemovesModulesInNonAutoImportMode() {
    doTestAddingAndRemovingAddsAndRemovesModules(false);
  }

  private void doTestAddingAndRemovingAddsAndRemovesModules(boolean autoImport) {
    VirtualFile m1 = createModulePom("m1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m1</artifactId>" +
                                     "<version>1</version>");

    final VirtualFile m2 = createModulePom("m2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m2</artifactId>" +
                                     "<version>1</version>");
    importProject(m1);
    assertModules("m1");

    resolveDependenciesAndImport(); // ensure no pending imports

    getMavenImporterSettings().setImportAutomatically(autoImport);

    myProjectsManager.addManagedFiles(Collections.singletonList(m2));
    waitForReadingCompletion();
    resolveDependenciesAndImport();

    assertModules("m1", "m2");

    configConfirmationForYesAnswer();

    myProjectsManager.removeManagedFiles(Collections.singletonList(m2));
    waitForReadingCompletion();
    resolveDependenciesAndImport();

    assertModules("m1");
  }

  public void testAddingManagedFileAndChangingAggregation() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>parent</artifactId>" +
                  "<version>1</version>" +

                  "<modules>" +
                  "  <module>m</module>" +
                  "</modules>");

    VirtualFile m = createModulePom("m",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>m</artifactId>" +
                                    "<version>1</version>");
    waitForReadingCompletion();

    assertEquals(1, myProjectsTree.getRootProjects().size());
    assertEquals(1, myProjectsTree.getModules(myProjectsTree.getRootProjects().get(0)).size());

    myProjectsManager.addManagedFiles(Arrays.asList(m));
    waitForReadingCompletion();

    assertEquals(1, myProjectsTree.getRootProjects().size());
    assertEquals(1, myProjectsTree.getModules(myProjectsTree.getRootProjects().get(0)).size());

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>");
    waitForReadingCompletion();

    assertEquals(2, myProjectsTree.getRootProjects().size());
    assertEquals(0, myProjectsTree.getModules(myProjectsTree.getRootProjects().get(0)).size());
    assertEquals(0, myProjectsTree.getModules(myProjectsTree.getRootProjects().get(1)).size());
  }

  public void testUpdatingProjectsOnSettingsXmlChange() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>" +

                     "<build>" +
                     "  <sourceDirectory>${prop}</sourceDirectory>" +
                     "</build>");

    createModulePom("m",
                    "<groupId>test</groupId>" +
                    "<artifactId>m</artifactId>" +
                    "<version>1</version>" +

                    "<parent>" +
                    "  <groupId>test</groupId>" +
                    "  <artifactId>project</artifactId>" +
                    "  <version>1</version>" +
                    "</parent>" +

                    "<build>" +
                    "  <sourceDirectory>${prop}</sourceDirectory>" +
                    "</build>");

    updateSettingsXml("<profiles>" +
                      "  <profile>" +
                      "    <id>one</id>" +
                      "    <activation>" +
                      "      <activeByDefault>true</activeByDefault>" +
                      "    </activation>" +
                      "    <properties>" +
                      "      <prop>value1</prop>" +
                      "    </properties>" +
                      "  </profile>" +
                      "</profiles>");

    importProject();

    List<MavenProject> roots = myProjectsTree.getRootProjects();

    MavenProject parentNode = roots.get(0);
    MavenProject childNode = myProjectsTree.getModules(roots.get(0)).get(0);

    assertUnorderedPathsAreEqual(parentNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/value1")));
    assertUnorderedPathsAreEqual(childNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/m/value1")));

    updateSettingsXml("<profiles>" +
                      "  <profile>" +
                      "    <id>one</id>" +
                      "    <activation>" +
                      "      <activeByDefault>true</activeByDefault>" +
                      "    </activation>" +
                      "    <properties>" +
                      "      <prop>value2</prop>" +
                      "    </properties>" +
                      "  </profile>" +
                      "</profiles>");
    waitForReadingCompletion();

    assertUnorderedPathsAreEqual(parentNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/value2")));
    assertUnorderedPathsAreEqual(childNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/m/value2")));

    deleteSettingsXml();
    waitForReadingCompletion();

    assertUnorderedPathsAreEqual(parentNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/${prop}")));
    assertUnorderedPathsAreEqual(childNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/m/${prop}")));

    updateSettingsXml("<profiles>" +
                      "  <profile>" +
                      "    <id>one</id>" +
                      "    <activation>" +
                      "      <activeByDefault>true</activeByDefault>" +
                      "    </activation>" +
                      "    <properties>" +
                      "      <prop>value2</prop>" +
                      "    </properties>" +
                      "  </profile>" +
                      "</profiles>");
    waitForReadingCompletion();

    assertUnorderedPathsAreEqual(parentNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/value2")));
    assertUnorderedPathsAreEqual(childNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/m/value2")));
  }

  public void testUpdatingProjectsWhenSettingsXmlLocationIsChanged() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>" +

                     "<build>" +
                     "  <sourceDirectory>${prop}</sourceDirectory>" +
                     "</build>");

    createModulePom("m",
                    "<groupId>test</groupId>" +
                    "<artifactId>m</artifactId>" +
                    "<version>1</version>" +

                    "<parent>" +
                    "  <groupId>test</groupId>" +
                    "  <artifactId>project</artifactId>" +
                    "  <version>1</version>" +
                    "</parent>" +

                    "<build>" +
                    "  <sourceDirectory>${prop}</sourceDirectory>" +
                    "</build>");

    updateSettingsXml("<profiles>" +
                      "  <profile>" +
                      "    <id>one</id>" +
                      "    <activation>" +
                      "      <activeByDefault>true</activeByDefault>" +
                      "    </activation>" +
                      "    <properties>" +
                      "      <prop>value1</prop>" +
                      "    </properties>" +
                      "  </profile>" +
                      "</profiles>");

    importProject();

    List<MavenProject> roots = myProjectsTree.getRootProjects();

    MavenProject parentNode = roots.get(0);
    MavenProject childNode = myProjectsTree.getModules(roots.get(0)).get(0);

    assertUnorderedPathsAreEqual(parentNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/value1")));
    assertUnorderedPathsAreEqual(childNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/m/value1")));

    getMavenGeneralSettings().setUserSettingsFile("");
    waitForReadingCompletion();

    assertUnorderedPathsAreEqual(parentNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/${prop}")));
    assertUnorderedPathsAreEqual(childNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/m/${prop}")));

    getMavenGeneralSettings().setUserSettingsFile(new File(myDir, "settings.xml").getPath());
    waitForReadingCompletion();

    assertUnorderedPathsAreEqual(parentNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/value1")));
    assertUnorderedPathsAreEqual(childNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/m/value1")));
  }

  public void testUpdatingProjectsOnSettingsXmlCreationAndDeletion() throws Exception {
    deleteSettingsXml();
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    importProject();
    assertUnorderedElementsAreEqual(myProjectsTree.getAvailableProfiles());

    updateSettingsXml("<profiles>" +
                      "  <profile>" +
                      "    <id>one</id>" +
                      "  </profile>" +
                      "</profiles>");
    waitForReadingCompletion();
    assertUnorderedElementsAreEqual(myProjectsTree.getAvailableProfiles(), "one");

    deleteSettingsXml();
    waitForReadingCompletion();
    assertUnorderedElementsAreEqual(myProjectsTree.getAvailableProfiles());
  }

  public void testUpdatingMavenPathsWhenSettingsChanges() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    File repo1 = new File(myDir, "localRepo1");
    updateSettingsXml("<localRepository>" + repo1.getPath() + "</localRepository>");

    waitForReadingCompletion();
    assertEquals(repo1, getMavenGeneralSettings().getEffectiveLocalRepository());

    File repo2 = new File(myDir, "localRepo2");
    updateSettingsXml("<localRepository>" + repo2.getPath() + "</localRepository>");

    waitForReadingCompletion();
    assertEquals(repo2, getMavenGeneralSettings().getEffectiveLocalRepository());
  }

  public void testResolvingEnvVariableInRepositoryPath() throws Exception {
    String temp = System.getenv(getEnvVar());
    updateSettingsXml("<localRepository>${env." + getEnvVar() + "}/tmpRepo</localRepository>");

    File repo = new File(temp + "/tmpRepo").getCanonicalFile();
    assertEquals(repo.getPath(), getMavenGeneralSettings().getEffectiveLocalRepository().getPath());

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

    assertModuleLibDep("project", "Maven: junit:junit:4.0",
                       "jar://" + FileUtil.toSystemIndependentName(repo.getPath()) + "/junit/junit/4.0/junit-4.0.jar!/");
  }

  public void testUpdatingProjectsOnProfilesXmlChange() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>" +

                     "<build>" +
                     "  <sourceDirectory>${prop}</sourceDirectory>" +
                     "</build>");

    createModulePom("m",
                    "<groupId>test</groupId>" +
                    "<artifactId>m</artifactId>" +
                    "<version>1</version>" +

                    "<parent>" +
                    "  <groupId>test</groupId>" +
                    "  <artifactId>project</artifactId>" +
                    "  <version>1</version>" +
                    "</parent>" +

                    "<build>" +
                    "  <sourceDirectory>${prop}</sourceDirectory>" +
                    "</build>");

    createProfilesXmlOldStyle("<profile>" +
                              "  <id>one</id>" +
                              "  <activation>" +
                              "    <activeByDefault>true</activeByDefault>" +
                              "  </activation>" +
                              "  <properties>" +
                              "    <prop>value1</prop>" +
                              "  </properties>" +
                              "</profile>");

    importProject();

    List<MavenProject> roots = myProjectsTree.getRootProjects();

    MavenProject parentNode = roots.get(0);
    MavenProject childNode = myProjectsTree.getModules(roots.get(0)).get(0);

    assertUnorderedPathsAreEqual(parentNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/value1")));
    assertUnorderedPathsAreEqual(childNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/m/value1")));

    createProfilesXmlOldStyle("<profile>" +
                              "  <id>one</id>" +
                              "  <activation>" +
                              "    <activeByDefault>true</activeByDefault>" +
                              "  </activation>" +
                              "  <properties>" +
                              "    <prop>value2</prop>" +
                              "  </properties>" +
                              "</profile>");
    waitForReadingCompletion();

    assertUnorderedPathsAreEqual(parentNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/value2")));
    assertUnorderedPathsAreEqual(childNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/m/value2")));

    deleteProfilesXml();
    waitForReadingCompletion();

    assertUnorderedPathsAreEqual(parentNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/${prop}")));
    assertUnorderedPathsAreEqual(childNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/m/${prop}")));

    createProfilesXmlOldStyle("<profile>" +
                              "  <id>one</id>" +
                              "  <activation>" +
                              "    <activeByDefault>true</activeByDefault>" +
                              "  </activation>" +
                              "  <properties>" +
                              "    <prop>value2</prop>" +
                              "  </properties>" +
                              "</profile>");
    waitForReadingCompletion();

    assertUnorderedPathsAreEqual(parentNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/value2")));
    assertUnorderedPathsAreEqual(childNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/m/value2")));
  }

  public void testHandlingDirectoryWithPomFileDeletion() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<packaging>pom</packaging>" +
                  "<version>1</version>");

    createModulePom("dir/module", "<groupId>test</groupId>" +
                                  "<artifactId>module</artifactId>" +
                                  "<version>1</version>");
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>dir/module</module>" +
                     "</modules>");
    waitForReadingCompletion();

    assertEquals(2, MavenProjectsManager.getInstance(myProject).getProjects().size());

    final VirtualFile dir = myProjectRoot.findChild("dir");
    new WriteCommandAction.Simple(myProject) {
      @Override
      protected void run() throws Throwable {
        dir.delete(null);
      }
    }.execute().throwException();

    waitForReadingCompletion();

    assertEquals(1, MavenProjectsManager.getInstance(myProject).getProjects().size());
  }

  public void testSavingAndLoadingState() {
    MavenProjectsManagerState state = myProjectsManager.getState();
    assertTrue(state.originalFiles.isEmpty());
    assertTrue(MavenWorkspaceSettingsComponent.getInstance(myProject).getSettings().enabledProfiles.isEmpty());
    assertTrue(state.ignoredFiles.isEmpty());
    assertTrue(state.ignoredPathMasks.isEmpty());

    VirtualFile p1 = createModulePom("project1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>project1</artifactId>" +
                                     "<version>1</version>");

    VirtualFile p2 = createModulePom("project2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>project2</artifactId>" +
                                     "<version>1</version>" +
                                     "<packaging>pom</packaging>" +

                                     "<modules>" +
                                     "  <module>../project3</module>" +
                                     "</modules>");

    VirtualFile p3 = createModulePom("project3",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>project3</artifactId>" +
                                     "<version>1</version>");

    importProjects(p1, p2);
    myProjectsManager.setExplicitProfiles(new MavenExplicitProfiles(Arrays.asList("one", "two")));
    myProjectsManager.setIgnoredFilesPaths(Arrays.asList(p1.getPath()));
    myProjectsManager.setIgnoredFilesPatterns(Arrays.asList("*.xxx"));

    state = myProjectsManager.getState();
    assertUnorderedPathsAreEqual(state.originalFiles, Arrays.asList(p1.getPath(), p2.getPath()));
    assertUnorderedElementsAreEqual(MavenWorkspaceSettingsComponent.getInstance(myProject).getState().enabledProfiles, "one", "two");
    assertUnorderedPathsAreEqual(state.ignoredFiles, Arrays.asList(p1.getPath()));
    assertUnorderedElementsAreEqual(state.ignoredPathMasks, "*.xxx");

    MavenProjectsManagerState newState = new MavenProjectsManagerState();

    newState.originalFiles = Arrays.asList(p1.getPath(), p3.getPath());
    MavenWorkspaceSettingsComponent.getInstance(myProject).getSettings().setEnabledProfiles(Arrays.asList("three"));
    newState.ignoredFiles = Collections.singleton(p1.getPath());
    newState.ignoredPathMasks = Arrays.asList("*.zzz");

    myProjectsManager.loadState(newState);

    assertUnorderedPathsAreEqual(myProjectsManager.getProjectsTreeForTests().getManagedFilesPaths(),
                                 Arrays.asList(p1.getPath(), p3.getPath()));
    assertUnorderedElementsAreEqual(myProjectsManager.getExplicitProfiles().getEnabledProfiles(), "three");
    assertUnorderedPathsAreEqual(myProjectsManager.getIgnoredFilesPaths(), Arrays.asList(p1.getPath()));
    assertUnorderedElementsAreEqual(myProjectsManager.getIgnoredFilesPatterns(), "*.zzz");

    waitForReadingCompletion();
    assertUnorderedElementsAreEqual(myProjectsManager.getProjectsTreeForTests().getRootProjectsFiles(),
                                    p1, p3);
  }

  public void testSchedulingReimportWhenPomFileIsDeleted() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    final VirtualFile m = createModulePom("m",
                                          "<groupId>test</groupId>" +
                                          "<artifactId>m</artifactId>" +
                                          "<version>1</version>");
    importProject();
    myProjectsManager.performScheduledImportInTests(); // ensure no pending requests
    assertModules("project", "m");

    configConfirmationForYesAnswer();
    new WriteCommandAction.Simple(myProject) {
      @Override
      protected void run() throws Throwable {
        m.delete(this);
      }
    }.execute().throwException();

    waitForReadingCompletion();

    resolveDependenciesAndImport();
    assertModules("project");
  }

  public void testSchedulingResolveOfDependentProjectWhenDependencyChanges() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m2</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>");

    importProject();

    assertModuleModuleDeps("m1", "m2");
    assertModuleLibDeps("m1");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>junit</groupId>" +
                          "    <artifactId>junit</artifactId>" +
                          "    <version>4.0</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    waitForReadingCompletion();
    resolveDependenciesAndImport();

    assertModuleModuleDeps("m1", "m2");
    assertModuleLibDeps("m1", "Maven: junit:junit:4.0");
  }

  public void testSchedulingResolveOfDependentProjectWhenDependencyIsDeleted() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m2</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    final VirtualFile m2 = createModulePom("m2", "<groupId>test</groupId>" +
                                                 "<artifactId>m2</artifactId>" +
                                                 "<version>1</version>" +

                                                 "<dependencies>" +
                                                 "  <dependency>" +
                                                 "    <groupId>junit</groupId>" +
                                                 "    <artifactId>junit</artifactId>" +
                                                 "    <version>4.0</version>" +
                                                 "  </dependency>" +
                                                 "</dependencies>");

    importProject();

    assertModules("project", "m1", "m2");

    assertModuleModuleDeps("m1", "m2");
    assertModuleLibDeps("m1", "Maven: junit:junit:4.0");

    new WriteCommandAction.Simple(myProject) {
      @Override
      protected void run() throws Throwable {
        m2.delete(this);
      }
    }.execute().throwException();


    configConfirmationForYesAnswer();// should update deps even if module is not removed

    waitForReadingCompletion();
    resolveDependenciesAndImport();

    assertModules("project", "m1");

    assertModuleModuleDeps("m1");
    assertModuleLibDeps("m1", "Maven: test:m2:1");
  }

  public void testDoNotScheduleResolveOfInvalidProjectsDeleted() {
    final boolean[] called = new boolean[1];
    myProjectsManager.addProjectsTreeListener(new MavenProjectsTree.Listener() {
      @Override
      public void projectResolved(Pair<MavenProject, MavenProjectChanges> projectWithChanges,
                                  NativeMavenProjectHolder nativeMavenProject) {
        called[0] = true;
      }
    });

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1");
    importProject();
    assertModules("project");
    assertFalse(called[0]); // on import

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>2");

    waitForReadingCompletion();
    resolveDependenciesAndImport();

    assertFalse(called[0]); // on update
  }

  public void testUpdatingFoldersAfterFoldersResolving() {
    createStdProjectFolders();
    createProjectSubDirs("src1", "src2");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.codehaus.mojo</groupId>" +
                  "      <artifactId>build-helper-maven-plugin</artifactId>" +
                  "      <version>1.3</version>" +
                  "      <executions>" +
                  "        <execution>" +
                  "          <id>someId</id>" +
                  "          <phase>generate-sources</phase>" +
                  "          <goals>" +
                  "            <goal>add-source</goal>" +
                  "          </goals>" +
                  "          <configuration>" +
                  "            <sources>" +
                  "              <source>${basedir}/src1</source>" +
                  "              <source>${basedir}/src2</source>" +
                  "            </sources>" +
                  "          </configuration>" +
                  "        </execution>" +
                  "      </executions>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    assertSources("project", "src/main/java", "src1", "src2");
    assertResources("project", "src/main/resources");
  }

  public void testForceReimport() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>junit</groupId>" +
                     "    <artifactId>junit</artifactId>" +
                     "    <version>4.0</version>" +
                     "  </dependency>" +
                     "</dependencies>");
    importProject();
    assertModules("project");

    createProjectSubDir("src/main/java");

    ApplicationManager.getApplication().runWriteAction(() -> {
      ModifiableRootModel model = ModuleRootManager.getInstance(getModule("project")).getModifiableModel();
      for (OrderEntry each : model.getOrderEntries()) {
        if (each instanceof LibraryOrderEntry && MavenRootModelAdapter.isMavenLibrary(((LibraryOrderEntry)each).getLibrary())) {
          model.removeOrderEntry(each);
        }
      }
      model.commit();
    });


    assertSources("project");
    assertModuleLibDeps("project");

    myProjectsManager.forceUpdateAllProjectsOrFindAllAvailablePomFiles();
    waitForReadingCompletion();
    myProjectsManager.waitForResolvingCompletion();
    myProjectsManager.performScheduledImportInTests();

    assertSources("project", "src/main/java");
    assertModuleLibDeps("project", "Maven: junit:junit:4.0");
  }

  public void testScheduleReimportWhenPluginConfigurationChangesInTagName() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>group</groupId>" +
                  "      <artifactId>id</artifactId>" +
                  "      <version>1</version>" +
                  "      <configuration>" +
                  "        <foo>value</foo>" +
                  "      </configuration>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    myProjectsManager.performScheduledImportInTests();
    assertFalse(myProjectsManager.hasScheduledImportsInTests());

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <groupId>group</groupId>" +
                     "      <artifactId>id</artifactId>" +
                     "      <version>1</version>" +
                     "      <configuration>" +
                     "        <bar>value</bar>" +
                     "      </configuration>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");
    myProjectsManager.waitForResolvingCompletion();

    assertTrue(myProjectsManager.hasScheduledImportsInTests());
  }

  public void testScheduleReimportWhenPluginConfigurationChangesInValue() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>group</groupId>" +
                  "      <artifactId>id</artifactId>" +
                  "      <version>1</version>" +
                  "      <configuration>" +
                  "        <foo>value</foo>" +
                  "      </configuration>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    myProjectsManager.performScheduledImportInTests();
    assertFalse(myProjectsManager.hasScheduledImportsInTests());

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <groupId>group</groupId>" +
                     "      <artifactId>id</artifactId>" +
                     "      <version>1</version>" +
                     "      <configuration>" +
                     "        <foo>value2</foo>" +
                     "      </configuration>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");
    myProjectsManager.waitForResolvingCompletion();

    assertTrue(myProjectsManager.hasScheduledImportsInTests());
  }

  public void testIgnoringProjectsForDeletedModules() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    VirtualFile m = createModulePom("m",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>m</artifactId>" +
                                    "<version>1</version>");
    importProject();

    Module module = getModule("m");
    assertNotNull(module);
    assertFalse(myProjectsManager.isIgnored(myProjectsManager.findProject(m)));

    ModuleManager.getInstance(myProject).disposeModule(module);
    myProjectsManager.performScheduledImportInTests();

    assertNull(ModuleManager.getInstance(myProject).findModuleByName("m"));
    assertTrue(myProjectsManager.isIgnored(myProjectsManager.findProject(m)));
  }

  public void testDoNotRemoveMavenProjectsOnReparse() {
    // this pom file doesn't belong to any of the modules, this is won't be processed
    // by MavenProjectProjectsManager and won't occur in its projects list.
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    final StringBuilder log = new StringBuilder();
    myProjectsManager.performScheduledImportInTests();
    myProjectsManager.addProjectsTreeListener(new MavenProjectsTree.Listener() {
      @Override
      public void projectsUpdated(List<Pair<MavenProject, MavenProjectChanges>> updated, List<MavenProject> deleted) {
        for (Pair<MavenProject, MavenProjectChanges> each : updated) {
          log.append("updated: ").append(each.first.getDisplayName()).append(" ");
        }
        for (MavenProject each : deleted) {
          log.append("deleted: ").append(each.getDisplayName()).append(" ");
        }
      }
    });

    FileContentUtil.reparseFiles(myProject, myProjectsManager.getProjectsFiles(), true);
    myProjectsManager.waitForReadingCompletion();

    assertTrue(log.toString(), log.length() == 0);
  }
}
