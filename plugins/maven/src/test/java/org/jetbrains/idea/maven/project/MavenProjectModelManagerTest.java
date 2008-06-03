package org.jetbrains.idea.maven.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.MavenImportingTestCase;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MavenProjectModelManagerTest extends MavenImportingTestCase {
  MavenProjectModelManager model = new MavenProjectModelManager();

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
    List<MavenProjectModel> roots = model.getRootProjects();

    assertEquals(2, roots.size());
    assertEquals(m1, roots.get(0).getFile());
    assertEquals(m2, roots.get(1).getFile());
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
    List<MavenProjectModel> roots = model.getRootProjects();

    assertEquals(2, roots.size());
    assertEquals(m2, roots.get(0).getFile());
    assertEquals(m1, roots.get(1).getFile());
  }

  public void testDoNotImportChildAsRootProject() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    VirtualFile m = createModulePom("m",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>m</artifactId>" +
                                    "<version>1</version>");

    readModel(myProjectPom, m);
    List<MavenProjectModel> roots = model.getRootProjects();

    assertEquals(1, roots.size());
    assertEquals(myProjectPom, roots.get(0).getFile());

    assertEquals(1, roots.get(0).getModules().size());
    assertEquals(m, roots.get(0).getModules().get(0).getFile());
  }

  public void testSameProjectAsModuleOfSeveralProjects() throws Exception {
    VirtualFile p1 = createModulePom("project1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>project1</artifactId>" +
                                     "<version>1</version>" +
                                     "<packaging>pom</packaging>" +

                                     "<modules>" +
                                     "  <module>../module</module>" +
                                     "</modules>");

    VirtualFile p2 = createModulePom("project2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>project2</artifactId>" +
                                     "<version>1</version>" +
                                     "<packaging>pom</packaging>" +

                                     "<modules>" +
                                     "  <module>../module</module>" +
                                     "</modules>");

    VirtualFile m = createModulePom("module",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>module</artifactId>" +
                                    "<version>1</version>");

    readModel(p1, p2);
    List<MavenProjectModel> roots = model.getRootProjects();

    assertEquals(2, roots.size());
    assertEquals(p1, roots.get(0).getFile());
    assertEquals(p2, roots.get(1).getFile());

    assertEquals(1, roots.get(0).getModules().size());
    assertEquals(m, roots.get(0).getModules().get(0).getFile());

    assertEquals(0, roots.get(1).getModules().size());
  }

  public void testSameProjectAsModuleOfSeveralProjectsInHierarchy() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>module1</module>" +
                     "  <module>module1/module2</module>" +
                     "</modules>");

    VirtualFile m1 = createModulePom("module1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>module1</artifactId>" +
                                     "<version>1</version>" +
                                     "<packaging>pom</packaging>" +

                                     "<modules>" +
                                     "  <module>module2</module>" +
                                     "</modules>");

    VirtualFile m2 = createModulePom("module1/module2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>module2</artifactId>" +
                                     "<version>1</version>");

    readModel(myProjectPom);
    List<MavenProjectModel> roots = model.getRootProjects();

    assertEquals(1, roots.size());
    assertEquals(1, roots.get(0).getModules().size());
    assertEquals(m1, roots.get(0).getModules().get(0).getFile());

    assertEquals(1, roots.get(0).getModules().get(0).getModules().size());
    assertEquals(m2, roots.get(0).getModules().get(0).getModules().get(0).getFile());
  }

  public void testRemovingChildProjectFromRootProjects() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

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
    List<MavenProjectModel> roots = model.getRootProjects();

    assertEquals(1, roots.size());
    assertEquals(myProjectPom, roots.get(0).getFile());

    assertEquals(1, roots.get(0).getModules().size());
    assertEquals(m, roots.get(0).getModules().get(0).getFile());
  }

  public void testUpdatingWholeModel() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    VirtualFile m = createModulePom("m",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>m</artifactId>" +
                                    "<version>1</version>");

    readModel(myProjectPom);

    List<MavenProjectModel> roots = model.getRootProjects();

    MavenProjectModel parentNode = roots.get(0);
    MavenProjectModel childNode = roots.get(0).getModules().get(0);

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project1</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    updateModulePom("m",
                    "<groupId>test</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>1</version>");

    readModel(myProjectPom);

    roots = model.getRootProjects();

    assertEquals(1, roots.size());
    assertEquals(1, roots.get(0).getModules().size());

    MavenProjectModel parentNode1 = roots.get(0);
    MavenProjectModel childNode1 = roots.get(0).getModules().get(0);

    assertSame(parentNode, parentNode1);
    assertSame(childNode, childNode1);

    assertEquals("project1", parentNode1.getMavenProject().getArtifactId());
    assertEquals("m1", childNode1.getMavenProject().getArtifactId());
  }

  public void testUpdatingModelWithNewProfiles() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<profiles>" +
                     "  <profile>" +
                     "    <id>one</id>" +
                     "    <modules>" +
                     "      <module>m1</module>" +
                     "    </modules>" +
                     "  </profile>" +
                     "  <profile>" +
                     "    <id>two</id>" +
                     "    <modules>" +
                     "      <module>m2</module>" +
                     "    </modules>" +
                     "  </profile>" +
                     "</profiles>");

    VirtualFile m1 = createModulePom("m1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m1</artifactId>" +
                                     "<version>1</version>");

    VirtualFile m2 = createModulePom("m2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m2</artifactId>" +
                                     "<version>1</version>");

    readModel(Collections.singletonList("one"), myProjectPom);

    List<MavenProjectModel> roots = model.getRootProjects();
    assertEquals(1, roots.size());
    assertEquals(myProjectPom, roots.get(0).getFile());

    assertEquals(1, roots.get(0).getModules().size());
    assertEquals(m1, roots.get(0).getModules().get(0).getFile());

    readModel(Collections.singletonList("two"), myProjectPom);

    roots = model.getRootProjects();
    assertEquals(1, roots.size());
    assertEquals(myProjectPom, roots.get(0).getFile());

    assertEquals(1, roots.get(0).getModules().size());
    assertEquals(m2, roots.get(0).getModules().get(0).getFile());
  }

  public void testUpdatingParticularProject() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

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

    model.update(m, getMavenCoreSettings(), new MavenProcess(new EmptyProgressIndicator()));

    MavenProjectModel n = model.findProject(m);
    assertEquals("m1", n.getMavenProject().getArtifactId());
  }

  public void testUpdatingInheritance() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <childName>child</childName>" +
                     "</properties>");

    VirtualFile child = createModulePom("child",
                                        "<groupId>test</groupId>" +
                                        "<artifactId>${childName}</artifactId>" +
                                        "<version>1</version>" +

                                        "<parent>" +
                                        "  <groupId>test</groupId>" +
                                        "  <artifactId>parent</artifactId>" +
                                        "  <version>1</version>" +
                                        "</parent>");

    readModel(myProjectPom, child);
    assertEquals("child", model.findProject(child).getMavenProject().getArtifactId());

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <childName>child2</childName>" +
                     "</properties>");

    model.update(myProjectPom, getMavenCoreSettings(), new MavenProcess(new EmptyProgressIndicator()));

    assertEquals("child2", model.findProject(child).getMavenProject().getArtifactId());
  }

  public void testUpdatingInheritanceHierarhically() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <subChildName>subChild</subChildName>" +
                     "</properties>");

    VirtualFile child = createModulePom("child",
                                        "<groupId>test</groupId>" +
                                        "<artifactId>child</artifactId>" +
                                        "<version>1</version>" +

                                        "<parent>" +
                                        "  <groupId>test</groupId>" +
                                        "  <artifactId>parent</artifactId>" +
                                        "  <version>1</version>" +
                                        "</parent>");

    VirtualFile subChild = createModulePom("subChild",
                                           "<groupId>test</groupId>" +
                                           "<artifactId>${subChildName}</artifactId>" +
                                           "<version>1</version>" +

                                           "<parent>" +
                                           "  <groupId>test</groupId>" +
                                           "  <artifactId>child</artifactId>" +
                                           "  <version>1</version>" +
                                           "</parent>");

    readModel(myProjectPom, child, subChild);

    assertEquals("subChild", model.findProject(subChild).getMavenProject().getArtifactId());

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <subChildName>subChild2</subChildName>" +
                     "</properties>");

    model.update(myProjectPom, getMavenCoreSettings(), new MavenProcess(new EmptyProgressIndicator()));

    assertEquals("subChild2", model.findProject(subChild).getMavenProject().getArtifactId());
  }

  public void testAddingInheritanceParent() throws Exception {
    VirtualFile child = createModulePom("child",
                                        "<groupId>test</groupId>" +
                                        "<artifactId>${childName}</artifactId>" +
                                        "<version>1</version>" +

                                        "<parent>" +
                                        "  <groupId>test</groupId>" +
                                        "  <artifactId>parent</artifactId>" +
                                        "  <version>1</version>" +
                                        "</parent>");

    readModel(child);
    assertEquals("${childName}", model.findProject(child).getMavenProject().getArtifactId());

    VirtualFile parent = createModulePom("parent",
                                         "<groupId>test</groupId>" +
                                         "<artifactId>parent</artifactId>" +
                                         "<version>1</version>" +

                                         "<properties>" +
                                         "  <childName>child</childName>" +
                                         "</properties>");

    model.update(parent, getMavenCoreSettings(), new MavenProcess(new EmptyProgressIndicator()));

    assertEquals("child", model.findProject(child).getMavenProject().getArtifactId());
  }

  public void testAddingInheritanceChild() throws Exception {
    VirtualFile parent = createModulePom("parent",
                                         "<groupId>test</groupId>" +
                                         "<artifactId>parent</artifactId>" +
                                         "<version>1</version>" +

                                         "<properties>" +
                                         "  <childName>child</childName>" +
                                         "</properties>");

    readModel(parent);

    VirtualFile child = createModulePom("child",
                                        "<groupId>test</groupId>" +
                                        "<artifactId>${childName}</artifactId>" +
                                        "<version>1</version>" +

                                        "<parent>" +
                                        "  <groupId>test</groupId>" +
                                        "  <artifactId>parent</artifactId>" +
                                        "  <version>1</version>" +
                                        "</parent>");

    model.update(child, getMavenCoreSettings(), new MavenProcess(new EmptyProgressIndicator()));

    assertEquals("child", model.findProject(child).getMavenProject().getArtifactId());
  }

  public void testAddingInheritanceChildOnParentUpdate() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <childName>child</childName>" +
                     "</properties>" +

                     "<modules>" +
                     " <module>child</module>" +
                     "</modules>");

    readModel(myProjectPom);

    VirtualFile child = createModulePom("child",
                                        "<groupId>test</groupId>" +
                                        "<artifactId>${childName}</artifactId>" +
                                        "<version>1</version>" +

                                        "<parent>" +
                                        "  <groupId>test</groupId>" +
                                        "  <artifactId>parent</artifactId>" +
                                        "  <version>1</version>" +
                                        "</parent>");

    model.update(myProjectPom, getMavenCoreSettings(), new MavenProcess(new EmptyProgressIndicator()));

    assertEquals("child", model.findProject(child).getMavenProject().getArtifactId());
  }

  public void testDoNotReAddInheritanceChildOnParentModulesRemoval() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     " <module>child</module>" +
                     "</modules>");

    VirtualFile child = createModulePom("child",
                                        "<groupId>test</groupId>" +
                                        "<artifactId>child</artifactId>" +
                                        "<version>1</version>" +

                                        "<parent>" +
                                        "  <groupId>test</groupId>" +
                                        "  <artifactId>parent</artifactId>" +
                                        "  <version>1</version>" +
                                        "</parent>");
    readModel(myProjectPom);

    List<MavenProjectModel> roots = model.getRootProjects();
    assertEquals(1, roots.size());
    assertEquals(myProjectPom, roots.get(0).getFile());
    assertEquals(1, roots.get(0).getModules().size());
    assertEquals(child, roots.get(0).getModules().get(0).getFile());

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>");

    model.update(myProjectPom, getMavenCoreSettings(), new MavenProcess(new EmptyProgressIndicator()));

    roots = model.getRootProjects();
    assertEquals(1, roots.size());
    assertEquals(myProjectPom, roots.get(0).getFile());
    assertEquals(0, roots.get(0).getModules().size());
  }

  public void testChangingInheritance() throws Exception {
    VirtualFile parent1 = createModulePom("parent1",
                                          "<groupId>test</groupId>" +
                                          "<artifactId>parent1</artifactId>" +
                                          "<version>1</version>" +

                                          "<properties>" +
                                          "  <childName>child1</childName>" +
                                          "</properties>");

    VirtualFile parent2 = createModulePom("parent2",
                                          "<groupId>test</groupId>" +
                                          "<artifactId>parent2</artifactId>" +
                                          "<version>1</version>" +

                                          "<properties>" +
                                          "  <childName>child2</childName>" +
                                          "</properties>");


    VirtualFile child = createModulePom("child",
                                        "<groupId>test</groupId>" +
                                        "<artifactId>${childName}</artifactId>" +
                                        "<version>1</version>" +

                                        "<parent>" +
                                        "  <groupId>test</groupId>" +
                                        "  <artifactId>parent1</artifactId>" +
                                        "  <version>1</version>" +
                                        "</parent>");


    readModel(parent1, parent2, child);
    assertEquals("child1", model.findProject(child).getMavenProject().getArtifactId());

    updateModulePom("child",
                    "<groupId>test</groupId>" +
                    "<artifactId>${childName}</artifactId>" +
                    "<version>1</version>" +

                    "<parent>" +
                    "  <groupId>test</groupId>" +
                    "  <artifactId>parent2</artifactId>" +
                    "  <version>1</version>" +
                    "</parent>");

    model.update(child, getMavenCoreSettings(), new MavenProcess(new EmptyProgressIndicator()));

    assertEquals("child2", model.findProject(child).getMavenProject().getArtifactId());
  }

  public void testChangingInheritanceParentId() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <childName>child</childName>" +
                     "</properties>");

    VirtualFile child = createModulePom("child",
                                        "<groupId>test</groupId>" +
                                        "<artifactId>${childName}</artifactId>" +
                                        "<version>1</version>" +

                                        "<parent>" +
                                        "  <groupId>test</groupId>" +
                                        "  <artifactId>parent</artifactId>" +
                                        "  <version>1</version>" +
                                        "</parent>");


    readModel(myProjectPom, child);
    assertEquals("child", model.findProject(child).getMavenProject().getArtifactId());

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent2</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <childName>child</childName>" +
                     "</properties>");

    model.update(myProjectPom, getMavenCoreSettings(), new MavenProcess(new EmptyProgressIndicator()));

    assertEquals("${childName}", model.findProject(child).getMavenProject().getArtifactId());
  }

  public void testDeletingInheritanceParent() throws Exception {
    VirtualFile parent = createModulePom("parent",
                                         "<groupId>test</groupId>" +
                                         "<artifactId>parent</artifactId>" +
                                         "<version>1</version>" +

                                         "<properties>" +
                                         "  <childName>child</childName>" +
                                         "</properties>");

    VirtualFile child = createModulePom("child",
                                        "<groupId>test</groupId>" +
                                        "<artifactId>${childName}</artifactId>" +
                                        "<version>1</version>" +

                                        "<parent>" +
                                        "  <groupId>test</groupId>" +
                                        "  <artifactId>parent</artifactId>" +
                                        "  <version>1</version>" +
                                        "</parent>");

    readModel(parent, child);

    assertEquals("child", model.findProject(child).getMavenProject().getArtifactId());

    model.delete(parent, getMavenCoreSettings(), new MavenProcess(new EmptyProgressIndicator()));

    assertEquals("${childName}", model.findProject(child).getMavenProject().getArtifactId());
  }

  public void testDeletingInheritanceChild() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <subChildName>subChild</subChildName>" +
                     "</properties>");

    VirtualFile child = createModulePom("child",
                                        "<groupId>test</groupId>" +
                                        "<artifactId>child</artifactId>" +
                                        "<version>1</version>" +

                                        "<parent>" +
                                        "  <groupId>test</groupId>" +
                                        "  <artifactId>parent</artifactId>" +
                                        "  <version>1</version>" +
                                        "</parent>");

    VirtualFile subChild = createModulePom("subChild",
                                           "<groupId>test</groupId>" +
                                           "<artifactId>${subChildName}</artifactId>" +
                                           "<version>1</version>" +

                                           "<parent>" +
                                           "  <groupId>test</groupId>" +
                                           "  <artifactId>child</artifactId>" +
                                           "  <version>1</version>" +
                                           "</parent>");

    readModel(myProjectPom, child, subChild);
    assertEquals("subChild", model.findProject(subChild).getMavenProject().getArtifactId());

    model.delete(child, getMavenCoreSettings(), new MavenProcess(new EmptyProgressIndicator()));
    assertEquals("${subChildName}", model.findProject(subChild).getMavenProject().getArtifactId());
  }

  public void testUpdatingAddsModules() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>");

    VirtualFile m = createModulePom("m",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>m</artifactId>" +
                                    "<version>1</version>");

    readModel(myProjectPom);

    List<MavenProjectModel> roots = model.getRootProjects();
    assertEquals(1, roots.size());
    assertEquals(myProjectPom, roots.get(0).getFile());
    assertEquals(0, roots.get(0).getModules().size());

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    model.update(myProjectPom, getMavenCoreSettings(), new MavenProcess(new EmptyProgressIndicator()));

    roots = model.getRootProjects();
    assertEquals(1, roots.size());
    assertEquals(myProjectPom, roots.get(0).getFile());
    assertEquals(1, roots.get(0).getModules().size());
    assertEquals(m, roots.get(0).getModules().get(0).getFile());
  }

  public void testUpdatingDoesNotUpdateModules() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    VirtualFile m = createModulePom("m",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>m</artifactId>" +
                                    "<version>1</version>");

    readModel(myProjectPom);

    assertEquals("m", model.findProject(m).getMavenProject().getArtifactId());

    updateModulePom("m",
                    "<groupId>test</groupId>" +
                    "<artifactId>m2</artifactId>" +
                    "<version>1</version>");

    model.update(myProjectPom, getMavenCoreSettings(), new MavenProcess(new EmptyProgressIndicator()));

    // did not change
    assertEquals("m", model.findProject(m).getMavenProject().getArtifactId());
  }

  public void testAddingProjectAsModuleToExistingOne() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    readModel(myProjectPom);

    List<MavenProjectModel> roots = model.getRootProjects();
    assertEquals(1, roots.size());
    assertEquals(0, roots.get(0).getModules().size());

    VirtualFile m = createModulePom("m",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>m</artifactId>" +
                                    "<version>1</version>");

    model.update(m, getMavenCoreSettings(), new MavenProcess(new EmptyProgressIndicator()));

    roots = model.getRootProjects();
    assertEquals(1, roots.size());
    assertEquals(1, roots.get(0).getModules().size());
    assertEquals(m, roots.get(0).getModules().get(0).getFile());
  }

  public void testAddingProjectAsAggregatorForExistingOne() throws Exception {
    VirtualFile m = createModulePom("m",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>m</artifactId>" +
                                    "<version>1</version>");

    readModel(m);

    List<MavenProjectModel> roots = model.getRootProjects();
    assertEquals(1, roots.size());
    assertEquals(m, roots.get(0).getFile());
    assertEquals(0, roots.get(0).getModules().size());

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    model.update(myProjectPom, getMavenCoreSettings(), new MavenProcess(new EmptyProgressIndicator()));

    roots = model.getRootProjects();
    assertEquals(1, roots.size());
    assertEquals(myProjectPom, roots.get(0).getFile());
    assertEquals(1, roots.get(0).getModules().size());
    assertEquals(m, roots.get(0).getModules().get(0).getFile());
  }

  public void testAddingProjectWithModules() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>");

    readModel(myProjectPom);

    List<MavenProjectModel> roots = model.getRootProjects();
    assertEquals(1, roots.size());
    assertEquals(0, roots.get(0).getModules().size());

    VirtualFile m1 = createModulePom("m1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m1</artifactId>" +
                                     "<version>1</version>" +
                                     "<packaging>pom</packaging>" +

                                     "<modules>" +
                                     "  <module>m2</module>" +
                                     "</modules>");

    VirtualFile m2 = createModulePom("m1/m2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m2</artifactId>" +
                                     "<version>1</version>");


    model.update(m1, getMavenCoreSettings(), new MavenProcess(new EmptyProgressIndicator()));

    roots = model.getRootProjects();
    assertEquals(2, roots.size());
    assertEquals(myProjectPom, roots.get(0).getFile());
    assertEquals(m1, roots.get(1).getFile());
    assertEquals(1, roots.get(1).getModules().size());
    assertEquals(m2, roots.get(1).getModules().get(0).getFile());
  }

  public void testUpdatingAddsModulesFromRootProjects() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>");

    VirtualFile m = createModulePom("m",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>m</artifactId>" +
                                    "<version>1</version>");

    readModel(myProjectPom, m);

    List<MavenProjectModel> roots = model.getRootProjects();
    assertEquals(2, roots.size());
    assertEquals(myProjectPom, roots.get(0).getFile());
    assertEquals(m, roots.get(1).getFile());
    assertEquals("m", roots.get(1).getMavenProject().getArtifactId());
    assertEquals(0, roots.get(0).getModules().size());

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    model.update(myProjectPom, getMavenCoreSettings(), new MavenProcess(new EmptyProgressIndicator()));

    roots = model.getRootProjects();
    assertEquals(1, roots.size());
    assertEquals(myProjectPom, roots.get(0).getFile());
    assertEquals(1, roots.get(0).getModules().size());
    assertEquals(m, roots.get(0).getModules().get(0).getFile());
  }

  public void testDeletingProject() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    VirtualFile m = createModulePom("m",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>m</artifactId>" +
                                    "<version>1</version>");

    readModel(myProjectPom);

    List<MavenProjectModel> roots = model.getRootProjects();
    assertEquals(1, roots.size());
    assertEquals(1, roots.get(0).getModules().size());

    model.delete(m, getMavenCoreSettings(), new MavenProcess(new EmptyProgressIndicator()));

    roots = model.getRootProjects();
    assertEquals(1, roots.size());
    assertEquals(0, roots.get(0).getModules().size());
  }

  public void testDeletingProjectWithModules() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "</modules>");

    VirtualFile m1 = createModulePom("m1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m1</artifactId>" +
                                     "<version>1</version>" +
                                     "<packaging>pom</packaging>" +

                                     "<modules>" +
                                     "  <module>m2</module>" +
                                     "</modules>");

    createModulePom("m1/m2",
                    "<groupId>test</groupId>" +
                    "<artifactId>m2</artifactId>" +
                    "<version>1</version>");

    readModel(myProjectPom);

    List<MavenProjectModel> roots = model.getRootProjects();
    assertEquals(1, roots.size());
    assertEquals(1, roots.get(0).getModules().size());
    assertEquals(1, roots.get(0).getModules().get(0).getModules().size());

    model.delete(m1, getMavenCoreSettings(), new MavenProcess(new EmptyProgressIndicator()));

    roots = model.getRootProjects();
    assertEquals(1, roots.size());
    assertEquals(myProjectPom, roots.get(0).getFile());
    assertEquals(0, roots.get(0).getModules().size());
  }

  private void readModel(VirtualFile... files) throws CanceledException, MavenException {
    readModel(Collections.<String>emptyList(), files);
  }

  private void readModel(List<String> profiles, VirtualFile... files) throws CanceledException, MavenException {
    model.read(Arrays.asList(files),
               Collections.<VirtualFile, Module>emptyMap(),
               profiles,
               getMavenCoreSettings(),
               getMavenImporterSettings(),
               new MavenProcess(new EmptyProgressIndicator()));
  }
}
