package org.jetbrains.idea.maven;

public class ContentRootsImportingTest extends ImportingTestCase {
  public void testSimpleProjectStructure() throws Exception {
    createStdProjectFolders();

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertModules("project");
    assertContentRoots("project", getProjectPath());

    assertSources("project", "src/main/java", "src/main/resources");
    assertTestSources("project", "src/test/java", "src/test/resources");
  }

  public void testCustomSourceFolders() throws Exception {
    createStdProjectFolders();
    createProjectSubDirs("src", "test", "res1", "res2", "testRes1", "testRes2");

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

  public void testCustomSourceFoldersWithRelativePaths() throws Exception {
    createStdProjectFolders();
    createProjectSubDirs("m",
                         "src",
                         "test",
                         "res",
                         "testRes");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    createModulePom("m", "<groupId>test</groupId>" +
                         "<artifactId>m</artifactId>" +
                         "<version>1</version>" +

                         "<build>" +
                         "  <sourceDirectory>../src</sourceDirectory>" +
                         "  <testSourceDirectory>../test</testSourceDirectory>" +
                         "  <resources>" +
                         "    <resource><directory>../res</directory></resource>" +
                         "  </resources>" +
                         "  <testResources>" +
                         "    <testResource><directory>../testRes</directory></testResource>" +
                         "  </testResources>" +
                         "</build>");
    importProject();
    assertModules("project", "m");
    assertContentRoots("m",
                       getProjectPath() + "/m",
                       getProjectPath() + "/src",
                       getProjectPath() + "/test",
                       getProjectPath() + "/res",
                       getProjectPath() + "/testRes");
  }

  public void testPluginSources() throws Exception {
    createStdProjectFolders();
    createProjectSubDirs("src1", "src2");

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

  public void testPluginSourceDuringGenerateResourcesPhase() throws Exception {
    createStdProjectFolders();
    createProjectSubDirs("extraResources");

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
                  "          <phase>generate-resources</phase>" +
                  "          <goals>" +
                  "            <goal>add-source</goal>" +
                  "          </goals>" +
                  "          <configuration>" +
                  "            <sources>" +
                  "              <source>${basedir}/extraResources</source>" +
                  "            </sources>" +
                  "          </configuration>" +
                  "        </execution>" +
                  "      </executions>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");
    assertModules("project");

    assertSources("project", "extraResources", "src/main/java", "src/main/resources");
  }

  public void testPluginTestSourcesDuringGenerateTestResourcesPhase() throws Exception {
    createStdProjectFolders();
    createProjectSubDirs("extraTestResources");

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
                  "          <phase>generate-test-resources</phase>" +
                  "          <goals>" +
                  "            <goal>add-test-source</goal>" +
                  "          </goals>" +
                  "          <configuration>" +
                  "            <sources>" +
                  "              <source>${basedir}/extraTestResources</source>" +
                  "            </sources>" +
                  "          </configuration>" +
                  "        </execution>" +
                  "      </executions>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");
    assertModules("project");

    assertTestSources("project", "extraTestResources", "src/test/java", "src/test/resources");
  }

  public void testPluginSourcesWithRelativePath() throws Exception {
    createStdProjectFolders();
    createProjectSubDirs("relativePath");

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
                  "              <source>relativePath</source>" +
                  "            </sources>" +
                  "          </configuration>" +
                  "        </execution>" +
                  "      </executions>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");
    assertModules("project");

