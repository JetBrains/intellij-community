package org.jetbrains.idea.maven.project;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.MavenImportingTestCase;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class MavenProjectsManagerTest extends MavenImportingTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMavenProjectsManager(true);
  }

  public void testShouldReturnNullForUnprocessedFiles() throws Exception {
    // this pom file doesn't belong to any of the modules, this is won't be processed
    // by MavenProjectProjectsManager and won't occur in its projects list.
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");

    // shouldn't throw
    assertNull(myMavenProjectsManager.findProject(myProjectPom));
  }

  public void testUpdatingProjectsWhenAbsentManagedProjectFileAppears() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>parent</artifactId>" +
                  "<version>1</version>" +

                  "<modules>" +
                  "  <module>m</module>" +
                  "</modules>");

    assertEquals(1, myMavenTree.getRootProjects().size());

    myProjectPom.delete(this);
    waitForFullReadingCompletion();

    assertEquals(0, myMavenTree.getRootProjects().size());

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");
    waitForFullReadingCompletion();

    assertEquals(1, myMavenTree.getRootProjects().size());
  }

  public void testUpdatingProjectsWhenRenaming() throws Exception {
    VirtualFile p1 = createModulePom("project1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>project1</artifactId>" +
                                     "<version>1</version>");

    VirtualFile p2 = createModulePom("project2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>project2</artifactId>" +
                                     "<version>1</version>");
    importProjects(p1, p2);

    assertEquals(2, myMavenTree.getRootProjects().size());

    p2.rename(this, "foo.bar");
    waitForFullReadingCompletion();

    assertEquals(1, myMavenTree.getRootProjects().size());

    p2.rename(this, "pom.xml");
    waitForFullReadingCompletion();

    assertEquals(2, myMavenTree.getRootProjects().size());
  }

  public void testUpdatingProjectsWhenMoving() throws Exception {
    VirtualFile p1 = createModulePom("project1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>project1</artifactId>" +
                                     "<version>1</version>");

    VirtualFile p2 = createModulePom("project2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>project2</artifactId>" +
                                     "<version>1</version>");
    importProjects(p1, p2);

    VirtualFile oldDir = p2.getParent();
    VirtualFile newDir = myProjectRoot.createChildDirectory(this, "foo");

    assertEquals(2, myMavenTree.getRootProjects().size());

    p2.move(this, newDir);
    waitForFullReadingCompletion();

    assertEquals(1, myMavenTree.getRootProjects().size());

    p2.move(this, oldDir);
    waitForFullReadingCompletion();

    assertEquals(2, myMavenTree.getRootProjects().size());
  }

  public void testUpdatingProjectsWhenMovingModuleFile() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    VirtualFile m = createModulePom("m1",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>m</artifactId>" +
                                    "<version>1</version>");
    importProject();

    VirtualFile oldDir = m.getParent();
    VirtualFile newDir = myProjectRoot.createChildDirectory(this, "m2");

    assertEquals(1, myMavenTree.getRootProjects().size());
    assertEquals(1, myMavenTree.getModules(myMavenTree.getRootProjects().get(0)).size());

    m.move(this, newDir);
    waitForFullReadingCompletion();

    assertEquals(1, myMavenTree.getModules(myMavenTree.getRootProjects().get(0)).size());

    m.move(this, oldDir);
    waitForFullReadingCompletion();

    assertEquals(1, myMavenTree.getModules(myMavenTree.getRootProjects().get(0)).size());

    m.move(this, myProjectRoot.createChildDirectory(this, "xxx"));
    waitForFullReadingCompletion();

    assertEquals(0, myMavenTree.getModules(myMavenTree.getRootProjects().get(0)).size());
  }

  public void testUpdatingProjectsWhenAbsentModuleFileAppears() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>parent</artifactId>" +
                  "<version>1</version>" +

                  "<modules>" +
                  "  <module>m</module>" +
                  "</modules>");

    List<MavenProject> roots = myMavenTree.getRootProjects();
    MavenProject parentNode = roots.get(0);

    assertNotNull(parentNode);
    assertTrue(myMavenTree.getModules(roots.get(0)).isEmpty());

    VirtualFile m = createModulePom("m",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>m</artifactId>" +
                                    "<version>1</version>");
    waitForFullReadingCompletion();

    List<MavenProject> children = myMavenTree.getModules(roots.get(0));
    assertEquals(1, children.size());
    assertSame(m, children.get(0).getFile());
  }

  public void testAddingManagedFileAndChangingAggregation() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>parent</artifactId>" +
                  "<version>1</version>" +

                  "<modules>" +
                  "  <module>m</module>" +
                  "</modules>");

    VirtualFile m = createModulePom("m",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>m</artifactId>" +
                                    "<version>1</version>");
    waitForFullReadingCompletion();

    assertEquals(1, myMavenTree.getRootProjects().size());
    assertEquals(1, myMavenTree.getModules(myMavenTree.getRootProjects().get(0)).size());

    myMavenProjectsManager.addManagedFiles(Arrays.asList(m));
    waitForFullReadingCompletion();

    assertEquals(1, myMavenTree.getRootProjects().size());
    assertEquals(1, myMavenTree.getModules(myMavenTree.getRootProjects().get(0)).size());

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>1</version>");
    waitForFullReadingCompletion();

    assertEquals(2, myMavenTree.getRootProjects().size());
    assertEquals(0, myMavenTree.getModules(myMavenTree.getRootProjects().get(0)).size());
    assertEquals(0, myMavenTree.getModules(myMavenTree.getRootProjects().get(1)).size());
  }

  public void testUpdatingProjectsOnSettingsXmlChange() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>" +

                     "<build>" +
                     "  <sourceDirectory>${prop}</sourceDirectory>" +
                     "</build>");

    createModulePom("m",
                    "<groupId>test</groupId>" +
                    "<artifactId>m</artifactId>" +
                    "<version>1</version>" +

                    "<parent>" +
                    "  <groupId>test</groupId>" +
                    "  <artifactId>project</artifactId>" +
                    "  <version>1</version>" +
                    "</parent>" +

                    "<build>" +
                    "  <sourceDirectory>${prop}</sourceDirectory>" +
                    "</build>");

    updateSettingsXml("<profiles>" +
                      "  <profile>" +
                      "    <id>one</id>" +
                      "    <activation>" +
                      "      <activeByDefault>true</activeByDefault>" +
                      "    </activation>" +
                      "    <properties>" +
                      "      <prop>value1</prop>" +
                      "    </properties>" +
                      "  </profile>" +
                      "</profiles>");

    importProject();

    List<MavenProject> roots = myMavenTree.getRootProjects();

    MavenProject parentNode = roots.get(0);
    MavenProject childNode = myMavenTree.getModules(roots.get(0)).get(0);

    assertUnorderedElementsAreEqual(parentNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/value1"));
    assertUnorderedElementsAreEqual(childNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/m/value1"));

    updateSettingsXml("<profiles>" +
                      "  <profile>" +
                      "    <id>one</id>" +
                      "    <activation>" +
                      "      <activeByDefault>true</activeByDefault>" +
                      "    </activation>" +
                      "    <properties>" +
                      "      <prop>value2</prop>" +
                      "    </properties>" +
                      "  </profile>" +
                      "</profiles>");
    waitForFullReadingCompletion();

    assertUnorderedElementsAreEqual(parentNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/value2"));
    assertUnorderedElementsAreEqual(childNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/m/value2"));

    deleteSettingsXml();
    waitForFullReadingCompletion();

    assertUnorderedElementsAreEqual(parentNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/${prop}"));
    assertUnorderedElementsAreEqual(childNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/m/${prop}"));

    updateSettingsXml("<profiles>" +
                      "  <profile>" +
                      "    <id>one</id>" +
                      "    <activation>" +
                      "      <activeByDefault>true</activeByDefault>" +
                      "    </activation>" +
                      "    <properties>" +
                      "      <prop>value2</prop>" +
                      "    </properties>" +
                      "  </profile>" +
                      "</profiles>");
    waitForFullReadingCompletion();

    assertUnorderedElementsAreEqual(parentNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/value2"));
    assertUnorderedElementsAreEqual(childNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/m/value2"));
  }

  public void testUpdatingProjectsWhenSettingsXmlLocationIsChanged() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>" +

                     "<build>" +
                     "  <sourceDirectory>${prop}</sourceDirectory>" +
                     "</build>");

    createModulePom("m",
                    "<groupId>test</groupId>" +
                    "<artifactId>m</artifactId>" +
                    "<version>1</version>" +

                    "<parent>" +
                    "  <groupId>test</groupId>" +
                    "  <artifactId>project</artifactId>" +
                    "  <version>1</version>" +
                    "</parent>" +

                    "<build>" +
                    "  <sourceDirectory>${prop}</sourceDirectory>" +
                    "</build>");

    updateSettingsXml("<profiles>" +
                      "  <profile>" +
                      "    <id>one</id>" +
                      "    <activation>" +
                      "      <activeByDefault>true</activeByDefault>" +
                      "    </activation>" +
                      "    <properties>" +
                      "      <prop>value1</prop>" +
                      "    </properties>" +
                      "  </profile>" +
                      "</profiles>");

    importProject();

    List<MavenProject> roots = myMavenTree.getRootProjects();

    MavenProject parentNode = roots.get(0);
    MavenProject childNode = myMavenTree.getModules(roots.get(0)).get(0);

    assertUnorderedElementsAreEqual(parentNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/value1"));
    assertUnorderedElementsAreEqual(childNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/m/value1"));

    getMavenGeneralSettings().setMavenSettingsFile("");
    waitForFullReadingCompletion();

    assertUnorderedElementsAreEqual(parentNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/${prop}"));
    assertUnorderedElementsAreEqual(childNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/m/${prop}"));

    getMavenGeneralSettings().setMavenSettingsFile(new File(myDir, "settings.xml").getPath());
    waitForFullReadingCompletion();

    assertUnorderedElementsAreEqual(parentNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/value1"));
    assertUnorderedElementsAreEqual(childNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/m/value1"));
  }

  public void testUpdatingProjectsOnProfilesXmlChange() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>" +

                     "<build>" +
                     "  <sourceDirectory>${prop}</sourceDirectory>" +
                     "</build>");

    createModulePom("m",
                    "<groupId>test</groupId>" +
                    "<artifactId>m</artifactId>" +
                    "<version>1</version>" +

                    "<parent>" +
                    "  <groupId>test</groupId>" +
                    "  <artifactId>project</artifactId>" +
                    "  <version>1</version>" +
                    "</parent>" +

                    "<build>" +
                    "  <sourceDirectory>${prop}</sourceDirectory>" +
                    "</build>");

    createProfilesXml("<profile>" +
                      "  <id>one</id>" +
                      "  <activation>" +
                      "    <activeByDefault>true</activeByDefault>" +
                      "  </activation>" +
                      "  <properties>" +
                      "    <prop>value1</prop>" +
                      "  </properties>" +
                      "</profile>");

    importProject();

    List<MavenProject> roots = myMavenTree.getRootProjects();

    MavenProject parentNode = roots.get(0);
    MavenProject childNode = myMavenTree.getModules(roots.get(0)).get(0);

    assertUnorderedElementsAreEqual(parentNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/value1"));
    assertUnorderedElementsAreEqual(childNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/m/value1"));

    createProfilesXml("<profile>" +
                      "  <id>one</id>" +
                      "  <activation>" +
                      "    <activeByDefault>true</activeByDefault>" +
                      "  </activation>" +
                      "  <properties>" +
                      "    <prop>value2</prop>" +
                      "  </properties>" +
                      "</profile>");
    waitForFullReadingCompletion();

    assertUnorderedElementsAreEqual(parentNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/value2"));
    assertUnorderedElementsAreEqual(childNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/m/value2"));

    deleteProfilesXml();
    waitForFullReadingCompletion();

    assertUnorderedElementsAreEqual(parentNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/${prop}"));
    assertUnorderedElementsAreEqual(childNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/m/${prop}"));

    createProfilesXml("<profile>" +
                      "  <id>one</id>" +
                      "  <activation>" +
                      "    <activeByDefault>true</activeByDefault>" +
                      "  </activation>" +
                      "  <properties>" +
                      "    <prop>value2</prop>" +
                      "  </properties>" +
                      "</profile>");
    waitForFullReadingCompletion();

    assertUnorderedElementsAreEqual(parentNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/value2"));
    assertUnorderedElementsAreEqual(childNode.getSources(), FileUtil.toSystemDependentName(getProjectPath() + "/m/value2"));
  }

  public void testHandlingDirectoryWithPomFileDeletion() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<packaging>pom</packaging>" +
                  "<version>1</version>");

    createModulePom("dir/module", "<groupId>test</groupId>" +
                                  "<artifactId>module</artifactId>" +
                                  "<version>1</version>");
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>dir/module</module>" +
                     "</modules>");
    waitForFullReadingCompletion();

    assertEquals(2, MavenProjectsManager.getInstance(myProject).getProjects().size());

    VirtualFile dir = myProjectRoot.findChild("dir");
    dir.delete(null);
    waitForFullReadingCompletion();

    assertEquals(1, MavenProjectsManager.getInstance(myProject).getProjects().size());
  }
}
