// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.application.edtWriteAction
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper
import org.jetbrains.idea.maven.server.MavenServerManager
import org.junit.Test
import java.io.IOException
import java.util.function.Consumer
import kotlin.io.path.exists

class PluginFoldersImportingTest : FoldersImportingTestCase() {
  override fun skipPluginResolution() = false

  @Test
  fun testSourceFolderPointsToProjectRootParent() = runBlocking {
    assumeOnLocalEnvironmentOnly("IDEA-378277")

    createStdProjectFolders()
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.mojo</groupId>
                          <artifactId>build-helper-maven-plugin</artifactId>
                          <version>1.3</version>
                          <executions>
                            <execution>
                              <id>someId</id>
                              <phase>generate-sources</phase>
                              <goals>
                                <goal>add-source</goal>
                              </goals>
                              <configuration>
                                <sources>
                                  <source>${'$'}{basedir}/..</source>
                                </sources>
                              </configuration>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())
    assertModules("project")
    assertContentRoots("project", projectPath)
    assertSources("project", "src/main/java")
    assertTestSources("project", "src/test/java")
    assertDefaultResources("project")
    assertDefaultTestResources("project")
  }

  @Test
  fun testPluginSources() = runBlocking {
    createStdProjectFolders()
    createProjectSubDirs("src1", "src2")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.mojo</groupId>
                          <artifactId>build-helper-maven-plugin</artifactId>
                          <version>1.3</version>
                          <executions>
                            <execution>
                              <id>someId</id>
                              <phase>generate-sources</phase>
                              <goals>
                                <goal>add-source</goal>
                              </goals>
                              <configuration>
                                <sources>
                                  <source>${'$'}{basedir}/src1</source>
                                  <source>${'$'}{basedir}/src2</source>
                                </sources>
                              </configuration>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())
    resolveFoldersAndImport()
    assertModules("project")
    assertSources("project", "src/main/java", "src1", "src2")
    assertDefaultResources("project")
  }

  @Test
  fun testPluginSourceDuringGenerateResourcesPhase() = runBlocking {
    createStdProjectFolders()
    createProjectSubDirs("extraResources")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.mojo</groupId>
                          <artifactId>build-helper-maven-plugin</artifactId>
                          <version>1.3</version>
                          <executions>
                            <execution>
                              <id>someId</id>
                              <phase>generate-resources</phase>
                              <goals>
                                <goal>add-source</goal>
                              </goals>
                              <configuration>
                                <sources>
                                  <source>${'$'}{basedir}/extraResources</source>
                                </sources>
                              </configuration>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())
    resolveFoldersAndImport()
    assertModules("project")
    assertSources("project", "extraResources", "src/main/java")
    assertDefaultResources("project")
  }

  @Test
  fun testPluginTestSourcesDuringGenerateTestResourcesPhase() = runBlocking {
    createStdProjectFolders()
    createProjectSubDirs("extraTestResources")
    mavenImporterSettings.updateFoldersOnImportPhase = "generate-test-resources"
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.mojo</groupId>
                          <artifactId>build-helper-maven-plugin</artifactId>
                          <version>1.3</version>
                          <executions>
                            <execution>
                              <id>someId</id>
                              <phase>generate-test-resources</phase>
                              <goals>
                                <goal>add-test-source</goal>
                              </goals>
                              <configuration>
                                <sources>
                                  <source>${'$'}{basedir}/extraTestResources</source>
                                </sources>
                              </configuration>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())
    resolveFoldersAndImport()
    assertModules("project")
    assertTestSources("project", "extraTestResources", "src/test/java")
    assertDefaultTestResources("project")
  }

  @Test
  fun testPluginSourcesWithRelativePath() = runBlocking {
    createStdProjectFolders()
    createProjectSubDirs("relativePath")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.mojo</groupId>
                          <artifactId>build-helper-maven-plugin</artifactId>
                          <version>1.3</version>
                          <executions>
                            <execution>
                              <id>someId</id>
                              <phase>generate-sources</phase>
                              <goals>
                                <goal>add-source</goal>
                              </goals>
                              <configuration>
                                <sources>
                                  <source>relativePath</source>
                                </sources>
                              </configuration>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())
    resolveFoldersAndImport()
    assertModules("project")
    assertSources("project", "relativePath", "src/main/java")
    assertDefaultResources("project")
  }

  @Test
  fun testPluginSourcesWithVariables() = runBlocking {
    createStdProjectFolders()
    createProjectSubDirs("target/src")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.mojo</groupId>
                          <artifactId>build-helper-maven-plugin</artifactId>
                          <version>1.3</version>
                          <executions>
                            <execution>
                              <id>someId</id>
                              <phase>generate-sources</phase>
                              <goals>
                                <goal>add-source</goal>
                              </goals>
                              <configuration>
                                <sources>
                                  <source>${'$'}{project.build.directory}/src</source>
                                </sources>
                              </configuration>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())
    resolveFoldersAndImport()
    assertModules("project")
    assertSources("project", "src/main/java", "target/src")
    assertDefaultResources("project")
  }

  @Test
  fun testPluginSourcesWithIntermoduleDependency() = runBlocking {
    createStdProjectFolders("m1")
    createProjectSubDirs("m1/src/foo")
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())
    createModulePom("m1",
                    """
                      <groupId>test</groupId>
                      <artifactId>m1</artifactId>
                      <version>1</version>
                      <dependencies>
                        <dependency>
                          <groupId>test</groupId>
                          <artifactId>m2</artifactId>
                          <version>1</version>
                        </dependency>
                      </dependencies>
                      <build>
                        <plugins>
                          <plugin>
                            <groupId>org.codehaus.mojo</groupId>
                            <artifactId>build-helper-maven-plugin</artifactId>
                            <version>1.3</version>
                            <executions>
                              <execution>
                                <id>someId</id>
                                <phase>generate-sources</phase>
                                <goals>
                                  <goal>add-source</goal>
                                </goals>
                                <configuration>
                                  <sources>
                                    <source>src/foo</source>
                                  </sources>
                                </configuration>
                              </execution>
                            </executions>
                          </plugin>
                        </plugins>
                      </build>
                      """.trimIndent())
    createModulePom("m2",
                    """
                      <groupId>test</groupId>
                      <artifactId>m2</artifactId>
                      <version>1</version>
                      """.trimIndent())
    importProjectAsync()
    assertModules("project", "m1", "m2")
    resolveFoldersAndImport()
    assertSources("m1", "src/foo", "src/main/java")
    assertDefaultResources("m1")
  }

  @Test
  fun testPluginExtraFilesInMultipleExecutions() = runBlocking {
    createStdProjectFolders()
    createProjectSubDirs("src1", "src2")
    createProjectSubDirs("resources1", "resources2")
    createProjectSubDirs("test1", "test2")
    createProjectSubDirs("test-resources1", "test-resources2")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.mojo</groupId>
                          <artifactId>build-helper-maven-plugin</artifactId>
                          <version>1.3</version>
                          <executions>
                            <execution>
                              <id>add-src1</id>
                              <phase>generate-sources</phase>
                              <goals>
                                <goal>add-source</goal>
                              </goals>
                              <configuration>
                                <sources>
                                  <source>${'$'}{basedir}/src1</source>
                                </sources>
                              </configuration>
                            </execution>
                            <execution>
                              <id>add-src2</id>
                              <phase>generate-sources</phase>
                              <goals>
                                <goal>add-source</goal>
                              </goals>
                              <configuration>
                                <sources>
                                  <source>${'$'}{basedir}/src2</source>
                                </sources>
                              </configuration>
                            </execution>
                            <execution>
                              <id>add-resources1</id>
                              <phase>generate-sources</phase>
                              <goals>
                                <goal>add-resource</goal>
                              </goals>
                              <configuration>
                                <resources>
                                  <resource><directory>${'$'}{basedir}/resources1</directory></resource>
                                </resources>
                              </configuration>
                            </execution>
                            <execution>
                              <id>add-resources2</id>
                              <phase>generate-sources</phase>
                              <goals>
                                <goal>add-resource</goal>
                              </goals>
                              <configuration>
                                <resources>
                                  <resource><directory>${'$'}{basedir}/resources2</directory></resource>
                                </resources>
                              </configuration>
                            </execution>
                            <execution>
                              <id>add-test1</id>
                              <phase>generate-sources</phase>
                              <goals>
                                <goal>add-test-source</goal>
                              </goals>
                              <configuration>
                                <sources>
                                  <source>${'$'}{basedir}/test1</source>
                                </sources>
                              </configuration>
                            </execution>
                            <execution>
                              <id>add-test2</id>
                              <phase>generate-sources</phase>
                              <goals>
                                <goal>add-test-source</goal>
                              </goals>
                              <configuration>
                                <sources>
                                  <source>${'$'}{basedir}/test2</source>
                                </sources>
                              </configuration>
                            </execution>
                            <execution>
                              <id>add-test-resources1</id>
                              <phase>generate-sources</phase>
                              <goals>
                                <goal>add-test-resource</goal>
                              </goals>
                              <configuration>
                                <resources>
                                  <resource><directory>${'$'}{basedir}/test-resources1</directory></resource>
                                </resources>
                              </configuration>
                            </execution>
                            <execution>
                              <id>add-test-resources2</id>
                              <phase>generate-sources</phase>
                              <goals>
                                <goal>add-test-resource</goal>
                              </goals>
                              <configuration>
                                <resources>
                                  <resource><directory>${'$'}{basedir}/test-resources2</directory></resource>
                                </resources>
                              </configuration>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())
    resolveFoldersAndImport()
    assertModules("project")
    assertSources("project", "src/main/java", "src1", "src2")
    assertDefaultResources("project", "resources1", "resources2")
    assertTestSources("project", "src/test/java", "test1", "test2")
    assertDefaultTestResources("project", "test-resources1", "test-resources2")
  }

  @Test
  fun testDownloadingNecessaryPlugins() = runBlocking {
    try {
      val helper = MavenCustomRepositoryHelper(dir, "local1")
      repositoryPath = helper.getTestData("local1")
      val pluginFile = repositoryPath.resolve("org/codehaus/mojo/build-helper-maven-plugin/1.2/build-helper-maven-plugin-1.2.jar")
      assertFalse(pluginFile.exists())
      importProjectAsync("""
                      <groupId>test</groupId>
                      <artifactId>project</artifactId>
                      <version>1</version>
                      <build>
                        <plugins>
                          <plugin>
                            <groupId>org.codehaus.mojo</groupId>
                            <artifactId>build-helper-maven-plugin</artifactId>
                            <version>1.2</version>
                            <executions>
                              <execution>
                                <id>someId</id>
                                <phase>generate-sources</phase>
                                <goals>
                                  <goal>add-source</goal>
                                </goals>
                                <configuration>
                                  <sources>
                                    <source>src</source>
                                  </sources>
                                </configuration>
                              </execution>
                            </executions>
                          </plugin>
                        </plugins>
                      </build>
                      """.trimIndent())
      resolveFoldersAndImport()
      assertTrue(pluginFile.exists())
    }
    finally {
      // do not lock files by maven process
      MavenServerManager.getInstance().closeAllConnectorsAndWait()
    }
  }


  @Test
  fun testCustomAnnotationProcessorSources() = runBlocking {
    assumeOnLocalEnvironmentOnly("IDEA-378277")

    createStdProjectFolders()
    createProjectSubDirsWithFile("custom-annotations",
                                 "custom-test-annotations",
                                 "target/generated-sources/foo",
                                 "target/generated-sources/annotations",
                                 "target/generated-sources/test-annotations",
                                 "target/generated-test-sources/foo")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                     <plugins>
                      <plugin>
                       <groupId>org.apache.maven.plugins</groupId>
                       <artifactId>maven-compiler-plugin</artifactId>
                       <version>2.3.2</version>
                       <configuration>
                         <generatedSourcesDirectory>${'$'}{basedir}/custom-annotations</generatedSourcesDirectory>
                         <generatedTestSourcesDirectory>${'$'}{basedir}/custom-test-annotations</generatedTestSourcesDirectory>
                       </configuration>
                      </plugin>
                     </plugins>
                    </build>
                    """.trimIndent())
    assertSources("project",
                  "custom-annotations",
                  "src/main/java",
                  "target/generated-sources/annotations",
                  "target/generated-sources/foo",
                  "target/generated-sources/test-annotations")
    assertTestSources("project",
                      "src/test/java",
                      "target/generated-test-sources/foo",
                      "custom-test-annotations")
  }

  @Test
  fun testCustomAnnotationProcessorSourcesUnderMainGeneratedFolder() = runBlocking {
    assumeOnLocalEnvironmentOnly("IDEA-378277")

    createProjectSubDirsWithFile("target/generated-sources/foo",
                                 "target/generated-sources/annotations",
                                 "target/generated-sources/custom-annotations",  // this and...
                                 "target/generated-sources/custom-test-annotations",  // this, are explicitly specified as annotation folders
                                 "target/generated-test-sources/foo",
                                 "target/generated-test-sources/test-annotations"
    )
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                     <plugins>
                      <plugin>
                       <groupId>org.apache.maven.plugins</groupId>
                       <artifactId>maven-compiler-plugin</artifactId>
                       <version>2.3.2</version>
                       <configuration>
                         <generatedSourcesDirectory>${'$'}{basedir}/target/generated-sources/custom-annotations</generatedSourcesDirectory>
                         <generatedTestSourcesDirectory>${'$'}{basedir}/target/generated-sources/custom-test-annotations</generatedTestSourcesDirectory>
                       </configuration>
                      </plugin>
                     </plugins>
                    </build>
                    """.trimIndent())
    assertSources("project",
                  "src/main/java",
                  "target/generated-sources/foo",
                  "target/generated-sources/annotations",
                  "target/generated-sources/custom-annotations")
    assertTestSources("project",
                      "src/test/java",
                      "target/generated-sources/custom-test-annotations",
                      "target/generated-test-sources/foo",
                      "target/generated-test-sources/test-annotations")
  }

  @Test
  fun testSourceFoldersOrder() = runBlocking {
    assumeOnLocalEnvironmentOnly("IDEA-378277")

    createStdProjectFolders()
    val target = createProjectSubDir("target")
    createProjectSubDirsWithFile("anno",
                                 "test-anno",
                                 "target/generated-sources/foo",
                                 "target/generated-sources/annotations",
                                 "target/generated-sources/test-annotations",
                                 "target/generated-test-sources/foo")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                     <plugins>
                      <plugin>
                       <groupId>org.apache.maven.plugins</groupId>
                       <artifactId>maven-compiler-plugin</artifactId>
                       <version>2.3.2</version>
                       <configuration>
                         <generatedSourcesDirectory>${'$'}{basedir}/anno</generatedSourcesDirectory>
                         <generatedTestSourcesDirectory>${'$'}{basedir}/test-anno</generatedTestSourcesDirectory>
                       </configuration>
                      </plugin>
                     </plugins>
                    </build>
                    """.trimIndent())
    val testAssertions = Consumer { shouldKeepGeneratedFolders: Boolean ->
      if (shouldKeepGeneratedFolders) {
        assertSources("project",
                      "anno",
                      "src/main/java",
                      "target/generated-sources/annotations",
                      "target/generated-sources/foo",
                      "target/generated-sources/test-annotations")
      }
      else {
        assertSources("project",
                      "anno",
                      "src/main/java")
      }
      assertDefaultResources("project")
      if (shouldKeepGeneratedFolders) {
        assertTestSources("project",
                          "src/test/java",
                          "target/generated-test-sources/foo",
                          "test-anno")
      }
      else {
        assertTestSources("project",
                          "src/test/java",
                          "test-anno")
      }
      assertDefaultTestResources("project")
    }
    testAssertions.accept(true)
    edtWriteAction {
      try {
        target.delete(this)
      }
      catch (e: IOException) {
        fail("Unable to delete the file: " + e.message)
      }
    }
    testAssertions.accept(true)

    // incremental sync doesn't support updating source folders if effective pom dependencies haven't changed
    updateAllProjectsFullSync()
    testAssertions.accept(false)
    resolveFoldersAndImport()
    testAssertions.accept(false)
  }

  @Test
  fun testDoesNotAddAlreadyRegisteredSourcesUnderGeneratedDir() = runBlocking {
    createStdProjectFolders()
    createProjectSubDirs("target/generated-sources/main/src",
                         "target/generated-test-sources/test/src")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.mojo</groupId>
                          <artifactId>build-helper-maven-plugin</artifactId>
                          <version>1.3</version>
                          <executions>
                            <execution>
                              <id>id1</id>
                              <phase>generate-sources</phase>
                              <goals>
                                <goal>add-source</goal>
                              </goals>
                              <configuration>
                                <sources>
                                  <source>target/generated-sources/main/src</source>
                                </sources>
                              </configuration>
                            </execution>
                            <execution>
                              <id>id2</id>
                              <phase>generate-sources</phase>
                              <goals>
                                <goal>add-test-source</goal>
                              </goals>
                              <configuration>
                                <sources>
                                  <source>target/generated-test-sources/test/src</source>
                                </sources>
                              </configuration>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())
    resolveFoldersAndImport()
    assertSources("project",
                  "src/main/java",
                  "target/generated-sources/main/src")
    assertDefaultResources("project")
    assertTestSources("project",
                      "src/test/java",
                      "target/generated-test-sources/test/src")
    assertDefaultTestResources("project")
  }
}