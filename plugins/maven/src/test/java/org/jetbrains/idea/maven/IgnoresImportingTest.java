package org.jetbrains.idea.maven;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

public class IgnoresImportingTest extends MavenImportingTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initProjectsManager(false);
  }

  public void testDoNotImportIgnoredProjects() throws Exception {
    VirtualFile p1 = createModulePom("project1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>project1</artifactId>" +
                                     "<version>1</version>");

    VirtualFile p2 = createModulePom("project2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>project2</artifactId>" +
                                     "<version>1</version>");

    myProjectsManager.setIgnoredFilesPaths(Collections.singletonList(p1.getPath()));
    importProjects(p1, p2);
    assertModules("project2");
  }

  public void testAddingAndRemovingModulesWhenIgnoresChange() throws Exception {
    configConfirmationForYesAnswer();

    VirtualFile p1 = createModulePom("project1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>project1</artifactId>" +
                                     "<version>1</version>");

    VirtualFile p2 = createModulePom("project2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>project2</artifactId>" +
                                     "<version>1</version>");
    importProjects(p1, p2);
    assertModules("project1", "project2");

    myProjectsManager.setIgnoredFilesPaths(Collections.singletonList(p1.getPath()));
    waitForReadingCompletion();
    myProjectsManager.flushPendingImportRequestsInTests();

    assertModules("project2");

    myProjectsManager.setIgnoredFilesPaths(Collections.singletonList(p2.getPath()));
    waitForReadingCompletion();
    myProjectsManager.flushPendingImportRequestsInTests();

    assertModules("project1");
  }

  public void testDoNotAskTwiceToRemoveIgnoredModule() throws Exception {
    AtomicInteger counter = configConfirmationForNoAnswer();

    VirtualFile p1 = createModulePom("project1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>project1</artifactId>" +
                                     "<version>1</version>");

    VirtualFile p2 = createModulePom("project2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>project2</artifactId>" +
                                     "<version>1</version>");
    importProjects(p1, p2);
    assertModules("project1", "project2");

    myProjectsManager.setIgnoredFilesPaths(Collections.singletonList(p1.getPath()));
    waitForReadingCompletion();
    myProjectsManager.flushPendingImportRequestsInTests();

    assertModules("project1", "project2");
    assertEquals(1, counter.get());

    waitForReadingCompletion();
    myProjectsManager.flushPendingImportRequestsInTests();

    assertModules("project1", "project2");
    assertEquals(1, counter.get());
  }
}
