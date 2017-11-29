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

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MavenProjectsNavigatorTest extends MavenImportingTestCase {
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
    myProjectsManager.resetManagedFilesAndProfilesInTests(Collections.singletonList(myProjectPom), MavenExplicitProfiles.NONE);
    waitForReadingCompletion();

    myProjectsManager.fireActivatedInTests();
    assertEquals(1, getRootNodes().size());
    assertEquals(1, getRootNodes().get(0).getModulesNode().getProjectNodesInTests().size());
  }

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
    assertEquals(0, getRootNodes().get(0).getModulesNode().getProjectNodesInTests().size());

    VirtualFile m = createModulePom("m", "<groupId>test</groupId>" +
                                         "<artifactId>m</artifactId>" +
                                         "<version>1</version>");
    readFiles(m);

    assertEquals(1, getRootNodes().size());
    assertEquals(myProjectPom, getRootNodes().get(0).getVirtualFile());
    assertEquals(1, getRootNodes().get(0).getModulesNode().getProjectNodesInTests().size());
    assertEquals(m, getRootNodes().get(0).getModulesNode().getProjectNodesInTests().get(0).getVirtualFile());
  }

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
    assertEquals(1, getRootNodes().get(0).getModulesNode().getProjectNodesInTests().size());
    assertEquals(m, getRootNodes().get(0).getModulesNode().getProjectNodesInTests().get(0).getVirtualFile());
  }

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
    assertEquals(1, getRootNodes().get(0).getModulesNode().getProjectNodesInTests().size());
    assertEquals(m, getRootNodes().get(0).getModulesNode().getProjectNodesInTests().get(0).getVirtualFile());
  }

  public void testUpdatingWhenManagedFilesChange() {
    myProjectsManager.fireActivatedInTests();

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");
    readFiles(myProjectPom);
    assertEquals(1, getRootNodes().size());

    myProjectsManager.removeManagedFiles(Collections.singletonList(myProjectPom));
    waitForReadingCompletion();
    assertEquals(0, getRootNodes().size());
  }

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
    assertEquals(1, getRootNodes().get(0).getModulesNode().getProjectNodesInTests().size());
    assertEquals(1, getRootNodes().get(0).getModulesNode().getProjectNodesInTests().get(0).getModulesNode().getProjectNodesInTests().size());

    myNavigator.setGroupModules(false);
    assertEquals(3, getRootNodes().size());

    myNavigator.setGroupModules(true);
    assertEquals(1, getRootNodes().size());
    assertEquals(1, getRootNodes().get(0).getModulesNode().getProjectNodesInTests().size());
    assertEquals(1, getRootNodes().get(0).getModulesNode().getProjectNodesInTests().get(0).getModulesNode().getProjectNodesInTests().size());
  }

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
    assertTrue(getRootNodes().get(0).getModulesNode().isVisible());
    assertEquals(1, getRootNodes().get(0).getModulesNode().getChildren().length);

    myNavigator.setShowIgnored(false);
    assertFalse(getRootNodes().get(0).getModulesNode().isVisible());
    assertEquals(0, getRootNodes().get(0).getModulesNode().getChildren().length);
  }

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
    assertEquals(1, projectNode.getModulesNode().getProjectNodesInTests().size());

    myNavigator.setShowIgnored(false);
    assertEquals(2, getRootNodes().size());
    assertEquals(1, myStructure.getRootElement().getChildren().length); // only one of them is visible
    projectNode = (MavenProjectsStructure.ProjectNode)myStructure.getRootElement().getChildren()[0];
    assertEquals(m, projectNode.getVirtualFile());
    assertEquals(0, projectNode.getModulesNode().getProjectNodesInTests().size());
  }

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
    assertEquals(1, getRootNodes().get(0).getModulesNode().getProjectNodesInTests().size());

    MavenProjectsNavigatorState newState = new MavenProjectsNavigatorState();
    newState.groupStructurally = false;
    myNavigator.loadState(newState);

    assertEquals(2, getRootNodes().size());
  }

  public void testNavigatableForProjectNode() {
    myProjectsManager.fireActivatedInTests();

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    readFiles(myProjectPom);
    assertTrue(getRootNodes().get(0).getNavigatable().canNavigateToSource());
  }

  private void readFiles(VirtualFile... files) {
    myProjectsManager.addManagedFiles(Arrays.asList(files));
    waitForReadingCompletion();
  }

  private List<MavenProjectsStructure.ProjectNode> getRootNodes() {
    return myStructure.getRootElement().getProjectNodesInTests();
  }
}
