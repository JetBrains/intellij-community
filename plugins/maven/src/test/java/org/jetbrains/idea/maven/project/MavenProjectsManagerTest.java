package org.jetbrains.idea.maven.project;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.MavenImportingTestCase;

public class MavenProjectsManagerTest extends MavenImportingTestCase {
  public void testShouldReturnNullForUnprocessedFiles() throws Exception {
    MavenProjectsManager.getInstance(myProject).doInitComponent(false);

    // this pom file doesn't belong to any of the modules, this is won't be processed
    // by MavenProjectProjectsManager and won't occur in its projects list.
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    // shouldn't throw
    assertNull(MavenProjectsManager.getInstance(myProject).findProject(myProjectPom));
  }

  public void testHandingDirectoryWithPomFileDeletion() throws Exception {
    MavenProjectsManager.getInstance(myProject).initEventsHandling();

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<packaging>pom</packaging>" +
                  "<version>1</version>");

    createModulePom("dir/module", "<groupId>test</groupId>" +
                                  "<artifactId>module</artifactId>" +
                                  "<version>1</version>");
    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>dir/module</module>" +
                     "</modules>");

    assertEquals(2, MavenProjectsManager.getInstance(myProject).getProjects().size());

    VirtualFile dir = myProjectRoot.findChild("dir");
    dir.delete(null);

    assertEquals(1, MavenProjectsManager.getInstance(myProject).getProjects().size());
  }
}
