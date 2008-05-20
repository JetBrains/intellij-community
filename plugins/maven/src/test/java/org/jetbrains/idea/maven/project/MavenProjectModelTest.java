package org.jetbrains.idea.maven.project;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.MavenImportingTestCase;

import java.util.List;

public class MavenProjectModelTest extends MavenImportingTestCase {
  public void testTwoRootProjects() throws Exception {
    VirtualFile m1 = createModulePom("m1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m1</artifactId>" +
                                     "<version>1</version>");

    VirtualFile m2 = createModulePom("m2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m2</artifactId>" +
                                     "<version>1</version>");

    importSeveralProjects(m1, m2);

    List<MavenProjectModel.Node> nodes = myImportProcessor.getMavenProjectModel().getRootProjects();
    assertEquals(2, nodes.size());
    assertEquals(m1, nodes.get(0).getFile());
    assertEquals(m2, nodes.get(1).getFile());
  }

  public void testDoNotImportSameRootProjectTwice() throws Exception {
    VirtualFile m1 = createModulePom("m1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m1</artifactId>" +
                                     "<version>1</version>");

    VirtualFile m2 = createModulePom("m2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m2</artifactId>" +
                                     "<version>1</version>");

    importSeveralProjects(m1, m2, m1);

    List<MavenProjectModel.Node> nodes = myImportProcessor.getMavenProjectModel().getRootProjects();
    assertEquals(2, nodes.size());
    assertEquals(m1, nodes.get(0).getFile());
    assertEquals(m2, nodes.get(1).getFile());
  }

  public void testDoNotImportChildAsRootProject() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>m1</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    VirtualFile m = createModulePom("m",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>m</artifactId>" +
                                    "<version>1</version>");

    importSeveralProjects(myProjectPom, m);

    List<MavenProjectModel.Node> nodes = myImportProcessor.getMavenProjectModel().getRootProjects();

    assertEquals(1, nodes.size());
    assertEquals(myProjectPom, nodes.get(0).getFile());

    assertEquals(1, nodes.get(0).mySubProjects.size());
    assertEquals(m, nodes.get(0).mySubProjects.get(0).getFile());
  }

  public void testRemovingChildProjectFromRootProjects() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>m1</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    VirtualFile m = createModulePom("m",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>m</artifactId>" +
                                    "<version>1</version>");

    // all projects are processed in the specified order
    // if we have imported a child project as a root one,
    // we have to correct ourselves and to remove it from roots.
    importSeveralProjects(m, myProjectPom);

    List<MavenProjectModel.Node> nodes = myImportProcessor.getMavenProjectModel().getRootProjects();

    assertEquals(1, nodes.size());
    assertEquals(myProjectPom, nodes.get(0).getFile());

    assertEquals(1, nodes.get(0).mySubProjects.size());
    assertEquals(m, nodes.get(0).mySubProjects.get(0).getFile());
  }
}
