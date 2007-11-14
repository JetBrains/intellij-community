package org.jetbrains.idea.maven;

import java.io.IOException;

public class ContentRootsImportingTest extends ProjectImportingTestCase {
  public void testSimpleProjectStructure() throws IOException {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertModules("project");
    assertContentRoots("project", getProjectPath());

    assertSources("project", "src/main/java", "src/main/resources");
    assertTestSources("project", "src/test/java", "src/test/resources");
  }

  public void testCustomSourceFolders() throws IOException {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <sourceDirectory>src</sourceDirectory>" +
                  "  <testSourceDirectory>test</testSourceDirectory>" +
                  "  <resources>" +
                  "    <resource><directory>res1</directory></resource>" +
                  "    <resource><directory>res2</directory></resource>" +
                  "  </resources>" +
                  "  <testResources>" +
                  "    <testResource><directory>testRes1</directory></testResource>" +
                  "    <testResource><directory>testRes2</directory></testResource>" +
                  "  </testResources>" +
                  "</build>");

    assertModules("project");
    assertContentRoots("project", getProjectPath());

    assertSources("project", "src", "res1", "res2");
    assertTestSources("project", "test", "testRes1", "testRes2");
  }

  public void testBuildHelperPluginSources() throws Exception {
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
                  "              <source>${basedir}/src1</source>" +
                  "              <source>${basedir}/src2</source>" +
                  "            </sources>" +
                  "          </configuration>" +
                  "        </execution>" +
                  "      </executions>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");
    assertModules("project");

    assertSources("project", "src/main/java", "src/main/resources", "src1", "src2");
  }

  public void testBuildHelperPluginTestSource() throws Exception {
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
                  "            <goal>add-test-source</goal>" +
                  "          </goals>" +
                  "          <configuration>" +
                  "            <sources>" +
                  "              <source>${basedir}/src1</source>" +
                  "              <source>${basedir}/src2</source>" +
                  "            </sources>" +
                  "          </configuration>" +
                  "        </execution>" +
                  "      </executions>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");
    assertModules("project");

    assertTestSources("project", "src/test/java", "src/test/resources", "src1", "src2");
  }

  public void testBuildHelperPluginWithoutSources() throws Exception {
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
                  "          <id>add-source</id>" +
                  "          <phase>generate-sources</phase>" +
                  "          <goals>" +
                  "            <goal>add-source</goal>" +
                  "          </goals>" +
                  "          <configuration></configuration>" +
                  "        </execution>" +
                  "      </executions>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");
    assertModules("project");
  }

  public void testBuildHelperPluginWithoutConfiguration() throws Exception {
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
                  "          <id>add-source</id>" +
                  "          <phase>generate-sources</phase>" +
                  "          <goals>" +
                  "            <goal>add-source</goal>" +
                  "          </goals>" +
                  "        </execution>" +
                  "      </executions>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");
    assertModules("project");
  }

  public void testBuildHelperPluginWithoutGoals() throws Exception {
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
                  "          <id>add-source</id>" +
                  "          <phase>generate-sources</phase>" +
                  "        </execution>" +
                  "      </executions>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");
    assertModules("project");
  }

  public void testBuildHelperPluginWithoutExecution() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.codehaus.mojo</groupId>" +
                  "      <artifactId>build-helper-maven-plugin</artifactId>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");
    assertModules("project");
  }

  public void testAndRunPluginSources() throws IOException {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.apache.maven.plugins</groupId>" +
                  "      <artifactId>maven-antrun-plugin</artifactId>\n" +
                  "      <executions>" +
                  "        <execution>" +
                  "          <configuration>" +
                  "            <sourceRoot>${basedir}/src</sourceRoot>" +
                  "            <testSourceRoot>${basedir}/test</testSourceRoot>" +
                  "          </configuration>" +
                  "        </execution>" +
                  "      </executions>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");
    assertModules("project");

    assertSources("project", "src/main/java", "src/main/resources", "src");
    assertTestSources("project", "src/test/java", "src/test/resources", "test");
  }

  public void testAndRunPluginWithoutSources() throws IOException {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.apache.maven.plugins</groupId>" +
                  "      <artifactId>maven-antrun-plugin</artifactId>\n" +
                  "      <executions>" +
                  "        <execution>" +
                  "          <configuration></configuration>" +
                  "        </execution>" +
                  "      </executions>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");
    assertModules("project");
  }

  public void testAndRunPluginWithoutConfiguration() throws IOException {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.apache.maven.plugins</groupId>" +
                  "      <artifactId>maven-antrun-plugin</artifactId>\n" +
                  "      <executions>" +
                  "        <execution></execution>" +
                  "      </executions>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");
    assertModules("project");
  }

  public void testAndRunPluginWithoutExecution() throws IOException {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.apache.maven.plugins</groupId>" +
                  "      <artifactId>maven-antrun-plugin</artifactId>\n" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");
    assertModules("project");
  }

  public void testExcludingOutputDirectories() throws IOException {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
    assertModules("project");

    assertExcludes("project", "target");
    assertModuleOutput("project",
                       getProjectPath() + "/target/classes",
                       getProjectPath() + "/target/test-classes");
  }

  public void testExcludingOutputDirectoriesIfProjectOutputIsUsed() throws IOException {
    prefs.setUseMavenOutput(false);

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
    assertModules("project");

    assertExcludes("project", "target", "target/classes", "target/test-classes");
    assertProjectOutput("project");
  }

  public void testExcludingCustomOutputDirectories() throws IOException {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <directory>targetCustom</directory>" +
                  "  <outputDirectory>outputCustom</outputDirectory>" +
                  "  <testOutputDirectory>testCustom</testOutputDirectory>" +
                  "</build>");

    assertModules("project");

    assertExcludes("project", "targetCustom");
    assertModuleOutput("project",
                       getProjectPath() + "/outputCustom",
                       getProjectPath() + "/testCustom");
  }

  public void testOutputDirsOutsideOfContentRoot() throws IOException {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <directory>../target</directory>" +
                  "  <outputDirectory>../target/classes</outputDirectory>" +
                  "  <testOutputDirectory>../target/test-classes</testOutputDirectory>" +
                  "</build>");

    String parentPath = getParentPath();
    assertContentRoots("project", getProjectPath(), parentPath + "/target");
    assertContentRootExcludes("project", getProjectPath());
    assertContentRootExcludes("project", parentPath + "/target", "");

    assertModuleOutput("project",
                       parentPath + "/target/classes",
                       parentPath + "/target/test-classes");
  }
}