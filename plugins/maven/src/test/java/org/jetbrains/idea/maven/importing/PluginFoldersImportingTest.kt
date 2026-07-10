// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.fixtures.MavenCustomRepositoryHelper
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertContentRoots
import com.intellij.maven.testFramework.fixtures.assertDefaultResources
import com.intellij.maven.testFramework.fixtures.assertDefaultTestResources
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.assertSources
import com.intellij.maven.testFramework.fixtures.assertTestSources
import com.intellij.maven.testFramework.fixtures.assumeOnLocalEnvironmentOnly
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubDir
import com.intellij.maven.testFramework.fixtures.createProjectSubDirs
import com.intellij.maven.testFramework.fixtures.createProjectSubDirsWithFile
import com.intellij.maven.testFramework.fixtures.createStdProjectFolders
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImporterSettings
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.projectPath
import com.intellij.maven.testFramework.fixtures.resolveFoldersAndImport
import com.intellij.maven.testFramework.fixtures.updateAllProjectsFullSync
import com.intellij.openapi.application.edtWriteAction
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.server.MavenServerManager
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.io.IOException
import java.util.function.Consumer
import kotlin.io.path.exists

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class PluginFoldersImportingTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion,
    skipPluginResolution = false,
  )

  @BeforeEach
  fun setUpExternalChanges() {
    maven.projectsManager.listenForExternalChanges()
  }

  @Test
  fun testSourceFolderPointsToProjectRootParent() = runBlocking {
    maven.assumeOnLocalEnvironmentOnly("IDEA-378277")

    maven.createStdProjectFolders()
    maven.importProjectAsync($$"""
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
                                  <source>${basedir}/..</source>
                                </sources>
                              </configuration>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())
    maven.assertModules("project")
    maven.assertContentRoots("project", maven.projectPath)
    maven.assertSources("project", "src/main/java")
    maven.assertTestSources("project", "src/test/java")
    maven.assertDefaultResources("project")
    maven.assertDefaultTestResources("project")
  }

  @Test
  fun testPluginSources() = runBlocking {
    maven.createStdProjectFolders()
    maven.createProjectSubDirs("src1", "src2")
    maven.importProjectAsync($$"""
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
                                  <source>${basedir}/src1</source>
                                  <source>${basedir}/src2</source>
                                </sources>
                              </configuration>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())
    maven.resolveFoldersAndImport()
    maven.assertModules("project")
    maven.assertSources("project", "src/main/java", "src1", "src2")
    maven.assertDefaultResources("project")
  }

  @Test
  fun testPluginSourceDuringGenerateResourcesPhase() = runBlocking {
    maven.createStdProjectFolders()
    maven.createProjectSubDirs("extraResources")
    maven.importProjectAsync($$"""
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
                                  <source>${basedir}/extraResources</source>
                                </sources>
                              </configuration>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())
    maven.resolveFoldersAndImport()
    maven.assertModules("project")
    maven.assertSources("project", "extraResources", "src/main/java")
    maven.assertDefaultResources("project")
  }

  @Test
  fun testPluginTestSourcesDuringGenerateTestResourcesPhase() = runBlocking {
    maven.createStdProjectFolders()
    maven.createProjectSubDirs("extraTestResources")
    maven.mavenImporterSettings.updateFoldersOnImportPhase = "generate-test-resources"
    maven.importProjectAsync($$"""
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
                                  <source>${basedir}/extraTestResources</source>
                                </sources>
                              </configuration>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())
    maven.resolveFoldersAndImport()
    maven.assertModules("project")
    maven.assertTestSources("project", "extraTestResources", "src/test/java")
    maven.assertDefaultTestResources("project")
  }

  @Test
  fun testPluginSourcesWithRelativePath() = runBlocking {
    maven.createStdProjectFolders()
    maven.createProjectSubDirs("relativePath")
    maven.importProjectAsync("""
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
    maven.resolveFoldersAndImport()
    maven.assertModules("project")
    maven.assertSources("project", "relativePath", "src/main/java")
    maven.assertDefaultResources("project")
  }

  @Test
  fun testPluginSourcesWithVariables() = runBlocking {
    maven.createStdProjectFolders()
    maven.createProjectSubDirs("target/src")
    maven.importProjectAsync($$"""
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
                                  <source>${project.build.directory}/src</source>
                                </sources>
                              </configuration>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())
    maven.resolveFoldersAndImport()
    maven.assertModules("project")
    maven.assertSources("project", "src/main/java", "target/src")
    maven.assertDefaultResources("project")
  }

  @Test
  fun testPluginSourcesWithIntermoduleDependency() = runBlocking {
    maven.createStdProjectFolders("m1")
    maven.createProjectSubDirs("m1/src/foo")
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())
    maven.createModulePom("m1",
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
    maven.createModulePom("m2",
                    """
                      <groupId>test</groupId>
                      <artifactId>m2</artifactId>
                      <version>1</version>
                      """.trimIndent())
    maven.importProjectAsync()
    maven.assertModules("project", "m1", "m2")
    maven.resolveFoldersAndImport()
    maven.assertSources("m1", "src/foo", "src/main/java")
    maven.assertDefaultResources("m1")
  }

  @Test
  fun testPluginExtraFilesInMultipleExecutions() = runBlocking {
    maven.createStdProjectFolders()
    maven.createProjectSubDirs("src1", "src2")
    maven.createProjectSubDirs("resources1", "resources2")
    maven.createProjectSubDirs("test1", "test2")
    maven.createProjectSubDirs("test-resources1", "test-resources2")
    maven.importProjectAsync($$"""
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
                                  <source>${basedir}/src1</source>
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
                                  <source>${basedir}/src2</source>
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
                                  <resource><directory>${basedir}/resources1</directory></resource>
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
                                  <resource><directory>${basedir}/resources2</directory></resource>
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
                                  <source>${basedir}/test1</source>
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
                                  <source>${basedir}/test2</source>
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
                                  <resource><directory>${basedir}/test-resources1</directory></resource>
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
                                  <resource><directory>${basedir}/test-resources2</directory></resource>
                                </resources>
                              </configuration>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())
    maven.resolveFoldersAndImport()
    maven.assertModules("project")
    maven.assertSources("project", "src/main/java", "src1", "src2")
    maven.assertDefaultResources("project", "resources1", "resources2")
    maven.assertTestSources("project", "src/test/java", "test1", "test2")
    maven.assertDefaultTestResources("project", "test-resources1", "test-resources2")
  }

  @Test
  fun testDownloadingNecessaryPlugins() = runBlocking {
    try {
      val helper = MavenCustomRepositoryHelper(maven.dir, "local1")
      maven.repositoryPath = helper.getTestData("local1")
      val pluginFile = maven.repositoryPath.resolve("org/codehaus/mojo/build-helper-maven-plugin/1.2/build-helper-maven-plugin-1.2.jar")
      assertFalse(pluginFile.exists())
      maven.importProjectAsync("""
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
      maven.resolveFoldersAndImport()
      assertTrue(pluginFile.exists())
    }
    finally {
      // do not lock files by maven process
      MavenServerManager.getInstance().closeAllConnectorsAndWait()
    }
  }


  @Test
  fun testCustomAnnotationProcessorSources() = runBlocking {
    maven.assumeOnLocalEnvironmentOnly("IDEA-378277")

    maven.createStdProjectFolders()
    maven.createProjectSubDirsWithFile("custom-annotations",
                                 "custom-test-annotations",
                                 "target/generated-sources/foo",
                                 "target/generated-sources/annotations",
                                 "target/generated-sources/test-annotations",
                                 "target/generated-test-sources/foo")
    maven.importProjectAsync($$"""
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
                         <generatedSourcesDirectory>${basedir}/custom-annotations</generatedSourcesDirectory>
                         <generatedTestSourcesDirectory>${basedir}/custom-test-annotations</generatedTestSourcesDirectory>
                       </configuration>
                      </plugin>
                     </plugins>
                    </build>
                    """.trimIndent())
    maven.assertSources("project",
                  "custom-annotations",
                  "src/main/java",
                  "target/generated-sources/annotations",
                  "target/generated-sources/foo",
                  "target/generated-sources/test-annotations")
    maven.assertTestSources("project",
                      "src/test/java",
                      "target/generated-test-sources/foo",
                      "custom-test-annotations")
  }

  @Test
  fun testCustomAnnotationProcessorSourcesUnderMainGeneratedFolder() = runBlocking {
    maven.assumeOnLocalEnvironmentOnly("IDEA-378277")

    maven.createProjectSubDirsWithFile("target/generated-sources/foo",
                                 "target/generated-sources/annotations",
                                 "target/generated-sources/custom-annotations",  // this and...
                                 "target/generated-sources/custom-test-annotations",  // this, are explicitly specified as annotation folders
                                 "target/generated-test-sources/foo",
                                 "target/generated-test-sources/test-annotations"
    )
    maven.importProjectAsync($$"""
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
                         <generatedSourcesDirectory>${basedir}/target/generated-sources/custom-annotations</generatedSourcesDirectory>
                         <generatedTestSourcesDirectory>${basedir}/target/generated-sources/custom-test-annotations</generatedTestSourcesDirectory>
                       </configuration>
                      </plugin>
                     </plugins>
                    </build>
                    """.trimIndent())
    maven.assertSources("project",
                  "src/main/java",
                  "target/generated-sources/foo",
                  "target/generated-sources/annotations",
                  "target/generated-sources/custom-annotations")
    maven.assertTestSources("project",
                      "src/test/java",
                      "target/generated-sources/custom-test-annotations",
                      "target/generated-test-sources/foo",
                      "target/generated-test-sources/test-annotations")
  }

  @Test
  fun testSourceFoldersOrder() = runBlocking {
    maven.assumeOnLocalEnvironmentOnly("IDEA-378277")

    maven.createStdProjectFolders()
    val target = maven.createProjectSubDir("target")
    maven.createProjectSubDirsWithFile("anno",
                                 "test-anno",
                                 "target/generated-sources/foo",
                                 "target/generated-sources/annotations",
                                 "target/generated-sources/test-annotations",
                                 "target/generated-test-sources/foo")
    maven.importProjectAsync($$"""
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
                         <generatedSourcesDirectory>${basedir}/anno</generatedSourcesDirectory>
                         <generatedTestSourcesDirectory>${basedir}/test-anno</generatedTestSourcesDirectory>
                       </configuration>
                      </plugin>
                     </plugins>
                    </build>
                    """.trimIndent())
    val testAssertions = Consumer { shouldKeepGeneratedFolders: Boolean ->
      if (shouldKeepGeneratedFolders) {
        maven.assertSources("project",
                      "anno",
                      "src/main/java",
                      "target/generated-sources/annotations",
                      "target/generated-sources/foo",
                      "target/generated-sources/test-annotations")
      }
      else {
        maven.assertSources("project",
                      "anno",
                      "src/main/java")
      }
      maven.assertDefaultResources("project")
      if (shouldKeepGeneratedFolders) {
        maven.assertTestSources("project",
                          "src/test/java",
                          "target/generated-test-sources/foo",
                          "test-anno")
      }
      else {
        maven.assertTestSources("project",
                          "src/test/java",
                          "test-anno")
      }
      maven.assertDefaultTestResources("project")
    }
    testAssertions.accept(true)
    edtWriteAction {
      try {
        target.delete(this)
      }
      catch (e: IOException) {
        Assertions.fail("Unable to delete the file: " + e.message)
      }
    }
    testAssertions.accept(true)

    // incremental sync doesn't support updating source folders if effective pom dependencies haven't changed
    maven.updateAllProjectsFullSync()
    testAssertions.accept(false)
    maven.resolveFoldersAndImport()
    testAssertions.accept(false)
  }

  @Test
  fun testDoesNotAddAlreadyRegisteredSourcesUnderGeneratedDir() = runBlocking {
    maven.createStdProjectFolders()
    maven.createProjectSubDirs("target/generated-sources/main/src",
                         "target/generated-test-sources/test/src")
    maven.importProjectAsync("""
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
    maven.resolveFoldersAndImport()
    maven.assertSources("project",
                  "src/main/java",
                  "target/generated-sources/main/src")
    maven.assertDefaultResources("project")
    maven.assertTestSources("project",
                      "src/test/java",
                      "target/generated-test-sources/test/src")
    maven.assertDefaultTestResources("project")
  }
}