    assertSources("project", "src/main/java", "src/main/resources", "relativePath");
  }

  public void testPluginSourcesWithVariables() throws Exception {
    createStdProjectFolders();
    createProjectSubDirs("target/src");

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
                  "              <source>${project.build.directory}/src</source>" +
                  "            </sources>" +
                  "          </configuration>" +
                  "        </execution>" +
                  "      </executions>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");
    assertModules("project");

    assertSources("project", "src/main/java", "src/main/resources", "target/src");
  }

  public void testPluginSourcesWithInvalidDependency() throws Exception {
    createStdProjectFolders();
    createProjectSubDirs("src");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>invalid</groupId>" +
                  "    <artifactId>dependency</artifactId>" +
                  "    <version>123</version>" +
                  "  </dependency>" +
                  "</dependencies>" +

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
                  "              <source>src</source>" +
                  "            </sources>" +
                  "          </configuration>" +
                  "        </execution>" +
                  "      </executions>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");
    assertModules("project");

    assertSources("project", "src/main/java", "src/main/resources", "src");
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
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");
    assertModules("project");
  }

  public void testAddingExistingGeneratedSources() throws Exception {
    createStdProjectFolders();
    createProjectSubDirs("target/generated-sources/src1",
                         "target/generated-sources/src2");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertSources("project",
                  "src/main/java",
                  "src/main/resources",
                  "target/generated-sources/src1",
                  "target/generated-sources/src2");
  }

  public void testAddingExistingGeneratedSourcesWithCustomTargetDir() throws Exception {
    createStdProjectFolders();
    createProjectSubDirs("targetCustom/generated-sources/src1",
                         "targetCustom/generated-sources/src2");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <directory>targetCustom</directory>" +
                  "</build>");

    assertSources("project",
                  "src/main/java",
                  "src/main/resources",
                  "targetCustom/generated-sources/src1",
                  "targetCustom/generated-sources/src2");
  }

  public void testDoesNotAddAlreadyRegisteredSourcesUnderGeneratedDir() throws Exception {
    createStdProjectFolders();
    createProjectSubDir("target/generated-sources/main/src");

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
                  "          <id>id</id>" +
                  "          <phase>generate-sources</phase>" +
                  "          <goals>" +
                  "            <goal>add-source</goal>" +
                  "          </goals>" +
                  "          <configuration>" +
                  "            <sources>" +
                  "              <source>target/generated-sources/main/src</source>" +
                  "            </sources>" +
                  "          </configuration>" +
                  "        </execution>" +
                  "      </executions>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    assertSources("project",
                  "src/main/java",
                  "src/main/resources",
                  "target/generated-sources/main/src");
  }

  public void testIgnoringFilesRightUnderGeneratedSources() throws Exception {
    createStdProjectFolders();
    createProjectSubFile("target/generated-sources/f.txt");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertSources("project", "src/main/java", "src/main/resources");
  }

  public void testExcludingOutputDirectories() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
    assertModules("project");

    assertExcludes("project");
    assertModuleOutput("project",
                       getProjectPath() + "/target/classes",
                       getProjectPath() + "/target/test-classes");
  }

  public void testExcludingOutputDirectoriesIfProjectOutputIsUsed() throws Exception {
    myPrefs.setUseMavenOutput(false);

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
    assertModules("project");

    assertExcludes("project", "target/classes", "target/test-classes");
    assertProjectOutput("project");
  }

  public void testExcludingCustomOutputDirectories() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <directory>targetCustom</directory>" +
                  "  <outputDirectory>outputCustom</outputDirectory>" +
                  "  <testOutputDirectory>testCustom</testOutputDirectory>" +
                  "</build>");

    assertModules("project");

    assertExcludes("project");
    assertModuleOutput("project",
                       getProjectPath() + "/outputCustom",
                       getProjectPath() + "/testCustom");
  }

  public void testExcludingCustomOutputUnderTargetUsingStandardVariable() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <outputDirectory>${project.build.directory}/outputCustom</outputDirectory>" +
                  "  <testOutputDirectory>${project.build.directory}/testCustom</testOutputDirectory>" +
                  "</build>");

    assertModules("project");

    assertExcludes("project");
    assertModuleOutput("project",
                       getProjectPath() + "/target/outputCustom",
                       getProjectPath() + "/target/testCustom");
  }

  public void testOutputDirsOutsideOfContentRoot() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <directory>../target</directory>" +
                  "  <outputDirectory>../target/classes</outputDirectory>" +
                  "  <testOutputDirectory>../target/test-classes</testOutputDirectory>" +
                  "</build>");

    assertExcludes("project");
    assertModuleOutput("project",
                       getParentPath() + "/target/classes",
                       getParentPath() + "/target/test-classes");
  }

  public void testExcludingAllDirectoriesUnderTargetDir() throws Exception {
    createProjectSubDir("target/foo");
    createProjectSubDir("target/bar");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertExcludes("project", "target/foo", "target/bar");
  }

  public void testDoesNotExcludeGeneratedSourcesUnderTargetDir() throws Exception {
    createStdProjectFolders();
    createProjectSubDirs("target/foo",
                         "target/generated-sources/bar");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertExcludes("project",
                   "target/foo",
                   "target/classes");

    assertSources("project",
                  "src/main/java",
                  "src/main/resources",
                  "target/generated-sources/bar");
  }

  public void testDoesNotExcludeSourcesUnderTargetDir() throws Exception {
    createStdProjectFolders();
    createProjectSubDirs("target/src",
                         "target/test",
                         "target/xxx");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <sourceDirectory>target/src</sourceDirectory>" +
                  "  <testSourceDirectory>target/test</testSourceDirectory>" +
                  "</build>");

    assertModules("project");

    assertExcludes("project",
                   "target/xxx",
                   "target/classes");
  }

  public void testDoesNotExcludeFoldersWithSourcesUnderTargetDir() throws Exception {
    createStdProjectFolders();
    createProjectSubDirs("target/src/main",
                         "target/foo");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <sourceDirectory>target/src/main</sourceDirectory>" +
                  "</build>");

    assertModules("project");

    assertExcludes("project",
                   "target/foo",
                   "target/classes");

    assertSources("project",
                  "target/src/main",
                  "src/main/resources");
  }
}