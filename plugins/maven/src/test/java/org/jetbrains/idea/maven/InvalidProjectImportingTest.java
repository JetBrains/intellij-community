package org.jetbrains.idea.maven;

import org.jetbrains.idea.maven.project.MavenProjectModel;
import org.jetbrains.idea.maven.project.MavenProjectModelProblem;
import org.jetbrains.idea.maven.indices.MavenCustomRepositoryTestFixture;

import java.util.ArrayList;
import java.util.List;

public class InvalidProjectImportingTest extends MavenImportingTestCase {
  public void testUnknownProblem() throws Exception {
    importProject("");
    assertModules("project");

    MavenProjectModel root = getRootProjects().get(0);
    assertProblems(root, false, "Pom file has syntax errors.");
  }

  public void testUndefinedPropertyInHeader() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>${undefined}</artifactId>" +
                  "<version>1</version>");

    assertModules("project");
    MavenProjectModel root = getRootProjects().get(0);
    assertProblems(root, false, "'artifactId' with value '${undefined}' does not match a valid id pattern.");
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

    MavenProjectModel root = getRootProjects().get(0);
    assertProblems(root, true, "Parent 'test:parent:1' not found.");
  }

  public void testUnresolvedParentForInvalidProject() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<parent>" +
                  "  <groupId>test</groupId>" +
                  "  <artifactId>parent</artifactId>" +
                  "  <version>1</version>" +
                  "</parent>" +

                  // not of the 'pom' type
                  "<modules>" +
                  "  <module>foo</module>" +
                  "</modules>");

    MavenProjectModel root = getRootProjects().get(0);
    assertProblems(root, false,
                   "Parent 'test:parent:1' not found.",
                   "Packaging 'jar' is invalid. Aggregator projects require 'pom' as packaging.",
                   "Missing module: 'foo'.");
  }

  public void testMissingModules() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<packaging>pom</packaging>" +

                  "<modules>" +
                  "  <module>foo</module>" +
                  "</modules>");

    assertModules("project");

    MavenProjectModel root = getRootProjects().get(0);
    assertProblems(root, true, "Missing module: 'foo'.");
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

    MavenProjectModel root = getRootProjects().get(0);
    assertProblems(root, false, "Packaging 'jar' is invalid. Aggregator projects require 'pom' as packaging.");
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

    MavenProjectModel root = getRootProjects().get(0);
    assertProblems(root, true);

    assertProblems(getModules(root).get(0), false, "Pom file has syntax errors.");
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

    MavenProjectModel root = getRootProjects().get(0);
    assertProblems(root, false, "Cannot find ArtifactRepositoryLayout instance for: nothing");
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

    MavenProjectModel root = getRootProjects().get(0);

    assertProblems(root, true);
    assertProblems(getModules(root).get(0), true,
                   "Unresolved dependency: 'xxx:xxx:jar:1:compile'.",
                   "Unresolved dependency: 'yyy:yyy:jar:2:compile'.");
    assertProblems(getModules(root).get(1), true,
                   "Unresolved dependency: 'zzz:zzz:jar:3:compile'.");
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

    MavenProjectModel root = getRootProjects().get(0);
    assertProblems(root, true, "Unresolved dependency: 'xxx:yyy:pom:4.0:compile'.");
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

    MavenProjectModel root = getRootProjects().get(0);
    assertProblems(root, true);
    assertProblems(getModules(root).get(0), true);
    assertProblems(getModules(root).get(1), true);
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

    MavenProjectModel root = getRootProjects().get(0);
    assertProblems(root, true);
    assertProblems(getModules(root).get(0), true);
    assertProblems(getModules(root).get(1), true);
    assertProblems(getModules(root).get(2), true);
  }

  public void testUnresolvedExtensionsAfterImport() throws Exception {
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

    MavenProjectModel root = getRootProjects().get(0);
    assertProblems(root, true, "Unresolved build extension: 'xxx:yyy:jar:1:runtime'.");
  }

  public void testUnresolvedExtensionsAfterResolve() throws Exception {
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

    resolveProject();
    MavenProjectModel root = getRootProjects().get(0);
    assertProblems(root, true, "Unresolved build extension: 'xxx:yyy:jar:1:runtime'.");
  }

  public void testDoesNotReportExtensionsThatWereNotTriedToBeResolved() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  // for some reasons this plugins is not rtied to be resolved by embedder.
                  // we shouldn't report it as unresolved.
                  "<build>" +
                  "  <extensions>" +
                  "   <extension>" +
                  "      <groupId>org.apache.maven.wagon</groupId>" +
                  "      <artifactId>wagon-ssh-external</artifactId>" +
                  "      <version>1.0-alpha-6</version>" +
                  "    </extension>" +
                  "  </extensions>" +
                  "</build>");

    assertProblems(getRootProjects().get(0), true);

    resolveProject();
    assertProblems(getRootProjects().get(0), true);
  }

  public void testDoesNotReportExtensionsThatDoNotHaveJarFiles() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  // for some reasons this plugins is not rtied to be resolved by embedder.
                  // we shouldn't report it as unresolved.
                  "<build>" +
                  "  <extensions>" +
                  "   <extension>" +
                  "      <groupId>org.apache.maven.wagon</groupId>" +
                  "      <artifactId>wagon</artifactId>" +
                  "      <version>1.0-alpha-6</version>" +
                  "    </extension>" +
                  "  </extensions>" +
                  "</build>");

    assertProblems(getRootProjects().get(0), true);

    resolveProject();
    assertProblems(getRootProjects().get(0), true);
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

    MavenProjectModel root = getRootProjects().get(0);

    assertProblems(root, true);
    assertProblems(getModules(root).get(0), true,
                   "Unresolved build extension: 'xxx:xxx:jar:1:runtime'.");
    assertProblems(getModules(root).get(1), true,
                   "Unresolved build extension: 'yyy:yyy:jar:1:runtime'.",
                   "Unresolved build extension: 'zzz:zzz:jar:1:runtime'.");
  }

  public void testUnresolvedPlugins() throws Exception {
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

    MavenProjectModel root = getRootProjects().get(0);
    assertProblems(root, true, "Unresolved plugin: 'xxx:yyy:1'.");
  }

  public void testDoNotReportResolvedPlugins() throws Exception {
    MavenCustomRepositoryTestFixture fixture = new MavenCustomRepositoryTestFixture(myDir, "plugins");
    fixture.setUp();

    setRepositoryPath(fixture.getTestDataPath("plugins"));

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  " <plugins>" +
                  "   <plugin>" +
                  "     <groupId>org.apache.maven.plugins</groupId>" +
                  "     <artifactId>maven-compiler-plugin</artifactId>" +
                  "     <version>2.0.2</version>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    assertProblems(getRootProjects().get(0), true);
  }

  public void testUnresolvedPluginsAsExtensions() throws Exception {
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

    MavenProjectModel root = getRootProjects().get(0);
    assertProblems(root, true, "Unresolved plugin: 'xxx:yyy:1'.");
  }

  private void assertProblems(MavenProjectModel project, boolean isValid, String... expectedProblems) {
    assertEquals(isValid, project.isValid());

    List<String> actualProblems = new ArrayList<String>();
    for (MavenProjectModelProblem each : project.getProblems()) {
      actualProblems.add(each.getDescription());
    }
    assertOrderedElementsAreEqual(actualProblems, expectedProblems);
  }

  private List<MavenProjectModel> getRootProjects() {
    return myMavenTree.getRootProjects();
  }

  private List<MavenProjectModel> getModules(MavenProjectModel p) {
    return myMavenTree.getModules(p);
  }
}
