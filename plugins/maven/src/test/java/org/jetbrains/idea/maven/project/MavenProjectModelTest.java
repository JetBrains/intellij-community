package org.jetbrains.idea.maven.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.MavenImportingTestCase;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MavenProjectModelTest extends MavenImportingTestCase {
  MavenProjectModel model = new MavenProjectModel();

  public void testTwoRootProjects() throws Exception {
    VirtualFile m1 = createModulePom("m1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m1</artifactId>" +
                                     "<version>1</version>");

    VirtualFile m2 = createModulePom("m2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m2</artifactId>" +
                                     "<version>1</version>");

    readModel(m1, m2);
    List<MavenProjectModel.Node> nodes = model.getRootProjects();

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

    readModel(m1, m2, m1);
    List<MavenProjectModel.Node> nodes = model.getRootProjects();

    assertEquals(2, nodes.size());
    assertEquals(m1, nodes.get(0).getFile());
    assertEquals(m2, nodes.get(1).getFile());
  }

  public void testDoNotImportChildAsRootProject() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    VirtualFile m = createModulePom("m",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>m</artifactId>" +
                                    "<version>1</version>");

    readModel(myProjectPom, m);
    List<MavenProjectModel.Node> nodes = model.getRootProjects();

    assertEquals(1, nodes.size());
    assertEquals(myProjectPom, nodes.get(0).getFile());

    assertEquals(1, nodes.get(0).getSubProjects().size());
    assertEquals(m, nodes.get(0).getSubProjects().get(0).getFile());
  }

  public void testRemovingChildProjectFromRootProjects() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
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
    readModel(m, myProjectPom);
    List<MavenProjectModel.Node> nodes = model.getRootProjects();

    assertEquals(1, nodes.size());
    assertEquals(myProjectPom, nodes.get(0).getFile());

    assertEquals(1, nodes.get(0).getSubProjects().size());
    assertEquals(m, nodes.get(0).getSubProjects().get(0).getFile());
  }

  public void testUpdatingWholeModel() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    VirtualFile m = createModulePom("m",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>m</artifactId>" +
                                    "<version>1</version>");

    readModel(myProjectPom);

    List<MavenProjectModel.Node> nodes = model.getRootProjects();

    MavenProjectModel.Node parentNode = nodes.get(0);
    MavenProjectModel.Node childNode = nodes.get(0).getSubProjects().get(0);

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project1</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    updateModulePom("m",
                    "<groupId>test</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>1</version>");

    readModel(myProjectPom);
    
    nodes = model.getRootProjects();

    assertEquals(1, nodes.size());
    assertEquals(1, nodes.get(0).getSubProjects().size());

    MavenProjectModel.Node parentNode1 = nodes.get(0);
    MavenProjectModel.Node childNode1 = nodes.get(0).getSubProjects().get(0);

    assertSame(parentNode, parentNode1);
    assertSame(childNode, childNode1);

    assertEquals("project1", parentNode1.getMavenProject().getArtifactId());
    assertEquals("m1", childNode1.getMavenProject().getArtifactId());
  }

  public void testUpdatingParticularProject() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    VirtualFile m = createModulePom("m",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>m</artifactId>" +
                                    "<version>1</version>");

    readModel(myProjectPom);

    updateModulePom("m",
                    "<groupId>test</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>1</version>");

    model.update(m, getMavenCoreSettings());

    MavenProjectModel.Node n = model.findProject(m);
    assertEquals("m1", n.getMavenProject().getArtifactId());
  }

  public void testUpdatingSubProjects() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <moduleName>name</moduleName>" +
                     "</properties>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    VirtualFile m = createModulePom("m",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>${moduleName}</artifactId>" +
                                    "<version>1</version>" +

                                    "<parent>" +
                                    "  <groupId>test</groupId>" +
                                    "  <artifactId>project</artifactId>" +
                                    "  <version>1</version>" +
                                    "</parent>");

    readModel(myProjectPom);

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <moduleName>name2</moduleName>" +
                     "</properties>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    model.update(myProjectPom, getMavenCoreSettings());

    MavenProjectModel.Node n = model.findProject(m);
    assertEquals("name2", n.getMavenProject().getArtifactId());
  }

  public void testUpdatingProjectWithSubProjectsRemoval() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "</modules>");

    VirtualFile m1 = createModulePom("m1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m1</artifactId>" +
                                     "<version>1</version>" +

                                     "<modules>" +
                                     "  <module>m2</module>" +
                                     "</modules>");

    VirtualFile m2 = createModulePom("m1/m2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m2</artifactId>" +
                                     "<version>1</version>");

    readModel(myProjectPom);

    List<MavenProjectModel.Node> roots = model.getRootProjects();
    assertEquals(1, roots.size());
    assertEquals(1, roots.get(0).getSubProjects().size());
    assertEquals(1, roots.get(0).getSubProjects().get(0).getSubProjects().size());

    model.remove(m1);

    roots = model.getRootProjects();
    assertEquals(1, roots.size());
    assertEquals(myProjectPom, roots.get(0).getFile());
    assertEquals(0, roots.get(0).getSubProjects().size());
  }

  public void testUpdatingWithSubprojectAdding() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    VirtualFile m = createModulePom("m",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>m</artifactId>" +
                                    "<version>1</version>");

    readModel(myProjectPom, m);

    List<MavenProjectModel.Node> roots = model.getRootProjects();
    assertEquals(2, roots.size());
    assertEquals(myProjectPom, roots.get(0).getFile());
    assertEquals(m, roots.get(1).getFile());
    assertEquals("m", roots.get(1).getMavenProject().getArtifactId());
    assertEquals(0, roots.get(0).getSubProjects().size());

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    updateModulePom("m",
                    "<groupId>test</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>1</version>");

    model.update(myProjectPom, getMavenCoreSettings());

    roots = model.getRootProjects();
    assertEquals(1, roots.size());
    assertEquals(myProjectPom, roots.get(0).getFile());
    assertEquals(1, roots.get(0).getSubProjects().size());
    assertEquals(m, roots.get(0).getSubProjects().get(0).getFile());
    assertEquals("m1", roots.get(0).getSubProjects().get(0).getMavenProject().getArtifactId());
  }

  public void testUpdatingWithAddingNewModule() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    VirtualFile m = createModulePom("m",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>m</artifactId>" +
                                    "<version>1</version>");

    readModel(myProjectPom);

    List<MavenProjectModel.Node> roots = model.getRootProjects();
    assertEquals(1, roots.size());
    assertEquals(myProjectPom, roots.get(0).getFile());
    assertEquals(0, roots.get(0).getSubProjects().size());

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    model.update(myProjectPom, getMavenCoreSettings());

    roots = model.getRootProjects();
    assertEquals(1, roots.size());
    assertEquals(myProjectPom, roots.get(0).getFile());
    assertEquals(1, roots.get(0).getSubProjects().size());
    assertEquals(m, roots.get(0).getSubProjects().get(0).getFile());
  }

  public void testDeletingProject() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    VirtualFile m = createModulePom("m",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>m</artifactId>" +
                                    "<version>1</version>");

    readModel(myProjectPom);

    List<MavenProjectModel.Node> roots = model.getRootProjects();
    assertEquals(1, roots.size());
    assertEquals(1, roots.get(0).getSubProjects().size());

    model.remove(m);

    roots = model.getRootProjects();
    assertEquals(1, roots.size());
    assertEquals(0, roots.get(0).getSubProjects().size());
  }

  public void testAddingProject() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    readModel(myProjectPom);

    List<MavenProjectModel.Node> roots = model.getRootProjects();
    assertEquals(1, roots.size());
    assertEquals(0, roots.get(0).getSubProjects().size());

    VirtualFile m1 = createModulePom("m1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m1</artifactId>" +
                                     "<version>1</version>" +

                                     "<modules>" +
                                     "  <module>m2</module>" +
                                     "</modules>");

    VirtualFile m2 = createModulePom("m1/m2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m2</artifactId>" +
                                     "<version>1</version>");


    model.update(m1, getMavenCoreSettings());

    roots = model.getRootProjects();
    assertEquals(2, roots.size());
    assertEquals(myProjectPom, roots.get(0).getFile());
    assertEquals(m1, roots.get(1).getFile());
    assertEquals(1, roots.get(1).getSubProjects().size());
    assertEquals(m2, roots.get(1).getSubProjects().get(0).getFile());
  }

  public void testAddingProjectAsAModule() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    readModel(myProjectPom);

    List<MavenProjectModel.Node> roots = model.getRootProjects();
    assertEquals(1, roots.size());
    assertEquals(0, roots.get(0).getSubProjects().size());

    VirtualFile m = createModulePom("m",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>m</artifactId>" +
                                    "<version>1</version>");

    model.update(m, getMavenCoreSettings());

    roots = model.getRootProjects();
    assertEquals(1, roots.size());
    assertEquals(1, roots.get(0).getSubProjects().size());
    assertEquals(m, roots.get(0).getSubProjects().get(0).getFile());
  }

  private void readModel(VirtualFile... files) throws CanceledException, MavenException {
    model.read(Arrays.asList(files),
               Collections.<VirtualFile, Module>emptyMap(),
               Collections.<String>emptyList(),
               getMavenCoreSettings(),
               getMavenImporterSettings(),
               new MavenProcess(new EmptyProgressIndicator()));
  }
}
