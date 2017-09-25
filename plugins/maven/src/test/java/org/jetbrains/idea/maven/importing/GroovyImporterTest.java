/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;

import java.io.File;
import java.util.Arrays;

public class GroovyImporterTest extends MavenImportingTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setRepositoryPath(new File(myDir, "repo").getPath());
  }

  public void testConfiguringFacetWithoutLibrary() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.codehaus.groovy.maven</groupId>" +
                  "      <artifactId>gmaven-plugin</artifactId>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    assertModules("project");

    assertUnorderedElementsAreEqual(GroovyConfigUtils.getInstance().getSDKLibrariesByModule(getModule("project")));
  }

  public void testConfiguringFacetWithLibrary() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>org.codehaus.groovy.maven.runtime</groupId>" +
                  "    <artifactId>gmaven-runtime-default</artifactId>" +
                  "    <version>1.0-rc-1</version>" +
                  "  </dependency>" +
                  "</dependencies>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.codehaus.groovy.maven</groupId>" +
                  "      <artifactId>gmaven-plugin</artifactId>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    assertModules("project");

    Library[] libraries = GroovyConfigUtils.getInstance().getSDKLibrariesByModule(getModule("project"));
    assertTrue("unexpected groovy libs configuration: " + libraries.length, libraries.length > 0);
    Library library = libraries[0];
    assertUnorderedPathsAreEqual(
      Arrays.asList(library.getUrls(OrderRootType.CLASSES)),
      Arrays.asList("jar://" + getRepositoryPath() + "/org/codehaus/groovy/groovy-all-minimal/1.5.6/groovy-all-minimal-1.5.6.jar!/"));
  }

  public void testAddingGroovySpecificSources() {
    createStdProjectFolders();
    createProjectSubDirs("src/main/groovy",
                         "src/test/groovy");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.codehaus.groovy.maven</groupId>" +
                  "      <artifactId>gmaven-plugin</artifactId>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    assertModules("project");

    assertSources("project",
                  "src/main/groovy",
                  "src/main/java");
    assertResources("project", "src/main/resources");
    assertTestSources("project",
                      "src/test/groovy",
                      "src/test/java");
    assertTestResources("project", "src/test/resources");
  }

  public void testAddingGroovySpecificSources2() {
    createStdProjectFolders();
    createProjectSubDirs("src/main/groovy",
                         "src/test/groovy");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.codehaus.gmaven</groupId>" +
                  "      <artifactId>groovy-maven-plugin</artifactId>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    assertModules("project");

    assertSources("project",
                  "src/main/groovy",
                  "src/main/java");
    assertResources("project", "src/main/resources");
    assertTestSources("project",
                      "src/test/groovy",
                      "src/test/java");
    assertTestResources("project", "src/test/resources");
  }

  public void testGroovyEclipsePlugin() {
    createStdProjectFolders();
    createProjectSubDirs("src/main/groovy",
                         "src/test/groovy");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "" +
                  "<dependencies>\n" +
                  "  <dependency>\n" +
                  "    <groupId>org.codehaus.groovy</groupId>\n" +
                  "    <artifactId>groovy-all</artifactId>\n" +
                  "    <version>2.1.0</version>\n" +
                  "  </dependency>\n" +
                  "</dependencies>" +
                  "" +
                  "<build>\n" +
                  "  <pluginManagement>\n" +
                  "    <plugins>\n" +
                  "      <plugin>\n" +
                  "        <artifactId>maven-compiler-plugin</artifactId>\n" +
                  "        <configuration>\n" +
                  "          <compilerId>groovy-eclipse-compiler</compilerId>\n" +
                  "          <source>1.7</source>\n" +
                  "          <target>1.7</target>\n" +
                  "          <showWarnings>false</showWarnings>\n" +
                  "        </configuration>\n" +
                  "        <dependencies>\n" +
                  "          <dependency>\n" +
                  "            <groupId>org.codehaus.groovy</groupId>\n" +
                  "            <artifactId>groovy-eclipse-compiler</artifactId>\n" +
                  "            <version>2.8.0-01</version>\n" +
                  "          </dependency>\n" +
                  "          <dependency>\n" +
                  "            <groupId>org.codehaus.groovy</groupId>\n" +
                  "            <artifactId>groovy-eclipse-batch</artifactId>\n" +
                  "            <version>2.1.3-01</version>\n" +
                  "          </dependency>\n" +
                  "        </dependencies>\n" +
                  "      </plugin>\n" +
                  "      <plugin>\n" +
                  "        <groupId>org.codehaus.groovy</groupId>\n" +
                  "        <artifactId>groovy-eclipse-compiler</artifactId>\n" +
                  "        <version>2.8.0-01</version>\n" +
                  "        <extensions>true</extensions>\n" +
                  "      </plugin>\n" +
                  "    </plugins>\n" +
                  "  </pluginManagement>\n" +
                  "</build>\n");

    assertModules("project");

    assertSources("project",
                  "src/main/groovy",
                  "src/main/java");
    assertResources("project", "src/main/resources");
    assertTestSources("project",
                      "src/test/groovy",
                      "src/test/java");
    assertTestResources("project", "src/test/resources");
  }

  public void testAddingCustomGroovySpecificSources() {
    createStdProjectFolders();
    createProjectSubDirs("src/main/groovy",
                         "src/foo1",
                         "src/foo2",
                         "src/test/groovy",
                         "src/test-foo1",
                         "src/test-foo2");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.codehaus.groovy.maven</groupId>" +
                  "      <artifactId>gmaven-plugin</artifactId>" +
                  "      <executions>" +
                  "        <execution>" +
                  "          <id>one</id>" +
                  "          <goals>" +
                  "            <goal>compile</goal>" +
                  "          </goals>" +
                  "          <configuration>" +
                  "            <sources>" +
                  "              <fileset>" +
                  "                <directory>${pom.basedir}/src/foo1</directory>" +
                  "              </fileset>" +
                  "              <fileset>" +
                  "                <directory>${pom.basedir}/src/foo2</directory>" +
                  "              </fileset>" +
                  "            </sources>" +
                  "          </configuration>" +
                  "        </execution>" +
                  "        <execution>" +
                  "          <id>two</id>" +
                  "          <goals>" +
                  "            <goal>testCompile</goal>" +
                  "          </goals>" +
                  "          <configuration>" +
                  "            <sources>" +
                  "              <fileset>" +
                  "                <directory>${pom.basedir}/src/test-foo1</directory>" +
                  "              </fileset>" +
                  "              <fileset>" +
                  "                <directory>${pom.basedir}/src/test-foo2</directory>" +
                  "              </fileset>" +
                  "            </sources>" +
                  "          </configuration>" +
                  "        </execution>" +
                  "      </executions>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    assertModules("project");

    assertSources("project",
                  "src/foo1",
                  "src/foo2",
                  "src/main/java");
    assertResources("project", "src/main/resources");
    assertTestSources("project",
                      "src/test-foo1",
                      "src/test-foo2",
                      "src/test/java");
    assertTestResources("project", "src/test/resources");
  }

  public void testAddingCustomGroovySpecificSourcesByRelativePath() {
    createProjectSubDirs("src/foo",
                         "src/test-foo");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.codehaus.groovy.maven</groupId>" +
                  "      <artifactId>gmaven-plugin</artifactId>" +
                  "      <executions>" +
                  "        <execution>" +
                  "          <id>one</id>" +
                  "          <goals>" +
                  "            <goal>compile</goal>" +
                  "          </goals>" +
                  "          <configuration>" +
                  "            <sources>" +
                  "              <fileset>" +
                  "                <directory>src/foo</directory>" +
                  "              </fileset>" +
                  "            </sources>" +
                  "          </configuration>" +
                  "        </execution>" +
                  "        <execution>" +
                  "          <id>two</id>" +
                  "          <goals>" +
                  "            <goal>testCompile</goal>" +
                  "          </goals>" +
                  "          <configuration>" +
                  "            <sources>" +
                  "              <fileset>" +
                  "                <directory>src/test-foo</directory>" +
                  "              </fileset>" +
                  "            </sources>" +
                  "          </configuration>" +
                  "        </execution>" +
                  "      </executions>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    assertModules("project");

    assertSources("project", "src/foo");
    assertTestSources("project", "src/test-foo");
  }

  public void testDoNotAddGroovySpecificGeneratedSources() {
    createStdProjectFolders();
    createProjectSubDirs("target/generated-sources/xxx/yyy",
                         "target/generated-sources/groovy-stubs/main/foo",
                         "target/generated-sources/groovy-stubs/test/bar");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.codehaus.groovy.maven</groupId>" +
                  "      <artifactId>gmaven-plugin</artifactId>" +
                  "      <executions>" +
                  "        <execution>" +
                  "          <goals>" +
                  "            <goal>generateStubs</goal>" +
                  "            <goal>compile</goal>" +
                  "            <goal>generateTestStubs</goal>" +
                  "            <goal>testCompile</goal>" +
                  "          </goals>" +
                  "        </execution>" +
                  "      </executions>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    assertModules("project");

    assertSources("project",
                  "src/main/java",
                  "target/generated-sources/xxx");
    assertResources("project", "src/main/resources");
    assertTestSources("project", "src/test/java");
    assertTestResources("project", "src/test/resources");

    assertExcludes("project", "target");
  }

  public void testDoNotAddCustomGroovySpecificGeneratedSources() {
    createStdProjectFolders();
    createProjectSubDirs("target/generated-sources/xxx/yyy",
                         "target/generated-sources/foo/aaa",
                         "target/generated-sources/bar/bbb");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.codehaus.groovy.maven</groupId>" +
                  "      <artifactId>gmaven-plugin</artifactId>" +
                  "      <executions>" +
                  "        <execution>" +
                  "          <id>one</id>" +
                  "          <goals>" +
                  "            <goal>generateStubs</goal>" +
                  "          </goals>" +
                  "          <configuration>" +
                  "            <outputDirectory>${project.build.directory}/generated-sources/foo</outputDirectory>" +
                  "          </configuration>" +
                  "        </execution>" +
                  "        <execution>" +
                  "          <id>two</id>" +
                  "          <goals>" +
                  "            <goal>generateTestStubs</goal>" +
                  "          </goals>" +
                  "          <configuration>" +
                  "            <outputDirectory>${project.build.directory}/generated-sources/bar</outputDirectory>" +
                  "          </configuration>" +
                  "        </execution>" +
                  "      </executions>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    assertModules("project");

    assertSources("project",
                  "src/main/java",
                  "target/generated-sources/xxx");
    assertResources("project", "src/main/resources");
    assertTestSources("project", "src/test/java");
    assertTestResources("project", "src/test/resources");

    assertExcludes("project", "target");
  }

  public void testDoNotAddCustomGroovySpecificGeneratedSourcesByRelativePath() {
    createProjectSubDirs("target/generated-sources/xxx/yyy",
                         "target/generated-sources/foo/aaa",
                         "target/generated-sources/bar/bbb");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.codehaus.groovy.maven</groupId>" +
                  "      <artifactId>gmaven-plugin</artifactId>" +
                  "      <executions>" +
                  "        <execution>" +
                  "          <id>one</id>" +
                  "          <goals>" +
                  "            <goal>generateStubs</goal>" +
                  "          </goals>" +
                  "          <configuration>" +
                  "            <outputDirectory>target/generated-sources/foo</outputDirectory>" +
                  "          </configuration>" +
                  "        </execution>" +
                  "        <execution>" +
                  "          <id>two</id>" +
                  "          <goals>" +
                  "            <goal>generateTestStubs</goal>" +
                  "          </goals>" +
                  "          <configuration>" +
                  "            <outputDirectory>target/generated-sources/bar</outputDirectory>" +
                  "          </configuration>" +
                  "        </execution>" +
                  "      </executions>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    assertModules("project");

    assertSources("project",
                  "target/generated-sources/xxx");
    assertTestSources("project");

    assertExcludes("project", "target");
  }

  public void testUpdatingGroovySpecificGeneratedSourcesOnFoldersUpdate() {
    try {
      importProject("<groupId>test</groupId>" +
                    "<artifactId>project</artifactId>" +
                    "<version>1</version>" +

                    "<build>" +
                    "  <plugins>" +
                    "    <plugin>" +
                    "      <groupId>org.codehaus.groovy.maven</groupId>" +
                    "      <artifactId>gmaven-plugin</artifactId>" +
                    "      <executions>" +
                    "        <execution>" +
                    "          <goals>" +
                    "            <goal>generateStubs</goal>" +
                    "            <goal>generateTestStubs</goal>" +
                    "          </goals>" +
                    "        </execution>" +
                    "      </executions>" +
                    "    </plugin>" +
                    "  </plugins>" +
                    "</build>");

      ApplicationManager.getApplication().runWriteAction(() -> {
        MavenRootModelAdapter a = new MavenRootModelAdapter(myProjectsTree.findProject(myProjectPom),
                                                            getModule("project"),
                                                            new IdeModifiableModelsProviderImpl(myProject));
        a.unregisterAll(getProjectPath() + "/target", true, true);
        a.getRootModel().commit();
      });


      assertSources("project");
      assertTestSources("project");
      assertExcludes("project");

      createProjectSubDirs("src/main/groovy",
                           "src/test/groovy",
                           "target/generated-sources/xxx/yyy",
                           "target/generated-sources/groovy-stubs/main/foo",
                           "target/generated-sources/groovy-stubs/test/bar");

      resolveFoldersAndImport();

      assertSources("project",
                    "src/main/groovy",
                    "target/generated-sources/xxx");
      assertTestSources("project",
                        "src/test/groovy");
      assertExcludes("project", "target");
    }
    finally {
      // do not lock files by maven process
      MavenServerManager.getInstance().shutdown(true);
    }
  }

  public void testDoNotAddGroovySpecificGeneratedSourcesForGMaven_1_2() {
    createStdProjectFolders();
    createProjectSubDirs("target/generated-sources/xxx/yyy",
                         "target/generated-sources/groovy-stubs/main/foo",
                         "target/generated-sources/groovy-stubs/test/bar");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.codehaus.gmaven</groupId>" +
                  "      <artifactId>gmaven-plugin</artifactId>" +
                  "      <version>1.2</version>" +
                  "      <executions>" +
                  "        <execution>" +
                  "          <goals>" +
                  "            <goal>generateStubs</goal>" +
                  "            <goal>compile</goal>" +
                  "            <goal>generateTestStubs</goal>" +
                  "            <goal>testCompile</goal>" +
                  "          </goals>" +
                  "        </execution>" +
                  "      </executions>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    assertModules("project");

    assertSources("project",
                  "src/main/java",
                  "target/generated-sources/xxx");
    assertResources("project", "src/main/resources");
    assertTestSources("project", "src/test/java");
    assertTestResources("project", "src/test/resources");

    assertExcludes("project", "target");
  }

}
