// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.importing;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker;
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectNotificationAware;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleTypeId;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.util.FileContentUtil;
import org.jetbrains.annotations.NotNull;
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.project.actions.RemoveManagedFilesAction;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MavenProjectsManagerTest extends MavenMultiVersionImportingTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initProjectsManager(true);
  }

  @Test
  public void testShouldReturnNullForUnprocessedFiles() {
    // this pom file doesn't belong to any of the modules, this is won't be processed
    // by MavenProjectProjectsManager and won't occur in its projects list.
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    // shouldn't throw
    assertNull(myProjectsManager.findProject(myProjectPom));
  }

  @Test
  public void testShouldReturnNotNullForProcessedFiles() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");
    importProject();

    // shouldn't throw
    assertNotNull(myProjectsManager.findProject(myProjectPom));
  }

  @Test 
  public void testUpdatingProjectsWhenAbsentManagedProjectFileAppears() throws IOException {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>parent</artifactId>" +
                  "<version>1</version>" +
                  "<packaging>pom</packaging>" +

                  "<modules>" +
                  "  <module>m</module>" +
                  "</modules>");
    assertEquals(1, myProjectsTree.getRootProjects().size());

    WriteCommandAction.writeCommandAction(myProject).run(() -> myProjectPom.delete(this));

    configConfirmationForYesAnswer();
    scheduleProjectImportAndWait();

    assertEquals(0, myProjectsTree.getRootProjects().size());

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");
    scheduleProjectImportAndWait();

    assertEquals(1, myProjectsTree.getRootProjects().size());
  }

  @Test 
  public void testUpdatingProjectsWhenRenaming() throws IOException {
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

    runWriteAction(() -> p2.rename(this, "foo.bar"));
    configConfirmationForYesAnswer();
    scheduleProjectImportAndWaitWithoutCheckFloatingBar();
    assertEquals(1, myProjectsTree.getRootProjects().size());

    runWriteAction(() -> p2.rename(this, "pom.xml"));
    scheduleProjectImportAndWaitWithoutCheckFloatingBar();
    assertEquals(2, myProjectsTree.getRootProjects().size());
  }

  @Test 
  public void testUpdatingProjectsWhenMoving() throws IOException, InterruptedException {
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
    runWriteAction(() -> VfsUtil.markDirtyAndRefresh(false, true, true, myProjectRoot));
    VirtualFile newDir = runWriteAction(() -> myProjectRoot.createChildDirectory(this, "foo"));
    assertEquals(2, myProjectsTree.getRootProjects().size());

    runWriteAction(() -> p2.move(this, newDir));
    configConfirmationForYesAnswer();
    scheduleProjectImportAndWaitWithoutCheckFloatingBar();
    assertEquals(1, myProjectsTree.getRootProjects().size());

    runWriteAction(() -> p2.move(this, oldDir));
    scheduleProjectImportAndWaitWithoutCheckFloatingBar();
    assertEquals(2, myProjectsTree.getRootProjects().size());
  }

  @Test 
  public void testUpdatingProjectsWhenMovingModuleFile() throws IOException {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

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
    WriteCommandAction.writeCommandAction(myProject).run(() -> {
      VirtualFile newDir = myProjectRoot.createChildDirectory(this, "m2");

      assertEquals(1, myProjectsTree.getRootProjects().size());
      assertEquals(1, myProjectsTree.getModules(myProjectsTree.getRootProjects().get(0)).size());

      m.move(this, newDir);
      scheduleProjectImportAndWaitWithoutCheckFloatingBar();

      assertEquals(1, myProjectsTree.getModules(myProjectsTree.getRootProjects().get(0)).size());

      m.move(this, oldDir);
      scheduleProjectImportAndWaitWithoutCheckFloatingBar();

      assertEquals(1, myProjectsTree.getModules(myProjectsTree.getRootProjects().get(0)).size());

      m.move(this, myProjectRoot.createChildDirectory(this, "xxx"));
    });

    configConfirmationForYesAnswer();
    scheduleProjectImportAndWaitWithoutCheckFloatingBar();

    assertEquals(0, myProjectsTree.getModules(myProjectsTree.getRootProjects().get(0)).size());
  }

  @Test 
  public void testUpdatingProjectsWhenAbsentModuleFileAppears() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>parent</artifactId>" +
                  "<version>1</version>" +
                  "<packaging>pom</packaging>" +

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
    scheduleProjectImportAndWait();

    List<MavenProject> children = myProjectsTree.getModules(roots.get(0));
    assertEquals(1, children.size());
    assertEquals(m, children.get(0).getFile());
  }

  @Test 
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

  @Test 
  public void testAddingAndRemovingManagedFilesAddsAndRemovesModules() {
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

  @Test 
  public void testAddingManagedFileAndChangingAggregation() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>parent</artifactId>" +
                  "<version>1</version>" +
                  "<packaging>pom</packaging>" +

                  "<modules>" +
                  "  <module>m</module>" +
                  "</modules>");

    VirtualFile m = createModulePom("m",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>m</artifactId>" +
                                    "<version>1</version>");
    scheduleProjectImportAndWait();

    assertEquals(1, myProjectsTree.getRootProjects().size());
    assertEquals(1, myProjectsTree.getModules(myProjectsTree.getRootProjects().get(0)).size());

    myProjectsManager.addManagedFiles(Arrays.asList(m));
    waitForReadingCompletion();

    assertEquals(1, myProjectsTree.getRootProjects().size());
    assertEquals(1, myProjectsTree.getModules(myProjectsTree.getRootProjects().get(0)).size());

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +
                     "");
    scheduleProjectImportAndWait();

    assertEquals(2, myProjectsTree.getRootProjects().size());
    assertEquals(0, myProjectsTree.getModules(myProjectsTree.getRootProjects().get(0)).size());
    assertEquals(0, myProjectsTree.getModules(myProjectsTree.getRootProjects().get(1)).size());
  }

  @Test 
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

  @Test 
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

  @Test 
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

  @Test 
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

  @Test 
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

  @Test 
  public void testUpdatingProjectsOnProfilesXmlChange() throws IOException {
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
    importProject();

    assertUnorderedPathsAreEqual(parentNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/value2")));
    assertUnorderedPathsAreEqual(childNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/m/value2")));

    updateSettingsXml("<profiles/>");
    importProject();

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
    importProject();

    assertUnorderedPathsAreEqual(parentNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/value2")));
    assertUnorderedPathsAreEqual(childNode.getSources(), Arrays.asList(FileUtil.toSystemDependentName(getProjectPath() + "/m/value2")));
  }

  @Test 
  public void testHandlingDirectoryWithPomFileDeletion() throws IOException {
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
    scheduleProjectImportAndWait();

    assertEquals(2, MavenProjectsManager.getInstance(myProject).getProjects().size());

    final VirtualFile dir = myProjectRoot.findChild("dir");
    WriteCommandAction.writeCommandAction(myProject).run(() -> dir.delete(null));

    configConfirmationForYesAnswer();
    scheduleProjectImportAndWait();

    assertEquals(1, MavenProjectsManager.getInstance(myProject).getProjects().size());
  }

  @Test 
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
    setIgnoredFilesPathForNextImport(Arrays.asList(p1.getPath()));
    setIgnoredPathPatternsForNextImport(Arrays.asList("*.xxx"));

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

  @Test 
  public void testSchedulingReimportWhenPomFileIsDeleted() throws IOException {
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

    runWriteAction(() -> m.delete(this));

    configConfirmationForYesAnswer();
    scheduleProjectImportAndWait();
    assertModules("project");
  }

  @Test 
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

    scheduleProjectImportAndWait();

    assertModuleModuleDeps("m1", "m2");
    assertModuleLibDeps("m1", "Maven: junit:junit:4.0");
  }

  @Test 
  public void testSchedulingResolveOfDependentProjectWhenDependencyIsDeleted() throws IOException {
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

    WriteCommandAction.writeCommandAction(myProject).run(() -> m2.delete(this));


    configConfirmationForYesAnswer();// should update deps even if module is not removed
    scheduleProjectImportAndWait();

    assertModules("project", "m1");

    assertModuleModuleDeps("m1");
    assertModuleLibDeps("m1", "Maven: test:m2:1");
  }

  @Test 
  public void testDoNotScheduleResolveOfInvalidProjectsDeleted() {
    final boolean[] called = new boolean[1];
    myProjectsManager.addProjectsTreeListener(new MavenProjectsTree.Listener() {
      @Override
      public void projectResolved(@NotNull Pair<MavenProject, MavenProjectChanges> projectWithChanges,
                                  NativeMavenProjectHolder nativeMavenProject) {
        called[0] = true;
      }
    });

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1");
    importProjectWithErrors();
    assertModules("project");
    assertFalse(called[0]); // on import

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>2");

    waitForReadingCompletion();
    resolveDependenciesAndImport();

    assertFalse(called[0]); // on update
  }

  @Test 
  public void testUpdatingFoldersAfterFoldersResolving() {
    createStdProjectFolders();
    createProjectSubDirs("src1", "src2", "test1", "test2", "res1", "res2", "testres1", "testres2");

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
                  "          <id>someId1</id>" +
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
                  "        <execution>" +
                  "          <id>someId2</id>" +
                  "          <phase>generate-resources</phase>" +
                  "          <goals>" +
                  "            <goal>add-resource</goal>" +
                  "          </goals>" +
                  "          <configuration>" +
                  "            <resources>" +
                  "              <resource>" +
                  "                 <directory>${basedir}/res1</directory>" +
                  "              </resource>" +
                  "              <resource>" +
                  "                 <directory>${basedir}/res2</directory>" +
                  "              </resource>" +
                  "            </resources>" +
                  "          </configuration>" +
                  "        </execution>" +
                  "        <execution>" +
                  "          <id>someId3</id>" +
                  "          <phase>generate-test-sources</phase>" +
                  "          <goals>" +
                  "            <goal>add-test-source</goal>" +
                  "          </goals>" +
                  "          <configuration>" +
                  "            <sources>" +
                  "              <source>${basedir}/test1</source>" +
                  "              <source>${basedir}/test2</source>" +
                  "            </sources>" +
                  "          </configuration>" +
                  "        </execution>" +
                  "        <execution>" +
                  "          <id>someId4</id>" +
                  "          <phase>generate-test-resources</phase>" +
                  "          <goals>" +
                  "            <goal>add-test-resource</goal>" +
                  "          </goals>" +
                  "          <configuration>" +
                  "            <resources>" +
                  "              <resource>" +
                  "                 <directory>${basedir}/testres1</directory>" +
                  "              </resource>" +
                  "              <resource>" +
                  "                 <directory>${basedir}/testres2</directory>" +
                  "              </resource>" +
                  "            </resources>" +
                  "          </configuration>" +
                  "        </execution>" +
                  "      </executions>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    assertSources("project", "src/main/java", "src1", "src2");
    assertResources("project", "res1", "res2", "src/main/resources");

    assertTestSources("project", "src/test/java", "test1", "test2");
    assertTestResources("project", "src/test/resources", "testres1", "testres2");
  }

  @Test 
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

  @Test 
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
    assertFalse(hasProjectsToBeImported());

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
    assertTrue(hasProjectsToBeImported());

    scheduleProjectImportAndWait();
    assertFalse(hasProjectsToBeImported());
  }

  @Test 
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
    assertFalse(hasProjectsToBeImported());

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
    assertTrue(hasProjectsToBeImported());

    scheduleProjectImportAndWait();
    assertFalse(hasProjectsToBeImported());
  }

  @Test 
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

  @Test 
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
      public void projectsUpdated(@NotNull List<Pair<MavenProject, MavenProjectChanges>> updated, @NotNull List<MavenProject> deleted) {
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

  @Test 
  public void testShouldRemoveMavenProjectsAndNotAddThemToIgnore() throws Exception {
    VirtualFile mavenParentPom = createProjectSubFile("maven-parent/pom.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                                                              "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                                                                              "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                                                                              "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                                                                              "    <modelVersion>4.0.0</modelVersion>\n" +
                                                                              "    <groupId>test</groupId>\n" +
                                                                              "    <artifactId>parent-maven</artifactId>\n" +
                                                                              "    <packaging>pom</packaging>\n" +
                                                                              "    <version>1.0-SNAPSHOT</version>\n" +
                                                                              "    <modules>\n" +
                                                                              "        <module>child1</module>\n" +
                                                                              "    </modules>" +
                                                                              "</project>");

    createProjectSubFile("maven-parent/child1/pom.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                                        "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                                                        "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                                                        "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                                                        "    <parent>\n" +
                                                        "        <artifactId>parent-maven</artifactId>\n" +
                                                        "        <groupId>test</groupId>\n" +
                                                        "        <version>1.0-SNAPSHOT</version>\n" +
                                                        "    </parent>\n" +
                                                        "    <modelVersion>4.0.0</modelVersion>" +
                                                        "    <artifactId>child1</artifactId>" +
                                                        "</project>");

    ApplicationManager.getApplication().runWriteAction(() -> {
      ModuleManager.getInstance(myProject).newModule("non-maven", ModuleTypeId.JAVA_MODULE);
    });
    importProject(mavenParentPom);
    assertEquals(3, ModuleManager.getInstance(myProject).getModules().length);

    configConfirmationForYesAnswer();

    RemoveManagedFilesAction action = new RemoveManagedFilesAction();
    action.actionPerformed(new TestActionEvent(createTestDataContext(mavenParentPom), action));
    assertEquals(1, ModuleManager.getInstance(myProject).getModules().length);
    assertEquals("non-maven", ModuleManager.getInstance(myProject).getModules()[0].getName());
    assertEmpty(myProjectsManager.getIgnoredFilesPaths());

    //should then import project in non-ignored state again
    importProject(mavenParentPom);
    assertEquals(3, ModuleManager.getInstance(myProject).getModules().length);
    assertEmpty(myProjectsManager.getIgnoredFilesPaths());
  }



  private DataContext createTestDataContext(VirtualFile mavenParentPom) {
    final DataContext defaultContext = DataManager.getInstance().getDataContext();
    return dataId -> {
      if (CommonDataKeys.PROJECT.is(dataId)) {
        return myProject;
      }
      if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
        return new VirtualFile[]{mavenParentPom};
      }
      return defaultContext.getData(dataId);
    };
  }

  @Override
  protected void doImportProjects(List<VirtualFile> files, boolean failOnReadingError, String... profiles) {
    if(isNewImportingProcess){
      importViaNewFlow(files, failOnReadingError, Collections.emptyList(), profiles);
    } else {
      super.doImportProjects(files, failOnReadingError, profiles);
      resolveDependenciesAndImport(); // wait of full import completion
    }

  }

  private boolean hasProjectsToBeImported() {
    return ExternalSystemProjectNotificationAware.getInstance(myProject).isNotificationVisible();
  }

  private void scheduleProjectImportAndWait() {
    assertTrue(hasProjectsToBeImported()); // otherwise all imports will be skip
    ExternalSystemProjectTracker.getInstance(myProject).scheduleProjectRefresh();
    resolveDependenciesAndImport();
    assertFalse(hasProjectsToBeImported()); // otherwise project settings was modified while importing
  }

  /**
   * temporary solution. since The maven deletes files during the import process (renaming the file).
   * And therefore the floating bar is always displayed.
   * Because there is no information who deleted the import file or the other user action
   * problem in MavenProjectsAware#collectSettingsFiles() / yieldAll(projectsTree.projectsFiles.map { it.path })
   */
  private void scheduleProjectImportAndWaitWithoutCheckFloatingBar() {
    ExternalSystemProjectTracker.getInstance(myProject).scheduleProjectRefresh();
    resolveDependenciesAndImport();
  }
}
