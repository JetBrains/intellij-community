package org.jetbrains.idea.maven;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;

import java.util.Collections;

public class IgnoresImportingTest extends MavenImportingTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMavenProjectsManager(false);
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

    myMavenProjectsManager.setIgnoredFilesPaths(Collections.singletonList(p1.getPath()));
    importProjects(p1, p2);
    assertModules("project2");
  }

  public void testAddingAndRemovingModulesWhenIgnoresChange() throws Exception {
    Messages.setTestDialog(new TestDialog() {
      public int show(String message) {
        return 0; // yes
      }
    });

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

    myMavenProjectsManager.setIgnoredFilesPaths(Collections.singletonList(p1.getPath()));
    waitForReadingCompletion();
    myMavenProjectsManager.flushPendingImportRequestsInTests();

    assertModules("project2");

    myMavenProjectsManager.setIgnoredFilesPaths(Collections.singletonList(p2.getPath()));
    waitForReadingCompletion();
    myMavenProjectsManager.flushPendingImportRequestsInTests();

    assertModules("project1");
  }
}
