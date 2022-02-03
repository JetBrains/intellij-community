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
package org.jetbrains.idea.maven.navigator;

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.idea.maven.project.importing.FilesList;
import org.jetbrains.idea.maven.project.importing.MavenImportFlow;
import org.jetbrains.idea.maven.project.importing.MavenInitialImportContext;
import org.jetbrains.idea.maven.project.importing.MavenReadContext;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MavenProjectsNavigatorTest extends MavenMultiVersionImportingTestCase {
  private MavenProjectsNavigator myNavigator;
  private MavenProjectsStructure myStructure;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initProjectsManager(false);

    myNavigator = MavenProjectsNavigator.getInstance(myProject);
    myNavigator.initForTests();
    myNavigator.setGroupModules(true);

    myStructure = myNavigator.getStructureForTests();
  }

  @Override
  protected void tearDown() throws Exception {
    myNavigator = null;
    myStructure = null;
    super.tearDown();
  }

  @Test
  public void testActivation() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    createModulePom("m", "<groupId>test</groupId>" +
                         "<artifactId>m</artifactId>" +
                         "<version>1</version>");

    readFiles(myProjectPom);


    myProjectsManager.fireActivatedInTests();
    waitForMavenUtilRunnablesComplete();
    assertEquals(1, getRootNodes().size());
    assertEquals(1, getRootNodes().get(0).getProjectNodesInTests().size());
  }

  @Test
  public void testReconnectingModulesWhenModuleRead() {
    myProjectsManager.fireActivatedInTests();

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");
    readFiles(myProjectPom);

    assertEquals(1, getRootNodes().size());
    assertEquals(myProjectPom, getRootNodes().get(0).getVirtualFile());
    assertEquals(0, getRootNodes().get(0).getProjectNodesInTests().size());

    VirtualFile m = createModulePom("m", "<groupId>test</groupId>" +
                                         "<artifactId>m</artifactId>" +
                                         "<version>1</version>");
    readFiles(m);

    assertEquals(1, getRootNodes().size());
    assertEquals(myProjectPom, getRootNodes().get(0).getVirtualFile());
    assertEquals(1, getRootNodes().get(0).getProjectNodesInTests().size());
    assertEquals(m, getRootNodes().get(0).getProjectNodesInTests().get(0).getVirtualFile());
  }

  @Test
  public void testReconnectingModulesWhenParentRead() {
    myProjectsManager.fireActivatedInTests();

    VirtualFile m = createModulePom("m", "<groupId>test</groupId>" +
                                         "<artifactId>m</artifactId>" +
                                         "<version>1</version>");
    readFiles(m);

    assertEquals(1, getRootNodes().size());
    assertEquals(m, getRootNodes().get(0).getVirtualFile());

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");
    readFiles(myProjectPom);

    assertEquals(1, getRootNodes().size());
    assertEquals(myProjectPom, getRootNodes().get(0).getVirtualFile());
    assertEquals(1, getRootNodes().get(0).getProjectNodesInTests().size());
    assertEquals(m, getRootNodes().get(0).getProjectNodesInTests().get(0).getVirtualFile());
  }

  @Test
  public void testReconnectingModulesWhenProjectBecomesParent() {
    myProjectsManager.fireActivatedInTests();

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    VirtualFile m = createModulePom("m", "<groupId>test</groupId>" +
                                         "<artifactId>m</artifactId>" +
                                         "<version>1</version>");
    readFiles(myProjectPom, m);

    assertEquals(2, getRootNodes().size());

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");
    readFiles(myProjectPom);

    assertEquals(1, getRootNodes().size());
    assertEquals(myProjectPom, getRootNodes().get(0).getVirtualFile());
    assertEquals(1, getRootNodes().get(0).getProjectNodesInTests().size());
    assertEquals(m, getRootNodes().get(0).getProjectNodesInTests().get(0).getVirtualFile());
  }

  @Test
  public void testUpdatingWhenManagedFilesChange() {
    myProjectsManager.fireActivatedInTests();

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");
    readFiles(myProjectPom);
    resolveDependenciesAndImport();
    assertEquals(1, getRootNodes().size());

    MavenUtil.cleanAllRunnables();
    myProjectsManager.removeManagedFiles(Collections.singletonList(myProjectPom));
    readFiles();
    assertEquals(0, getRootNodes().size());
  }

  @Test
  public void testGroupModulesAndGroupNot() {
    myProjectsManager.fireActivatedInTests();

    myNavigator.setGroupModules(true);

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    VirtualFile m = createModulePom("m", "<groupId>test</groupId>" +
                                         "<artifactId>m</artifactId>" +
                                         "<version>1</version>" +
                                         "<modules>" +
                                         "  <module>mm</module>" +
                                         "</modules>");

    VirtualFile mm = createModulePom("m/mm", "<groupId>test</groupId>" +
                                             "<artifactId>mm</artifactId>" +
                                             "<version>1</version>");
    readFiles(myProjectPom, m, mm);

    assertEquals(1, getRootNodes().size());
    assertEquals(1, getRootNodes().get(0).getProjectNodesInTests().size());
    assertEquals(1, getRootNodes().get(0).getProjectNodesInTests().get(0).getProjectNodesInTests().size());

    myNavigator.setGroupModules(false);
    waitForMavenUtilRunnablesComplete();
    assertEquals(3, getRootNodes().size());

    myNavigator.setGroupModules(true);
    waitForMavenUtilRunnablesComplete();
    assertEquals(1, getRootNodes().size());
    assertEquals(1, getRootNodes().get(0).getProjectNodesInTests().size());
    assertEquals(1, getRootNodes().get(0).getProjectNodesInTests().get(0).getProjectNodesInTests().size());
  }

  @Test
  public void testIgnoringProjects() {
    myProjectsManager.fireActivatedInTests();

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    VirtualFile m = createModulePom("m", "<groupId>test</groupId>" +
                                         "<artifactId>m</artifactId>" +
                                         "<version>1</version>");
    readFiles(myProjectPom, m);

    myProjectsTree.setIgnoredFilesPaths(Arrays.asList(m.getPath()));

    myNavigator.setShowIgnored(true);
    waitForMavenUtilRunnablesComplete();
    assertTrue(getRootNodes().get(0).isVisible());
    assertEquals(2, getRootNodes().get(0).getChildren().length);

    myNavigator.setShowIgnored(false);
    waitForMavenUtilRunnablesComplete();
    assertTrue(getRootNodes().get(0).isVisible());
    assertEquals(1, getRootNodes().get(0).getChildren().length);
  }

  @Test
  public void testIgnoringParentProjectWhenNeedNoReconnectModule() {
    myProjectsManager.fireActivatedInTests();

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    VirtualFile m = createModulePom("m", "<groupId>test</groupId>" +
                                         "<artifactId>m</artifactId>" +
                                         "<version>1</version>");
    readFiles(myProjectPom, m);

    myProjectsTree.setIgnoredFilesPaths(Arrays.asList(myProjectPom.getPath()));

    myNavigator.setShowIgnored(true);
    assertEquals(1, getRootNodes().size());
    assertEquals(1, myStructure.getRootElement().getChildren().length);
    MavenProjectsStructure.ProjectNode projectNode = (MavenProjectsStructure.ProjectNode)myStructure.getRootElement().getChildren()[0];
    assertEquals(myProjectPom, projectNode.getVirtualFile());
    assertEquals(1, projectNode.getProjectNodesInTests().size());

    myNavigator.setShowIgnored(false);
    waitForMavenUtilRunnablesComplete();
    assertEquals(2, getRootNodes().size());
    assertEquals(1, myStructure.getRootElement().getChildren().length); // only one of them is visible
    projectNode = (MavenProjectsStructure.ProjectNode)myStructure.getRootElement().getChildren()[0];
    assertEquals(m, projectNode.getVirtualFile());
    assertEquals(0, projectNode.getProjectNodesInTests().size());
  }

  @Test
  public void testReorderingProjectsWhenNameChanges() {
    myProjectsManager.fireActivatedInTests();

    VirtualFile m1 = createModulePom("m1", "<groupId>test</groupId>" +
                                           "<artifactId>m1</artifactId>" +
                                           "<version>1</version>");

    VirtualFile m2 = createModulePom("m2", "<groupId>test</groupId>" +
                                           "<artifactId>m2</artifactId>" +
                                           "<version>1</version>");
    readFiles(m1, m2);

    assertEquals(2, getRootNodes().size());
    assertEquals(m1, getRootNodes().get(0).getVirtualFile());
    assertEquals(m2, getRootNodes().get(1).getVirtualFile());

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>am2</artifactId>" +
                          "<version>1</version>");
    readFiles(m2);

    assertEquals(2, getRootNodes().size());
    assertEquals(m2, getRootNodes().get(0).getVirtualFile());
    assertEquals(m1, getRootNodes().get(1).getVirtualFile());
  }

  @Test
  public void testReloadingState() {
    myProjectsManager.fireActivatedInTests();

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    VirtualFile m = createModulePom("m", "<groupId>test</groupId>" +
                                         "<artifactId>m</artifactId>" +
                                         "<version>1</version>");
    readFiles(myProjectPom, m);

    assertEquals(1, getRootNodes().size());
    assertEquals(1, getRootNodes().get(0).getProjectNodesInTests().size());

    MavenProjectsNavigatorState newState = new MavenProjectsNavigatorState();
    newState.groupStructurally = false;
    myNavigator.loadState(newState);

    waitForMavenUtilRunnablesComplete();
    assertEquals(2, getRootNodes().size());
  }

  @Test
  public void testNavigatableForProjectNode() {
    myProjectsManager.fireActivatedInTests();

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    readFiles(myProjectPom);
    assertTrue(getRootNodes().get(0).getNavigatable().canNavigateToSource());
  }

  private void readFiles(VirtualFile... files) {
    if (isNewImportingProcess) {
      MavenImportFlow flow = new MavenImportFlow();
      List<VirtualFile> allFiles = new ArrayList<>(myProjectsManager.getProjectsFiles());
      allFiles.addAll(Arrays.asList(files));
      MavenInitialImportContext initialImportContext =
        flow.prepareNewImport(myProject, getMavenProgressIndicator(),
                              new FilesList(allFiles),
                              getMavenGeneralSettings(),
                              getMavenImporterSettings(),
                              Collections.emptyList(), Collections.emptyList());
      MavenReadContext readContext = flow.readMavenFiles(initialImportContext, Collections.emptyList(), Collections.emptyList());
      flow.updateProjectManager(readContext);
      myNavigator.scheduleStructureUpdate();

      waitForMavenUtilRunnablesComplete();
    }
    else {
      myProjectsManager.addManagedFiles(Arrays.asList(files));
      waitForReadingCompletion();
    }
  }

  private List<MavenProjectsStructure.ProjectNode> getRootNodes() {
    return myStructure.getRootElement().getProjectNodesInTests();
  }
}
