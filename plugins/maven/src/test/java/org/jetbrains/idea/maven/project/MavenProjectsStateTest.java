package org.jetbrains.idea.maven.project;

import org.jetbrains.idea.maven.MavenTestCase;
import org.jetbrains.idea.maven.state.MavenProjectsState;

import java.io.IOException;

public class MavenProjectsStateTest extends MavenTestCase {
  public void testShouldReturnNullForUnprocessedFiles() throws IOException {
    // this pom file doesn't belong to any of the modules, this is won't be processed
    // by MavenProjectStateComponent and won't occur in its projects list.
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    // shouldn't throw
    assertNull(MavenProjectsState.getInstance(myProject).getMavenProject(projectPom));
  }
}
