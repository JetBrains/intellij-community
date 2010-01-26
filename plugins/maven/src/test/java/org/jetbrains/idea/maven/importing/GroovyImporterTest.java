/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.importing.MavenDefaultModifiableModelsProvider;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;

import java.io.File;

public class GroovyImporterTest extends MavenImportingTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setRepositoryPath(new File(myDir, "repo").getPath());
  }

  public void testConfiguringFacetWithoutLibrary() throws Exception {
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

  public void testConfiguringFacetWithLibrary() throws Exception {
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
    assertUnorderedElementsAreEqual(
      library.getUrls(OrderRootType.CLASSES),
      "jar://" + getRepositoryPath() + "/org/codehaus/groovy/groovy-all-minimal/1.5.6/groovy-all-minimal-1.5.6.jar!/");
  }

  public void testAddingGroovySpecificSources() throws Exception {
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
                  "src/main/java",
                  "src/main/resources",
                  "src/main/groovy");
    assertTestSources("project",
                      "src/test/java",
                      "src/test/resources",
                      "src/test/groovy");
  }

  public void testAddingCustomGroovySpecificSources() throws Exception {
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
                  "src/main/java",
                  "src/main/resources",
                  "src/foo1",
                  "src/foo2");
    assertTestSources("project",
                      "src/test/java",
                      "src/test/resources",
                      "src/test-foo1",
                      "src/test-foo2");
  }

  public void testAddingCustomGroovySpecificSourcesByRelativePath() throws Exception {
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

  public void testDoNotAddGroovySpecificGeneratedSources() throws Exception {
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
                  "src/main/resources",
                  "target/generated-sources/xxx");
    assertTestSources("project",
                      "src/test/java",
                      "src/test/resources");

    assertExcludes("project",
                   "target/generated-sources/groovy-stubs");
  }

  public void testDoNotAddCustomGroovySpecificGeneratedSources() throws Exception {
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
                  "src/main/resources",
                  "target/generated-sources/xxx");
    assertTestSources("project",
                      "src/test/java",
                      "src/test/resources");

    assertExcludes("project",
                   "target/generated-sources/foo",
                   "target/generated-sources/bar");
  }

  public void testDoNotAddCustomGroovySpecificGeneratedSourcesByRelativePath() throws Exception {
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

    assertExcludes("project",
                   "target/generated-sources/foo",
                   "target/generated-sources/bar");
  }

  public void testUpdatingGroovySpecificGeneratedSourcesOnFoldersUpdate() throws Exception {
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

    MavenRootModelAdapter a = new MavenRootModelAdapter(myProjectsTree.findProject(myProjectPom),
                                                        getModule("project"),
                                                        new MavenDefaultModifiableModelsProvider(myProject));
    a.unregisterAll(getProjectPath() + "/target", true, true);
    a.getRootModel().commit();

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
    assertExcludes("project",
                   "target/generated-sources/groovy-stubs");
  }

  public void testDoNotAddGroovySpecificGeneratedSourcesForGMaven_1_2() throws Exception {
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
                  "src/main/resources",
                  "target/generated-sources/xxx");
    assertTestSources("project",
                      "src/test/java",
                      "src/test/resources");

    assertExcludes("project",
                   "target/generated-sources/groovy-stubs");
  }

}
