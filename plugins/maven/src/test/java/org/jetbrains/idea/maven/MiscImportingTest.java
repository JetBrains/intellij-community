package org.jetbrains.idea.maven;

import com.intellij.ProjectTopics;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;

public class MiscImportingTest extends MavenImportingTestCase {
  private int count;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myProject.getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {
      }

      public void rootsChanged(ModuleRootEvent event) {
        count++;
      }
    });
  }

  public void testImportingFiresRootChangesOnlyOnce() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertEquals(1, count);
  }

  public void testResolvingFiresRootChangesOnlyOnce() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertEquals(1, count);

    resolveProject();
    assertEquals(2, count);
  }

  public void testFacetsDoNotFireRootsChanges() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<packaging>war</packaging>");

    assertEquals(1, count);

    resolveProject();
    assertEquals(2, count);
  }
}
