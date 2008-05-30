package org.jetbrains.idea.maven;

import org.jetbrains.idea.maven.project.MavenProjectModel;

import java.util.List;

public class InvalidProjectImportingTest extends MavenImportingTestCase {
  public void testUnknownProblem() throws Exception {
    importProject("");
    assertModules("project");

    MavenProjectModel.Node root = getRootProjects().get(0);
    assertFalse(root.isValid());
    assertProblems(root, "Invalid Maven Model");
  }

  public void testUndefinedPropertyInHeader() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>${undefined}</artifactId>" +
                  "<version>1</version>");

    assertModules("project");
    MavenProjectModel.Node root = getRootProjects().get(0);
    assertFalse(root.isValid());
    assertProblems(root, "Invalid Maven Model", "'artifactId' with value '${undefined}' does not match a valid id pattern.");
  }

  public void testUnresolvedParent() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<parent>" +
                  "  <groupId>test</groupId>" +
                  "  <artifactId>parent</artifactId>" +
                  "  <version>1</version>" +
                  "</parent>");

    assertModules("project");

    MavenProjectModel.Node root = getRootProjects().get(0);
    assertTrue(root.isValid());

    assertProblems(root, "Parent 'test:parent:pom:1' not found");
  }

  public void testNonExistentModules() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<packaging>pom</packaging>" +

                  "<modules>" +
                  "  <module>foo</module>" +
                  "</modules>");

    assertModules("project");

    MavenProjectModel.Node root = getRootProjects().get(0);
    assertTrue(root.isValid());

    assertProblems(root, "Missing module: 'foo'");
  }

  public void testInvalidProjectModel() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>jar</packaging>" + // invalid packaging

                     "<modules>" +
                     "  <module>foo</module>" +
                     "</modules>");

    createModulePom("foo", "<groupId>test</groupId>" +
                           "<artifactId>foo</artifactId>" +
                           "<version>1</version>");
    importProject();
    assertModules("project", "foo");

    MavenProjectModel.Node root = getRootProjects().get(0);
    assertFalse(root.isValid());
    assertProblems(root, "Invalid Maven Model", "Packaging 'jar' is invalid. Aggregator projects require 'pom' as packaging.");
  }

  public void testInvalidModuleModel() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>foo</module>" +
                     "</modules>");

    createModulePom("foo", "<groupId>test</groupId>" +
                           "<artifactId>foo</artifactId>" +
                           "<version>1"); //  invalid tag

    importProject();
    assertModules("project", "foo");

    MavenProjectModel.Node root = getRootProjects().get(0);
    assertTrue(root.isValid());
    assertProblems(root);

    assertFalse(root.getSubProjects().get(0).isValid());
    assertProblems(root.getSubProjects().get(0), "Invalid Maven Model");
  }

  public void testSeveratInvalidModulesAndWithSameName() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>foo</module>" +
                     "  <module>bar</module>" +
                     "  <module>bar/bar</module>" +
                     "</modules>");

    createModulePom("foo", "<groupId>test</groupId>" +
                           "<artifactId>foo</artifactId>" +
                           "<version>1"); //  invalid tag

    createModulePom("bar", "<groupId>test</groupId>" +
                           "<artifactId>bar</artifactId>" +
                           "<version>1"); //  invalid tag

    createModulePom("bar/bar", "<groupId>test</groupId>" +
                               "<artifactId>bar-bar</artifactId>" +
                               "<version>1"); //  invalid tag

    importProject();
    assertModules("project", "foo", "bar (1)", "bar (2)");
  }

  public void testInvalidProjectWithModules() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1" + // invalid tag

                     "<modules>" +
                     "  <module>foo</module>" +
                     "</modules>");

    createModulePom("foo", "<groupId>test</groupId>" +
                           "<artifactId>foo</artifactId>" +
                           "<version>1</version>");

    importProject();

    assertModules("project");
  }

  public void testNonPOMProjectWithModules() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>foo</module>" +
                     "</modules>");

    createModulePom("foo", "<groupId>test</groupId>" +
                           "<artifactId>foo</artifactId>" +
                           "<version>1</version>");

    importProject();

    assertModules("project", "foo");
  }

  public void testInvalidRepositoryLayout() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<distributionManagement>" +
                  "  <repository>" +
                  "    <id>test</id>" +
                  "    <url>http://www.google.com</url>" +
                  "    <layout>nothing</layout>" + // invalid layout
                  "  </repository>" +
                  "</distributionManagement>");

    assertModules("project");

    MavenProjectModel.Node root = getRootProjects().get(0);
    assertFalse(root.isValid());
  }

  public void testUnresolvedDependencies() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>xxx</groupId>" +
                          "    <artifactId>xxx</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "  <dependency>" +
                          "    <groupId>yyy</groupId>" +
                          "    <artifactId>yyy</artifactId>" +
                          "    <version>2</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>zzz</groupId>" +
                          "    <artifactId>zzz</artifactId>" +
                          "    <version>3</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    importProject();

    MavenProjectModel.Node root = getRootProjects().get(0);
    assertTrue(root.isValid());

    assertProblems(root);

    assertProblems(root.getSubProjects().get(0),
                   "Unresolved dependency: xxx:xxx:jar:1:compile",
                   "Unresolved dependency: yyy:yyy:jar:2:compile");

    assertProblems(root.getSubProjects().get(1),
                   "Unresolved dependency: zzz:zzz:jar:3:compile");
  }

  public void testUnresolvedPomTypeDependency() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>xxx</groupId>" +
                     "    <artifactId>yyy</artifactId>" +
                     "    <version>4.0</version>" +
                     "    <type>pom</type>" +
                     "  </dependency>" +
                     "</dependencies>");

    importProject();

    assertModuleLibDeps("project");

    MavenProjectModel.Node root = getRootProjects().get(0);
    assertTrue(root.isValid());
    assertProblems(root, "Unresolved dependency: xxx:yyy:pom:4.0:compile");
  }

  public void testDoesNotReportInterModuleDependenciesAsUnresolved() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m2</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>");

    importProject();

    MavenProjectModel.Node root = getRootProjects().get(0);
    assertProblems(root);
    assertProblems(root.getSubProjects().get(0));
    assertProblems(root.getSubProjects().get(1));
  }

  public void testCircularDependencies() throws Exception {
    if (ignore()) return;

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "  <module>m3</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m2</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m3</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m3", "<groupId>test</groupId>" +
                          "<artifactId>m3</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m1</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    importProject();

    MavenProjectModel.Node root = getRootProjects().get(0);
    assertTrue(root.isValid());
    assertProblems(root);
    assertProblems(root.getSubProjects().get(0));
    assertProblems(root.getSubProjects().get(1));
    assertProblems(root.getSubProjects().get(2));
  }

  public void testUnresolvedExtensions() throws Exception {
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

    MavenProjectModel.Node root = getRootProjects().get(0);
    assertTrue(root.isValid());
    assertProblems(root, "Unresolved build extension: xxx:yyy:jar:1:runtime");
  }

  public void testUnresolvedBuildExtensionsInModules() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1",
                    "<groupId>test</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>1</version>" +

                    "<build>" +
                    " <extensions>" +
                    "   <extension>" +
                    "     <groupId>xxx</groupId>" +
                    "     <artifactId>xxx</artifactId>" +
                    "     <version>1</version>" +
                    "    </extension>" +
                    "  </extensions>" +
                    "</build>");

    createModulePom("m2",
                    "<groupId>test</groupId>" +
                    "<artifactId>m2</artifactId>" +
                    "<version>1</version>" +

                    "<build>" +
                    " <extensions>" +
                    "   <extension>" +
                    "     <groupId>yyy</groupId>" +
                    "     <artifactId>yyy</artifactId>" +
                    "     <version>1</version>" +
                    "    </extension>" +
                    "   <extension>" +
                    "     <groupId>zzz</groupId>" +
                    "     <artifactId>zzz</artifactId>" +
                    "     <version>1</version>" +
                    "    </extension>" +
                    "  </extensions>" +
                    "</build>");

    importProject();

    MavenProjectModel.Node root = getRootProjects().get(0);
    assertTrue(root.isValid());

    assertProblems(root);
    assertProblems(root.getSubProjects().get(0),
                   "Unresolved build extension: xxx:xxx:jar:1:runtime");
    assertProblems(root.getSubProjects().get(1),
                   "Unresolved build extension: yyy:yyy:jar:1:runtime",
                   "Unresolved build extension: zzz:zzz:jar:1:runtime");
  }

  public void testUnresolvedPlugins() throws Exception {
    if (ignore()) return;

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  " <plugins>" +
                  "   <plugin>" +
                  "     <groupId>xxx</groupId>" +
                  "     <artifactId>yyy</artifactId>" +
                  "     <version>1</version>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    MavenProjectModel.Node root = getRootProjects().get(0);
    assertTrue(root.isValid());

    assertProblems(root, "Unresolved plugin: xxx:yyy:maven-plugin:1:runtime");
  }

  public void testDoNotReportResolvedPlugins() throws Exception {
    if (ignore()) return;
    
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  " <plugins>" +
                  "   <plugin>" +
                  "     <groupId>org.apache.maven.plugins</groupId>" +
                  "     <artifactId>maven-compiler-plugin</artifactId>" +
                  "     <version>1</version>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    MavenProjectModel.Node root = getRootProjects().get(0);
    assertTrue(root.isValid());

    assertProblems(root);
  }

  public void testUnresolvedPluginsAsExtensions() throws Exception {
    if (ignore()) return;

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  " <plugins>" +
                  "   <plugin>" +
                  "     <groupId>xxx</groupId>" +
                  "     <artifactId>yyy</artifactId>" +
                  "     <version>1</version>" +
                  "     <extensions>true</extensions>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    assertModules("project");
    assertTrue(getRootProjects().get(0).isValid());

    MavenProjectModel.Node root = getRootProjects().get(0);
    assertProblems(root, "Unresolved plugin: xxx:yyy:maven-plugin:1:runtime");
  }

  private void assertProblems(MavenProjectModel.Node root, String... problems) {
    assertOrderedElementsAreEqual(root.getProblems(), problems);
  }

  private List<MavenProjectModel.Node> getRootProjects() {
    return myMavenProjectsManager.getMavenProjectModel().getRootProjects();
  }
}
