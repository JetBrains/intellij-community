package org.jetbrains.idea.maven;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.idea.maven.navigator.PomTreeStructure;

import java.io.IOException;

public class BasicProjectImportingTest extends ProjectImportingTestCase {
  public void testProjectWithDependency() throws IOException {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertModules("project");
    assertModuleLibDep("project", "junit:junit:4.0",
                       "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar!/");
  }

  public void testProjectWithEnvironmentProperty() throws IOException {
    String javaHome = FileUtil.toSystemIndependentName(System.getProperty("java.home"));

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>direct-system-dependency</groupId>" +
                  "    <artifactId>direct-system-dependency</artifactId>" +
                  "    <version>1.0</version>" +
                  "    <scope>system</scope>" +
                  "    <systemPath>${java.home}/lib/tools.jar</systemPath>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertModules("project");
    assertModuleLibDep("project",
                       "direct-system-dependency:direct-system-dependency:1.0",
                       "jar://" + javaHome + "/lib/tools.jar!/");
  }

  public void testProjectWithEnvironmentENVProperty() throws IOException {
    String ideaJDK = FileUtil.toSystemIndependentName(System.getenv("IDEA_JDK"));

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>direct-system-dependency</groupId>" +
                  "    <artifactId>direct-system-dependency</artifactId>" +
                  "    <version>1.0</version>" +
                  "    <scope>system</scope>" +
                  "    <systemPath>${env.IDEA_JDK}/lib/tools.jar</systemPath>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertModules("project");
    assertModuleLibDep("project",
                       "direct-system-dependency:direct-system-dependency:1.0",
                       "jar://" + ideaJDK + "/lib/tools.jar!/");
  }

  public void testModulesWithSlashesRegularAndBack() throws IOException {
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

    PomTreeStructure.RootNode r = createMavenTree();

    assertEquals(1, r.pomNodes.size());
    assertEquals("project", r.pomNodes.get(0).mavenProject.getArtifactId());

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

  public void testTestJarDependencies() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "   <dependency>" +
                  "    <groupId>group</groupId>" +
                  "    <artifactId>artifact</artifactId>" +
                  "    <type>test-jar</type>" +
                  "    <version>1</version>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertModules("project");
    assertModuleLibDeps("project", "group:artifact:1:tests");
  }

  public void testDependencyWithClassifier() throws IOException {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "   <dependency>" +
                  "    <groupId>group</groupId>" +
                  "    <artifactId>artifact</artifactId>" +
                  "    <classifier>bar</classifier>" +
                  "    <version>1</version>" +
                  "  </dependency>" +
                  "</dependencies>");
    assertModules("project");
    assertModuleLibDeps("project", "group:artifact:1:bar");
  }

  public void testSnapshotDependencyToLibrary() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<repositories>" +
                     "  <repository>" +
                     "    <id>internal</id>" +
                     "    <url>file://" + getParentPath() + "</url>" +
                     "  </repository>" +
                     "</repositories>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>someGroup</groupId>" +
                     "    <artifactId>someArtifact</artifactId>" +
                     "    <version>1-SNAPSHOT</version>" +
                     "  </dependency>" +
                     "</dependencies>");

    putArtefactInLocalRepository("someGroup", "someArtifact", "1-SNAPSHOT", "20000101120000", "1");
    importProject();

    assertModules("project");
    assertModuleLibDeps("project", "someGroup:someArtifact:1-20000101120000-1");
  }

  public void testSnapshotDependencyToModule() throws Exception {
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

                          "<repositories>" +
                          "  <repository>" +
                          "    <id>internal</id>" +
                          "    <url>file://" + getParentPath() + "</url>" +
                          "  </repository>" +
                          "</repositories>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m2</artifactId>" +
                          "    <version>1-SNAPSHOT</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1-SNAPSHOT</version>");

    putArtefactInLocalRepository("test", "m2", "1-SNAPSHOT", "20000101120000", "2");
    importProject();

    assertModules("project", "m1", "m2");
    assertModuleModuleDeps("m1", "m2");
  }

  public void testDependencyWithVersionRangeOnModule() throws Exception {
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
                          "    <version>[1, 3]</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>2</version>");

    importProject();
    assertModules("project", "m1", "m2");

    // commented until the problem will be solved...
    //assertModuleModuleDeps("m1", "m2");
    //assertModuleLibDeps("m1");
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
    assertEquals(LanguageLevel.JDK_1_4, getModule("project").getLanguageLevel());
  }

  public void testLanguageLevelWhenCompilerPluginIsNotSpecified() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertModules("project");
    assertNull(getModule("project").getLanguageLevel());
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
    assertNull(getModule("project").getLanguageLevel());
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
    assertNull(getModule("project").getLanguageLevel());
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

  public void testUsingPropertyInBuildExtensionsOfChildModule() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <xxx>123</xxx>" +
                     "</properties>" +
                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

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
                         "      <groupId>some.groupId</groupId>" +
                         "      <artifactId>some.artifactId</artifactId>" +
                         "      <version>${xxx}</version>" +
                         "    </extension>" +
                         "  </extensions>" +
                         "</build>");

    importProject();

    // will fail when bug in embedder is fixed
    assertModules();
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

    assertModuleLibDep("project", "junit:junit:4.0",
                       "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar!/");

    importProjectWithProfiles("two");
    assertModules("project");

    assertModuleLibDep("project", "junit:junit:3.8.1",
                       "jar://" + getRepositoryPath() + "/junit/junit/3.8.1/junit-3.8.1.jar!/");
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

    assertModuleLibDep("project", "junit:junit:4.0",
                       "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar!/");
  }
}
