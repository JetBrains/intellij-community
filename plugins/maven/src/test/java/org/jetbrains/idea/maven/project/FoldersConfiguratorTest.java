package org.jetbrains.idea.maven.project;

import org.jetbrains.idea.maven.MavenImportingTestCase;

import java.io.File;

public class FoldersConfiguratorTest extends MavenImportingTestCase {
  public void testUpdatingExternallyCreatedFolders() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    myProjectRoot.getChildren(); // make sure fs is cached

    new File(myProjectRoot.getPath(), "target/foo").mkdirs();
    updateFolders();

    assertExcludes("project", "target/foo");
    assertNull(myProjectRoot.findChild("target"));
  }

  public void testUpdatingFoldersForAllTheProjects() throws Exception {
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
                    "<version>1</version>");

    createModulePom("m2",
                    "<groupId>test</groupId>" +
                    "<artifactId>m2</artifactId>" +
                    "<version>1</version>");

    importProject();

    assertExcludes("m1");
    assertExcludes("m2");

    new File(myProjectRoot.getPath(), "m1/target/foo").mkdirs();
    new File(myProjectRoot.getPath(), "m2/target/bar").mkdirs();

    updateFolders();

    assertExcludes("m1", "target/foo");
    assertExcludes("m2", "target/bar");
  }
  
  public void testDoesNotTouchSourceFolders() throws Exception {
    createStdProjectFolders();
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertSources("project", "src/main/java", "src/main/resources");
    assertTestSources("project", "src/test/java", "src/test/resources");

    updateFolders();

    assertSources("project", "src/main/java", "src/main/resources");
    assertTestSources("project", "src/test/java", "src/test/resources");
  }

  public void testDoesNotExcludeGeneratedSources() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    new File(myProjectRoot.getPath(), "target/generated-sources").mkdirs();
    new File(myProjectRoot.getPath(), "target/foo").mkdirs();

    updateFolders();

    assertExcludes("project", "target/foo");
  }

  public void testDoesNotExcludeRegisteredSources() throws Exception {
    new File(myProjectRoot.getPath(), "target/src").mkdirs();

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.codehaus.mojo</groupId>" +
                  "      <artifactId>build-helper-maven-plugin</artifactId>" +
                  "      <executions>" +
                  "        <execution>" +
                  "          <id>someId</id>" +
                  "          <phase>generate-sources</phase>" +
                  "          <goals>" +
                  "            <goal>add-source</goal>" +
                  "          </goals>" +
                  "          <configuration>" +
                  "            <sources>" +
                  "              <source>${basedir}/target/src</source>" +
                  "            </sources>" +
                  "          </configuration>" +
                  "        </execution>" +
                  "      </executions>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    new File(myProjectRoot.getPath(), "target/foo").mkdirs();

    updateFolders();

    assertExcludes("project", "target/foo");
  }
  
  public void testDoesNothingWithNonMavenModules() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    createModule("userModule");
    updateFolders(); // shouldn't throw exceptions
  }

  private void updateFolders() throws MavenException {
    FoldersConfigurator.updateProjectExcludedFolders(myProject);
  }
}
