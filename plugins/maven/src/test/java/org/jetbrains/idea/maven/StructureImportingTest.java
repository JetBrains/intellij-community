package org.jetbrains.idea.maven;

import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.idea.maven.navigator.MavenTreeStructure;

import java.io.File;

public class StructureImportingTest extends MavenImportingTestCase {
  public void testUsingRelativePathForTheProject() throws Exception {
    assertFalse(((ProjectEx)myProject).isSavePathsRelative());

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertTrue(((ProjectEx)myProject).isSavePathsRelative());
  }

  public void testUsingRelativePathForModules() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertTrue(getModule("project").isSavePathsRelative());
  }

  public void testModulesWithSlashesRegularAndBack() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>dir\\m1</module>" +
                     "  <module>dir/m2</module>" +
                     "</modules>");

    createModulePom("dir/m1", "<groupId>test</groupId>" +
                              "<artifactId>m1</artifactId>" +
                              "<version>1</version>");

    createModulePom("dir/m2", "<groupId>test</groupId>" +
                              "<artifactId>m2</artifactId>" +
                              "<version>1</version>");

    importProject();
    assertModules("project", "m1", "m2");

    MavenTreeStructure.RootNode r = createMavenTree();

    assertEquals(1, r.pomNodes.size());
    assertEquals("project", r.pomNodes.get(0).getId());

    assertEquals(2, r.pomNodes.get(0).modulePomsNode.pomNodes.size());
  }

  public void testModulesAreNamedAfterArtifactIds() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +
                     "<name>name</name>" +

                     "<modules>" +
                     "  <module>dir1</module>" +
                     "  <module>dir2</module>" +
                     "</modules>");

    createModulePom("dir1", "<groupId>test</groupId>" +
                            "<artifactId>m1</artifactId>" +
                            "<version>1</version>" +
                            "<name>name1</name>");

    createModulePom("dir2", "<groupId>test</groupId>" +
                            "<artifactId>m2</artifactId>" +
                            "<version>1</version>" +
                            "<name>name2</name>");
    importProject();
    assertModules("project", "m1", "m2");
  }

  public void testModulesWithSlashesAtTheEnds() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1/</module>" +
                     "  <module>m2\\</module>" +
                     "  <module>m3//</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>");

    createModulePom("m3", "<groupId>test</groupId>" +
                          "<artifactId>m3</artifactId>" +
                          "<version>1</version>");

    importProject();
    assertModules("project", "m1", "m2", "m3");
  }

  public void testModulesWithSameArtifactId() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>dir1/m</module>" +
                     "  <module>dir2/m</module>" +
                     "</modules>");

    createModulePom("dir1/m", "<groupId>test.group1</groupId>" +
                              "<artifactId>m</artifactId>" +
                              "<version>1</version>");

    createModulePom("dir2/m", "<groupId>test.group2</groupId>" +
                              "<artifactId>m</artifactId>" +
                              "<version>1</version>");

    importProject();
    assertModules("project", "m (test.group1)", "m (test.group2)");
  }

  public void testModuleWithRelativePath() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>../m</module>" +
                     "</modules>");

    createModulePom("../m", "<groupId>test</groupId>" +
                            "<artifactId>m</artifactId>" +
                            "<version>1</version>");

    importProject();
    assertModules("project", "m");
  }

  public void testModuleWithRelativeParent() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<parent>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>parent</artifactId>" +
                     "  <version>1</version>" +
                     "  <relativePath>../parent</relativePath>" +
                     "</parent>");

    createModulePom("../parent", "<groupId>test</groupId>" +
                                 "<artifactId>parent</artifactId>" +
                                 "<version>1</version>");

    importProject();
    assertModules("project");
  }

  public void testModulePathsAsProperties() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <module1>m1</module1>" +
                     "  <module2>m2</module2>" +
                     "</properties>" +

                     "<modules>" +
                     "  <module>${module1}</module>" +
                     "  <module>${module2}</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>");

    importProject();
    assertModules("project", "m1", "m2");

    MavenTreeStructure.RootNode r = createMavenTree();

    assertEquals(1, r.pomNodes.size());
    assertEquals("project", r.pomNodes.get(0).getId());

    assertEquals(2, r.pomNodes.get(0).modulePomsNode.pomNodes.size());
    assertEquals("m1", r.pomNodes.get(0).modulePomsNode.pomNodes.get(0).getId());
    assertEquals("m2", r.pomNodes.get(0).modulePomsNode.pomNodes.get(1).getId());
  }

  public void testParentWithoutARelativePath() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <moduleName>m1</moduleName>" +
                     "</properties>" +

                     "<modules>" +
                     "  <module>modules/m</module>" +
                     "</modules>");

    createModulePom("modules/m", "<groupId>test</groupId>" +
                                 "<artifactId>${moduleName}</artifactId>" +
                                 "<version>1</version>" +

                                 "<parent>" +
                                 "  <groupId>test</groupId>" +
                                 "  <artifactId>project</artifactId>" +
                                 "  <version>1</version>" +
                                 "</parent>");

    importProject();
    assertModules("project", "m1");

    MavenTreeStructure.RootNode r = createMavenTree();

    assertEquals(1, r.pomNodes.size());
    assertEquals("project", r.pomNodes.get(0).getId());

    assertEquals(1, r.pomNodes.get(0).modulePomsNode.pomNodes.size());
    assertEquals("m1", r.pomNodes.get(0).modulePomsNode.pomNodes.get(0).getId());
  }

  public void testModuleWithPropertiesWithParentWithoutARelativePath() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <moduleName>m1</moduleName>" +
                     "</properties>" +

                     "<modules>" +
                     "  <module>modules/m</module>" +
                     "</modules>");

    createModulePom("modules/m", "<groupId>test</groupId>" +
                                 "<artifactId>${moduleName}</artifactId>" +
                                 "<version>1</version>" +

                                 "<parent>" +
                                 "  <groupId>test</groupId>" +
                                 "  <artifactId>project</artifactId>" +
                                 "  <version>1</version>" +
                                 "</parent>");

    importProject();
    assertModules("project", "m1");

    MavenTreeStructure.RootNode r = createMavenTree();

    assertEquals(1, r.pomNodes.size());
    assertEquals("project", r.pomNodes.get(0).getId());

    assertEquals(1, r.pomNodes.get(0).modulePomsNode.pomNodes.size());
    assertEquals("m1", r.pomNodes.get(0).modulePomsNode.pomNodes.get(0).getId());
  }

  public void testParentInRepository() throws Exception {
    VirtualFile parent = createModulePom("parent",
                                         "<groupId>test</groupId>" +
                                         "<artifactId>parent</artifactId>" +
                                         "<version>1</version>" +

                                         "<dependencies>" +
                                         "  <dependency>" +
                                         "    <groupId>junit</groupId>" +
                                         "    <artifactId>junit</artifactId>" +
                                         "    <version>4.0</version>" +
                                         "  </dependency>" +
                                         "</dependencies>");
    executeGoal("parent", "install");
    parent.delete(null);

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>m</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<parent>" +
                     "  <groupId>test</groupId>" +
                     "  <artifactId>parent</artifactId>" +
                     "  <version>1</version>" +
                     "</parent>");

    importProject();
    assertModules("m");
    assertModuleLibDeps("m", "junit:junit:4.0");
  }

  public void testCreatingModuleGroups() throws Exception {
    VirtualFile p1 = createModulePom("project1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>project1</artifactId>" +
                                     "<version>1</version>" +
                                     "<packaging>pom</packaging>" +

                                     "<modules>" +
                                     "  <module>m1</module>" +
                                     "</modules>");

    createModulePom("project1/m1",
                    "<groupId>test</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>1</version>");

    VirtualFile p2 = createModulePom("project2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>project2</artifactId>" +
                                     "<version>1</version>" +
                                     "<packaging>pom</packaging>" +

                                     "<modules>" +
                                     "  <module>m2</module>" +
                                     "</modules>");

    createModulePom("project2/m2",
                    "<groupId>test</groupId>" +
                    "<artifactId>m2</artifactId>" +
                    "<version>1</version>" +
                    "<packaging>pom</packaging>" +

                    "<modules>" +
                    "  <module>m3</module>" +
                    "</modules>");

    createModulePom("project2/m2/m3",
                    "<groupId>test</groupId>" +
                    "<artifactId>m3</artifactId>" +
                    "<version>1</version>");

    getMavenImporterSettings().setCreateModuleGroups(true);
    importSeveralProjects(p1, p2);
    assertModules("project1", "project2", "m1", "m2", "m3");

    assertModuleGroupPath("project1", "project1 and modules");
    assertModuleGroupPath("m1", "project1 and modules");
    assertModuleGroupPath("project2", "project2 and modules");
    assertModuleGroupPath("m2", "project2 and modules", "m2 and modules");
    assertModuleGroupPath("m3", "project2 and modules", "m2 and modules");
  }

  public void testDoesNotCreateUnnecessaryTopLevelModuleGroup() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "</modules>");

    createModulePom("m1",
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

    getMavenImporterSettings().setCreateModuleGroups(true);
    importProject();
    assertModules("project", "m1", "m2");

    assertModuleGroupPath("project");
    assertModuleGroupPath("m1", "m1 and modules");
    assertModuleGroupPath("m2", "m1 and modules");
  }

  public void testLanguageLevel() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.apache.maven.plugins</groupId>" +
                  "      <artifactId>maven-compiler-plugin</artifactId>" +
                  "      <configuration>" +
                  "        <source>1.4</source>" +
                  "      </configuration>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    assertModules("project");
    assertEquals(LanguageLevel.JDK_1_4, getLanguageLevelForProject());
  }

  public void testLanguageLevelWhenCompilerPluginIsNotSpecified() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertModules("project");
    assertNull(getLanguageLevelForProject());
  }

  public void testLanguageLevelWhenConfigurationIsNotSpecified() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.apache.maven.plugins</groupId>" +
                  "      <artifactId>maven-compiler-plugin</artifactId>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    assertModules("project");
    assertNull(getLanguageLevelForProject());
  }

  public void testLanguageLevelWhenSourseLanguageLevelIsNotSpecified() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.apache.maven.plugins</groupId>" +
                  "      <artifactId>maven-compiler-plugin</artifactId>" +
                  "      <configuration>" +
                  "      </configuration>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    assertModules("project");
    assertNull(getLanguageLevelForProject());
  }

  private LanguageLevel getLanguageLevelForProject() {
    return LanguageLevelModuleExtension.getInstance(getModule("project")).getLanguageLevel();
  }

  public void testProjectWithBuiltExtension() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  " <extensions>" +
                  "   <extension>" +
                  "     <groupId>org.apache.maven.wagon</groupId>" +
                  "     <artifactId>wagon-webdav</artifactId>" +
                  "     <version>1.0-beta-2</version>" +
                  "    </extension>" +
                  "  </extensions>" +
                  "</build>");
    assertModules("project");
  }

  public void testProjectWithInvalidBuildExtension() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  " <extensions>" +
                  "   <extension>" +
                  "     <groupId>xxx</groupId>" +
                  "     <artifactId>yyy</artifactId>" +
                  "     <version>1</version>" +
                  "    </extension>" +
                  "  </extensions>" +
                  "</build>");
    assertModules("project"); // shouldn't throw any exception
  }

  public void testUsingPropertyInBuildExtensionsOfChildModule() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<properties>" +
                     "  <xxx>1.0-beta-2</xxx>" +
                     "</properties>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>"
    );

    createModulePom("m", "<groupId>test</groupId>" +
                         "<artifactId>m</artifactId>" +

                         "<parent>" +
                         "  <groupId>test</groupId>" +
                         "  <artifactId>project</artifactId>" +
                         "  <version>1</version>" +
                         "</parent>" +

                         "<build>" +
                         "  <extensions>" +
                         "    <extension>" +
                         "      <groupId>org.apache.maven.wagon</groupId>" +
                         "      <artifactId>wagon-webdav</artifactId>" +
                         "      <version>${xxx}</version>" +
                         "    </extension>" +
                         "  </extensions>" +
                         "</build>");

    importProject();
    assertModules("project", "m");
  }

  public void testProjectWithProfilesXmlFile() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>junit</groupId>" +
                     "    <artifactId>junit</artifactId>" +
                     "    <version>${junit.version}</version>" +
                     "  </dependency>" +
                     "</dependencies>");

    createProfilesXml("<profile>" +
                      "  <id>one</id>" +
                      "  <activation>" +
                      "    <activeByDefault>false</activeByDefault>" +
                      "  </activation>" +
                      "  <properties>" +
                      "    <junit.version>4.0</junit.version>" +
                      "  </properties>" +
                      "</profile>" +

                      "<profile>" +
                      "  <id>two</id>" +
                      "  <activation>" +
                      "    <activeByDefault>false</activeByDefault>" +
                      "  </activation>" +
                      "  <properties>" +
                      "    <junit.version>3.8.1</junit.version>" +
                      "  </properties>" +
                      "</profile>");

    importProjectWithProfiles("one");
    assertModules("project");

    assertModuleLibDep("project", "junit:junit:4.0");

    importProjectWithProfiles("two");
    assertModules("project");

    assertModuleLibDep("project", "junit:junit:3.8.1");
  }

  public void testProjectWithDefaultProfileInProfilesXmlFile() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>junit</groupId>" +
                     "    <artifactId>junit</artifactId>" +
                     "    <version>${junit.version}</version>" +
                     "  </dependency>" +
                     "</dependencies>");

    createProfilesXml("<profile>" +
                      "  <id>one</id>" +
                      "  <activation>" +
                      "    <activeByDefault>true</activeByDefault>" +
                      "  </activation>" +
                      "  <properties>" +
                      "    <junit.version>4.0</junit.version>" +
                      "  </properties>" +
                      "</profile>");

    importProject();
    assertModules("project");

    assertModuleLibDep("project", "junit:junit:4.0");
  }

  public void testRefreshFSAfterImport() throws Exception {
    myProjectRoot.getChildren(); // make sure fs is cached
    new File(myProjectRoot.getPath(), "foo").mkdirs();

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertNotNull(myProjectRoot.findChild("foo"));
  }
}
