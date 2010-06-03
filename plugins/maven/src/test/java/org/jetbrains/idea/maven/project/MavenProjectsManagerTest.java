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
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.facade.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MavenProjectsManagerTest extends MavenImportingTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initProjectsManager(true);
  }

  public void testShouldReturnNullForUnprocessedFiles() throws Exception {
    // this pom file doesn't belong to any of the modules, this is won't be processed
    // by MavenProjectProjectsManager and won't occur in its projects list.
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    // shouldn't throw
    assertNull(myProjectsManager.findProject(myProjectPom));
  }

  public void testUpdatingProjectsWhenAbsentManagedProjectFileAppears() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>parent</artifactId>" +
                  "<version>1</version>" +

                  "<modules>" +
                  "  <module>m</module>" +
                  "</modules>");

    assertEquals(1, myProjectsTree.getRootProjects().size());

    myProjectPom.delete(this);
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

  public void testUpdatingProjectsWhenRenaming() throws Exception {
    VirtualFile p1 = createModulePom("project1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>project1</artifactId>" +
                                     "<version>1</version>");

    VirtualFile p2 = createModulePom("project2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>project2</artifactId>" +
                                     "<version>1</version>");
    importProjects(p1, p2);

    assertEquals(2, myProjectsTree.getRootProjects().size());

    p2.rename(this, "foo.bar");
    waitForReadingCompletion();

    assertEquals(1, myProjectsTree.getRootProjects().size());

    p2.rename(this, "pom.xml");
    waitForReadingCompletion();

    assertEquals(2, myProjectsTree.getRootProjects().size());
  }

  public void testUpdatingProjectsWhenMoving() throws Exception {
    VirtualFile p1 = createModulePom("project1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>project1</artifactId>" +
                                     "<version>1</version>");

    VirtualFile p2 = createModulePom("project2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>project2</artifactId>" +
                                     "<version>1</version>");
    importProjects(p1, p2);

    VirtualFile oldDir = p2.getParent();
    VirtualFile newDir = myProjectRoot.createChildDirectory(this, "foo");

    assertEquals(2, myProjectsTree.getRootProjects().size());

    p2.move(this, newDir);
    waitForReadingCompletion();

    assertEquals(1, myProjectsTree.getRootProjects().size());

    p2.move(this, oldDir);
    waitForReadingCompletion();

    assertEquals(2, myProjectsTree.getRootProjects().size());
  }

  public void testUpdatingProjectsWhenMovingModuleFile() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    VirtualFile m = createModulePom("m1",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>m</artifactId>" +
                                    "<version>1</version>");
    importProject();

    VirtualFile oldDir = m.getParent();
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
    waitForReadingCompletion();

    assertEquals(0, myProjectsTree.getModules(myProjectsTree.getRootProjects().get(0)).size());
  }

  public void testUpdatingProjectsWhenAbsentModuleFileAppears() throws Exception {
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
    assertSame(m, children.get(0).getFile());
  }

  public void testAddingAndRemovingManagedFiles() throws Exception {
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

  public void testAddingAndRemovingManagedFilesAddsAndRemovesModules() throws Exception {
    doTestAddingAndRemovingAddsAndRemovesModules(true);
  }

  public void testAddingAndRemovingManagedFilesAddsAndRemovesModulesInNonAutoImportMode() throws Exception {
    doTestAddingAndRemovingAddsAndRemovesModules(false);
  }

  private void doTestAddingAndRemovingAddsAndRemovesModules(boolean autoImport) throws IOException {
    VirtualFile m1 = createModulePom("m1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m1</artifactId>" +
                                     "<version>1</version>");

    VirtualFile m2 = createModulePom("m2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m2</artifactId>" +
                                     "<version>1</version>");
    importProject(m1);
    assertModules("m1");

    myProjectsManager.performScheduledImport(); // ensure no pending imports

    getMavenImporterSettings().setImportAutomatically(autoImport);

    myProjectsManager.addManagedFiles(Arrays.asList(m2));
    waitForReadingCompletion();
    myProjectsManager.performScheduledImport(false);

    assertModules("m1", "m2");

    configConfirmationForYesAnswer();

    myProjectsManager.removeManagedFiles(Arrays.asList(m2));
    waitForReadingCompletion();
    myProjectsManager.performScheduledImport(false);

    assertModules("m1");
  }

  public void testAddingManagedFileAndChangingAggregation() throws Exception {
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

    assertUnorderedElementsAreEqual(parentNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/value1"));
    assertUnorderedElementsAreEqual(childNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/m/value1"));

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

    assertUnorderedElementsAreEqual(parentNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/value2"));
    assertUnorderedElementsAreEqual(childNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/m/value2"));

    deleteSettingsXml();
    waitForReadingCompletion();

    assertUnorderedElementsAreEqual(parentNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/${prop}"));
    assertUnorderedElementsAreEqual(childNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/m/${prop}"));

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

    assertUnorderedElementsAreEqual(parentNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/value2"));
    assertUnorderedElementsAreEqual(childNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/m/value2"));
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

    assertUnorderedElementsAreEqual(parentNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/value1"));
    assertUnorderedElementsAreEqual(childNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/m/value1"));

    getMavenGeneralSettings().setMavenSettingsFile("");
    waitForReadingCompletion();

    assertUnorderedElementsAreEqual(parentNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/${prop}"));
    assertUnorderedElementsAreEqual(childNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/m/${prop}"));

    getMavenGeneralSettings().setMavenSettingsFile(new File(myDir, "settings.xml").getPath());
    waitForReadingCompletion();

    assertUnorderedElementsAreEqual(parentNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/value1"));
    assertUnorderedElementsAreEqual(childNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/m/value1"));
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

  public void testUpdatingProjectsOnProfilesXmlChange() throws Exception {
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

    assertUnorderedElementsAreEqual(parentNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/value1"));
    assertUnorderedElementsAreEqual(childNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/m/value1"));

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

    assertUnorderedElementsAreEqual(parentNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/value2"));
    assertUnorderedElementsAreEqual(childNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/m/value2"));

    deleteProfilesXml();
    waitForReadingCompletion();

    assertUnorderedElementsAreEqual(parentNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/${prop}"));
    assertUnorderedElementsAreEqual(childNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/m/${prop}"));

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

    assertUnorderedElementsAreEqual(parentNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/value2"));
    assertUnorderedElementsAreEqual(childNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/m/value2"));
  }

  public void testHandlingDirectoryWithPomFileDeletion() throws Exception {
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

    VirtualFile dir = myProjectRoot.findChild("dir");
    dir.delete(null);
    waitForReadingCompletion();

    assertEquals(1, MavenProjectsManager.getInstance(myProject).getProjects().size());
  }

  public void testSavingAndLoadingState() throws Exception {
    MavenProjectsManagerState state = myProjectsManager.getState();
    assertTrue(state.originalFiles.isEmpty());
    assertTrue(state.activeProfiles.isEmpty());
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
    myProjectsManager.setExplicitProfiles(Arrays.asList("one", "two"));
    myProjectsManager.setIgnoredFilesPaths(Arrays.asList(p1.getPath()));
    myProjectsManager.setIgnoredFilesPatterns(Arrays.asList("*.xxx"));

    state = myProjectsManager.getState();
    assertUnorderedElementsAreEqual(state.originalFiles, p1.getPath(), p2.getPath());
    assertUnorderedElementsAreEqual(state.activeProfiles, "one", "two");
    assertUnorderedElementsAreEqual(state.ignoredFiles, p1.getPath());
    assertUnorderedElementsAreEqual(state.ignoredPathMasks, "*.xxx");

    MavenProjectsManagerState newState = new MavenProjectsManagerState();

    newState.originalFiles = Arrays.asList(p1.getPath(), p3.getPath());
    newState.activeProfiles = Arrays.asList("three");
    newState.ignoredFiles = Collections.singleton(p1.getPath());
    newState.ignoredPathMasks = Arrays.asList("*.zzz");

    myProjectsManager.loadState(newState);

    assertUnorderedElementsAreEqual(myProjectsManager.getProjectsTreeForTests().getManagedFilesPaths(),
                                    p1.getPath(), p3.getPath());
    assertUnorderedElementsAreEqual(myProjectsManager.getExplicitProfiles(), "three");
    assertUnorderedElementsAreEqual(myProjectsManager.getIgnoredFilesPaths(), p1.getPath());
    assertUnorderedElementsAreEqual(myProjectsManager.getIgnoredFilesPatterns(), "*.zzz");

    waitForReadingCompletion();
    assertUnorderedElementsAreEqual(myProjectsManager.getProjectsTreeForTests().getRootProjectsFiles(),
                                    p1, p3);
  }

  public void testSchedulingReimportWhenPomFileIsDeleted() throws Exception {
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
    myProjectsManager.performScheduledImport(); // ensure no pending requests
    assertModules("project", "m");

    configConfirmationForYesAnswer();
    m.delete(this);
    waitForReadingCompletion();

    myProjectsManager.performScheduledImport();
    assertModules("project");
  }

  public void testSchedulingResolveOfDependentProjectWhenDependencyChanges() throws Exception {
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

  public void testSchedulingResolveOfDependentProjectWhenDependencyIsDeleted() throws Exception {
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

    VirtualFile m2 = createModulePom("m2", "<groupId>test</groupId>" +
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

    m2.delete(this);

    configConfirmationForYesAnswer();// should update deps even if module is not removed

    waitForReadingCompletion();
    resolveDependenciesAndImport();

    assertModules("project", "m1");

    assertModuleModuleDeps("m1");
    assertModuleLibDeps("m1", "Maven: test:m2:1");
  }

  public void testDoNotScheduleResolveOfInvalidProjectsDeleted() throws Exception {
    final boolean[] called = new boolean[1];
    myProjectsManager.addProjectsTreeListener(new MavenProjectsTree.ListenerAdapter() {
      @Override
      public void projectResolved(Pair<MavenProject, MavenProjectChanges> projectWithChanges,
                                  NativeMavenProjectHolder nativeMavenProject, Object message) {
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

  public void testUpdatingFoldersAfterFoldersResolving() throws Exception {
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
    assertSources("project", "src/main/java", "src/main/resources");

    resolveFoldersAndImport();

    assertSources("project", "src/main/java", "src/main/resources", "src1", "src2");
  }

  public void testForceReimport() throws Exception {
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

    ModifiableRootModel model = ModuleRootManager.getInstance(getModule("project")).getModifiableModel();
    for (OrderEntry each : model.getOrderEntries()) {
      if (each instanceof LibraryOrderEntry && MavenRootModelAdapter.isMavenLibrary(((LibraryOrderEntry)each).getLibrary())) {
        model.removeOrderEntry(each);
      }
    }
    model.commit();

    assertSources("project");
    assertModuleLibDeps("project");

    myProjectsManager.forceUpdateAllProjectsOrFindAllAvailablePomFiles();
    waitForReadingCompletion();
    myProjectsManager.waitForResolvingCompletion();
    myProjectsManager.performScheduledImport();

    assertSources("project", "src/main/java");
    assertModuleLibDeps("project", "Maven: junit:junit:4.0");
  }

  public void testScheduleReimportWhenPluginConfigurationChangesInTagName() throws Exception {
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

    myProjectsManager.performScheduledImport();
    assertFalse(myProjectsManager.hasScheduledImports());

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

    assertTrue(myProjectsManager.hasScheduledImports());
  }

  public void testScheduleReimportWhenPluginConfigurationChangesInValue() throws Exception {
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

    myProjectsManager.performScheduledImport();
    assertFalse(myProjectsManager.hasScheduledImports());

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

    assertTrue(myProjectsManager.hasScheduledImports());
  }
}
