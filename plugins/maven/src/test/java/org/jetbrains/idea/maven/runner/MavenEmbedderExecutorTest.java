package org.jetbrains.idea.maven.runner;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import org.apache.maven.project.MavenProject;
import org.jetbrains.idea.maven.MavenTestCase;
import org.jetbrains.idea.maven.runner.executor.MavenEmbeddedExecutor;
import org.jetbrains.idea.maven.runner.executor.MavenRunnerParameters;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

public class MavenEmbedderExecutorTest extends MavenTestCase {
  public void testCollectingProjects() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    VirtualFile m1 = createModulePom("m1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m1</artifactId>" +
                                     "<version>1</version>");

    VirtualFile m2 = createModulePom("m2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m2</artifactId>" +
                                     "<version>1</version>");

    MavenRunnerParameters params = new MavenRunnerParameters(myProjectPom.getPath(), Arrays.asList("compile"), null);
    MavenEmbeddedExecutor e = new MavenEmbeddedExecutor(params, getMavenCoreSettings(), new MavenRunnerSettings());

    ArrayList<MavenProject> result = new ArrayList<MavenProject>();
    assertTrue(e.execute(result, new EmptyProgressIndicator()));

    assertEquals(3, result.size());
    assertEquals(new File(myProjectPom.getPath()), result.get(0).getFile());
    assertEquals(new File(m1.getPath()), result.get(1).getFile());
    assertEquals(new File(m2.getPath()), result.get(2).getFile());
  }
}
