package org.jetbrains.idea.maven.navigator;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.MavenImportingTestCase;

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
    myStructure = myNavigator.getStructureForTests();
  }

  public void testActivation() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    createModulePom("m", "<groupId>test</groupId>" +
                         "<artifactId>m</artifactId>" +
                         "<version>1</version>");
    myProjectsManager.resetManagedFilesAndProfilesInTests(Collections.singletonList(myProjectPom), Collections.EMPTY_LIST);
    waitForReadingCompletion();
    assertTrue(getRootNodes().isEmpty());

    myProjectsManager.fireActivatedInTests();
    assertEquals(1, getRootNodes().size());
    assertEquals(1, getRootNodes().get(0).getModulesNode().getProjectNodes().size());
  }

  public void testReconnectingModulesWhenModuleRead() throws Exception {
    myProjectsManager.fireActivatedInTests();

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");
    readFiles(myProjectPom);

    assertEquals(1, getRootNodes().size());
    assertEquals(myProjectPom, getRootNodes().get(0).getFile());
    assertEquals(0, getRootNodes().get(0).getModulesNode().getProjectNodes().size());

    VirtualFile m = createModulePom("m", "<groupId>test</groupId>" +
                                         "<artifactId>m</artifactId>" +
                                         "<version>1</version>");
    readFiles(m);

    assertEquals(1, getRootNodes().size());
    assertEquals(myProjectPom, getRootNodes().get(0).getFile());
    assertEquals(1, getRootNodes().get(0).getModulesNode().getProjectNodes().size());
    assertEquals(m, getRootNodes().get(0).getModulesNode().getProjectNodes().get(0).getFile());
  }

  public void testReconnectingModulesWhenParentRead() throws Exception {
    myProjectsManager.fireActivatedInTests();

    VirtualFile m = createModulePom("m", "<groupId>test</groupId>" +
                                         "<artifactId>m</artifactId>" +
                                         "<version>1</version>");
    readFiles(m);

    assertEquals(1, getRootNodes().size());
    assertEquals(m, getRootNodes().get(0).getFile());

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");
    readFiles(myProjectPom);

    assertEquals(1, getRootNodes().size());
    assertEquals(myProjectPom, getRootNodes().get(0).getFile());
    assertEquals(1, getRootNodes().get(0).getModulesNode().getProjectNodes().size());
    assertEquals(m, getRootNodes().get(0).getModulesNode().getProjectNodes().get(0).getFile());
  }

  public void testReconnectingModulesWhenProjectBacomesParent() throws Exception {
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
    assertEquals(myProjectPom, getRootNodes().get(0).getFile());
    assertEquals(1, getRootNodes().get(0).getModulesNode().getProjectNodes().size());
    assertEquals(m, getRootNodes().get(0).getModulesNode().getProjectNodes().get(0).getFile());
  }

  public void testUpdatingWhenManagedFilesChange() throws Exception {
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

  public void testGroupModulesAndGroupNot() throws Exception {
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
    assertEquals(1, getRootNodes().get(0).getModulesNode().getProjectNodes().size());
    assertEquals(1, getRootNodes().get(0).getModulesNode().getProjectNodes().get(0).getModulesNode().getProjectNodes().size());

    myNavigator.setGroupModules(false);
    assertEquals(3, getRootNodes().size());

    myNavigator.setGroupModules(true);
    assertEquals(1, getRootNodes().size());
    assertEquals(1, getRootNodes().get(0).getModulesNode().getProjectNodes().size());
    assertEquals(1, getRootNodes().get(0).getModulesNode().getProjectNodes().get(0).getModulesNode().getProjectNodes().size());
  }

  public void testIgnoringProjects() throws Exception {
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

  public void testIgnoringParentProjectWhenNeedNoReconnectModule() throws Exception {
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
    assertEquals(myProjectPom, projectNode.getFile());
    assertEquals(1, projectNode.getModulesNode().getProjectNodes().size());

    myNavigator.setShowIgnored(false);
    assertEquals(2, getRootNodes().size());
    assertEquals(1, myStructure.getRootElement().getChildren().length); // only one of them is visible
    projectNode = (MavenProjectsStructure.ProjectNode)myStructure.getRootElement().getChildren()[0];
    assertEquals(m, projectNode.getFile());
    assertEquals(0, projectNode.getModulesNode().getProjectNodes().size());
  }

  public void testReorderingProjectsWhenNameChanges() throws Exception {
    myProjectsManager.fireActivatedInTests();

    VirtualFile m1 = createModulePom("m1", "<groupId>test</groupId>" +
                                           "<artifactId>m1</artifactId>" +
                                           "<version>1</version>");

    VirtualFile m2 = createModulePom("m2", "<groupId>test</groupId>" +
                                           "<artifactId>m2</artifactId>" +
                                           "<version>1</version>");
    readFiles(m1, m2);

    assertEquals(2, getRootNodes().size());
    assertEquals(m1, getRootNodes().get(0).getFile());
    assertEquals(m2, getRootNodes().get(1).getFile());

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>am2</artifactId>" +
                          "<version>1</version>");
    readFiles(m2);

    assertEquals(2, getRootNodes().size());
    assertEquals(m2, getRootNodes().get(0).getFile());
    assertEquals(m1, getRootNodes().get(1).getFile());
  }

  public void testReloadingState() throws Exception {
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
    assertEquals(1, getRootNodes().get(0).getModulesNode().getProjectNodes().size());

    MavenProjectsNavigatorState newState = new MavenProjectsNavigatorState();
    newState.groupStructurally = false;
    myNavigator.loadState(newState);

    assertEquals(2, getRootNodes().size());
  }

  public void testNavigatableForProjectNode() throws Exception {
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
    return myStructure.getRootElement().getProjectNodes();
  }
}
