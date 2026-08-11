// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.groovy

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertDefaultResources
import com.intellij.maven.testFramework.fixtures.assertDefaultTestResources
import com.intellij.maven.testFramework.fixtures.assertExcludes
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.assertSources
import com.intellij.maven.testFramework.fixtures.assertTestSources
import com.intellij.maven.testFramework.fixtures.assertUnorderedElementsAreEqual
import com.intellij.maven.testFramework.fixtures.assertUnorderedPathsAreEqual
import com.intellij.maven.testFramework.fixtures.createProjectSubDirs
import com.intellij.maven.testFramework.fixtures.getModule
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.projectPath
import com.intellij.maven.testFramework.fixtures.projectsTree
import com.intellij.maven.testFramework.fixtures.repositoryPathCanonical
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.utils.io.createFile
import com.intellij.util.io.createDirectories
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter
import org.jetbrains.idea.maven.importing.MavenRootModelAdapterLegacyImpl
import org.jetbrains.idea.maven.project.MavenFolderResolver
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.plugins.groovy.compiler.GreclipseIdeaCompilerSettings
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.nio.file.Path

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class GroovyImporterTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )

  @BeforeEach
  fun before() {
    maven.repositoryPath = maven.dir.resolve("repo")
  }

  @Test
  fun testConfiguringFacetWithoutLibrary() = runBlocking {
      maven.importProjectAsync(
          """
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.groovy.maven</groupId>
                          <artifactId>gmaven-plugin</artifactId>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent()
      )

      maven.assertModules("project")

      assertUnorderedElementsAreEqual(GroovyConfigUtils.getInstance().getSDKLibrariesByModule(maven.getModule("project")).asList())
  }

  @Test
  fun testConfiguringFacetWithLibrary() = runBlocking {
      maven.importProjectAsync(
          """
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>org.codehaus.groovy.maven.runtime</groupId>
                        <artifactId>gmaven-runtime-default</artifactId>
                        <version>1.0-rc-1</version>
                      </dependency>
                    </dependencies>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.groovy.maven</groupId>
                          <artifactId>gmaven-plugin</artifactId>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent()
      )

      maven.assertModules("project")

      val libraries = GroovyConfigUtils.getInstance().getSDKLibrariesByModule(maven.getModule("project"))
      assertTrue(libraries.size > 0, "unexpected groovy libs configuration: " + libraries.size)
      val library = libraries[0]
      assertUnorderedPathsAreEqual(
          listOf(*library.getUrls(OrderRootType.CLASSES)),
          listOf("jar://${maven.repositoryPathCanonical}/org/codehaus/groovy/groovy-all-minimal/1.5.6/groovy-all-minimal-1.5.6.jar!/")
      )
  }

  @Test
  fun testAddingGroovySpecificSources() = runBlocking {
      maven.importProjectAsync(
          """
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.groovy.maven</groupId>
                          <artifactId>gmaven-plugin</artifactId>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent()
      )

      maven.assertModules("project")

      maven.assertSources(
          "project",
          "src/main/groovy",
          "src/main/java"
      )
      maven.assertDefaultResources("project")
      maven.assertTestSources(
          "project",
          "src/test/groovy",
          "src/test/java"
      )
      maven.assertDefaultTestResources("project")
  }

  @Test
  fun testAddingGroovySpecificSources2() = runBlocking {
      maven.importProjectAsync(
          """
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.gmaven</groupId>
                          <artifactId>groovy-maven-plugin</artifactId>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent()
      )

      maven.assertModules("project")

      maven.assertSources(
          "project",
          "src/main/groovy",
          "src/main/java"
      )
      maven.assertDefaultResources("project")
      maven.assertTestSources(
          "project",
          "src/test/groovy",
          "src/test/java"
      )
      maven.assertDefaultTestResources("project")
  }

  @Test
  fun testAddingGroovySpecificSources3GmavenPlus() = runBlocking {
      maven.importProjectAsync(
          """
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.gmavenplus</groupId>
                          <artifactId>gmavenplus-plugin</artifactId>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent()
      )

      maven.assertModules("project")

      maven.assertSources(
          "project",
          "src/main/groovy",
          "src/main/java"
      )
      maven.assertDefaultResources("project")
      maven.assertTestSources(
          "project",
          "src/test/groovy",
          "src/test/java"
      )
      maven.assertDefaultTestResources("project")
  }

  @Test
  fun testGroovyEclipsePlugin() = runBlocking {
      val batchDir = maven.repositoryPath.resolve("org/codehaus/groovy/groovy-eclipse-batch/2.1.3-01/")
      batchDir.createDirectories()
      val batchJar = batchDir.resolve("groovy-eclipse-batch-2.1.3-01.jar")
      batchJar.createFile()

      maven.importProjectAsync(
          """
                    <groupId>test</groupId><artifactId>project</artifactId><version>1</version><dependencies>
                      <dependency>
                        <groupId>org.codehaus.groovy</groupId>
                        <artifactId>groovy-all</artifactId>
                        <version>2.1.0</version>
                      </dependency>
                    </dependencies><build>
                      <pluginManagement>
                        <plugins>
                          <plugin>
                            <artifactId>maven-compiler-plugin</artifactId>
                            <configuration>
                              <compilerId>groovy-eclipse-compiler</compilerId>
                              <source>1.7</source>
                              <target>1.7</target>
                              <showWarnings>false</showWarnings>
                            </configuration>
                            <dependencies>
                              <dependency>
                                <groupId>org.codehaus.groovy</groupId>
                                <artifactId>groovy-eclipse-compiler</artifactId>
                                <version>2.8.0-01</version>
                              </dependency>
                              <dependency>
                                <groupId>org.codehaus.groovy</groupId>
                                <artifactId>groovy-eclipse-batch</artifactId>
                                <version>2.1.3-01</version>
                              </dependency>
                            </dependencies>
                          </plugin>
                          <plugin>
                            <groupId>org.codehaus.groovy</groupId>
                            <artifactId>groovy-eclipse-compiler</artifactId>
                            <version>2.8.0-01</version>
                            <extensions>true</extensions>
                          </plugin>
                        </plugins>
                      </pluginManagement>
                    </build>
                    """.trimIndent()
      )

      maven.assertModules("project")

      maven.assertSources(
          "project",
          "src/main/groovy",
          "src/main/java"
      )
      maven.assertDefaultResources("project")
      maven.assertTestSources(
          "project",
          "src/test/groovy",
          "src/test/java"
      )
      maven.assertDefaultTestResources("project")

      val compilerSettings = maven.project.getService(
          GreclipseIdeaCompilerSettings::class.java
      )
      assertEquals(
          LocalFileSystem.getInstance().findFileByNioFile(batchJar)!!.toNioPath(),
          Path.of(compilerSettings.state!!.greclipsePath)
      )
  }

  @Test
  fun testGroovyEclipsePluginWhenOnlyCompilerDependency() = runBlocking {
      val batchDir = maven.repositoryPath.resolve("org/codehaus/groovy/groovy-eclipse-batch/2.1.3-01/")
      batchDir.createDirectories()
      val batchJar = batchDir.resolve("groovy-eclipse-batch-2.1.3-01.jar")
      batchJar.createFile()

      maven.importProjectAsync(
          """
                    <groupId>test</groupId><artifactId>project</artifactId><version>1</version><build>
                      <pluginManagement>
                        <plugins>
                          <plugin>
                            <artifactId>maven-compiler-plugin</artifactId>
                            <configuration>
                              <compilerId>groovy-eclipse-compiler</compilerId>
                              <source>1.7</source>
                              <target>1.7</target>
                              <showWarnings>false</showWarnings>
                            </configuration>
                            <dependencies>
                              <dependency>
                                <groupId>org.codehaus.groovy</groupId>
                                <artifactId>groovy-eclipse-compiler</artifactId>
                                <version>2.8.0-01</version>
                              </dependency>
                              <dependency>
                                <groupId>org.codehaus.groovy</groupId>
                                <artifactId>groovy-eclipse-batch</artifactId>
                                <version>2.1.3-01</version>
                              </dependency>
                            </dependencies>
                          </plugin>
                          <plugin>
                            <groupId>org.codehaus.groovy</groupId>
                            <artifactId>groovy-eclipse-compiler</artifactId>
                            <version>2.8.0-01</version>
                            <extensions>true</extensions>
                          </plugin>
                        </plugins>
                      </pluginManagement>
                    </build>
                    """.trimIndent()
      )

      maven.assertModules("project")

      maven.assertSources(
          "project",
          "src/main/groovy",
          "src/main/java"
      )
      maven.assertDefaultResources("project")
      maven.assertTestSources(
          "project",
          "src/test/groovy",
          "src/test/java"
      )
      maven.assertDefaultTestResources("project")

      val compilerSettings = maven.project.getService(GreclipseIdeaCompilerSettings::class.java)
      assertEquals(
          LocalFileSystem.getInstance().findFileByNioFile(batchJar)!!.toNioPath(),
          Path.of(compilerSettings.state!!.greclipsePath)
      )
  }

  @Test
  fun testAddingCustomGroovySpecificSources() = runBlocking {
      maven.importProjectAsync(
          """
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.groovy.maven</groupId>
                          <artifactId>gmaven-plugin</artifactId>
                          <executions>
                            <execution>
                              <id>one</id>
                              <goals>
                                <goal>compile</goal>
                              </goals>
                              <configuration>
                                <sources>
                                  <fileset>
                                    <directory>${'$'}{project.basedir}/src/foo1</directory>
                                  </fileset>
                                  <fileset>
                                    <directory>${'$'}{project.basedir}/src/foo2</directory>
                                  </fileset>
                                </sources>
                              </configuration>
                            </execution>
                            <execution>
                              <id>two</id>
                              <goals>
                                <goal>testCompile</goal>
                              </goals>
                              <configuration>
                                <sources>
                                  <fileset>
                                    <directory>${'$'}{project.basedir}/src/test-foo1</directory>
                                  </fileset>
                                  <fileset>
                                    <directory>${'$'}{project.basedir}/src/test-foo2</directory>
                                  </fileset>
                                </sources>
                              </configuration>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent()
      )

      maven.assertModules("project")

      maven.assertSources(
          "project",
          "src/foo1",
          "src/foo2",
          "src/main/java"
      )
      maven.assertDefaultResources("project")
      maven.assertTestSources(
          "project",
          "src/test-foo1",
          "src/test-foo2",
          "src/test/java"
      )
      maven.assertDefaultTestResources("project")
  }

  @Test
  fun testAddingCustomGroovySpecificSources2GmavenPlus() = runBlocking {
      maven.importProjectAsync(
          """
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.gmavenplus</groupId>
                          <artifactId>gmavenplus-plugin</artifactId>
                          <executions>
                            <execution>
                              <id>one</id>
                              <goals>
                                <goal>compile</goal>
                              </goals>
                              <configuration>
                                <sources>
                                  <fileset>
                                    <directory>${'$'}{project.basedir}/src/foo1</directory>
                                  </fileset>
                                  <fileset>
                                    <directory>${'$'}{project.basedir}/src/foo2</directory>
                                  </fileset>
                                </sources>
                              </configuration>
                            </execution>
                            <execution>
                              <id>two</id>
                              <goals>
                                <goal>testCompile</goal>
                              </goals>
                              <configuration>
                                <sources>
                                  <fileset>
                                    <directory>${'$'}{project.basedir}/src/test-foo1</directory>
                                  </fileset>
                                  <fileset>
                                    <directory>${'$'}{project.basedir}/src/test-foo2</directory>
                                  </fileset>
                                </sources>
                              </configuration>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent()
      )

      maven.assertModules("project")

      maven.assertSources(
          "project",
          "src/foo1",
          "src/foo2",
          "src/main/java"
      )
      maven.assertDefaultResources("project")
      maven.assertTestSources(
          "project",
          "src/test-foo1",
          "src/test-foo2",
          "src/test/java"
      )
      maven.assertDefaultTestResources("project")
  }

  @Test
  fun testAddingCustomGroovySpecificSourcesByRelativePath() = runBlocking {
      maven.importProjectAsync(
          """
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.groovy.maven</groupId>
                          <artifactId>gmaven-plugin</artifactId>
                          <executions>
                            <execution>
                              <id>one</id>
                              <goals>
                                <goal>compile</goal>
                              </goals>
                              <configuration>
                                <sources>
                                  <fileset>
                                    <directory>src/foo</directory>
                                  </fileset>
                                </sources>
                              </configuration>
                            </execution>
                            <execution>
                              <id>two</id>
                              <goals>
                                <goal>testCompile</goal>
                              </goals>
                              <configuration>
                                <sources>
                                  <fileset>
                                    <directory>src/test-foo</directory>
                                  </fileset>
                                </sources>
                              </configuration>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent()
      )

      maven.assertModules("project")

      maven.assertSources("project", "src/foo", "src/main/java")
      maven.assertTestSources("project", "src/test-foo", "src/test/java")
  }

  @Test
  fun testDoNotAddGroovySpecificGeneratedSources() = runBlocking {
      maven.createProjectSubDirs(
          "target/generated-sources/xxx/yyy",
          "target/generated-sources/groovy-stubs/main/foo",
          "target/generated-sources/groovy-stubs/test/bar"
      )

      maven.importProjectAsync(
          """
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.groovy.maven</groupId>
                          <artifactId>gmaven-plugin</artifactId>
                          <executions>
                            <execution>
                              <goals>
                                <goal>generateStubs</goal>
                                <goal>compile</goal>
                                <goal>generateTestStubs</goal>
                                <goal>testCompile</goal>
                              </goals>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent()
      )

      maven.assertModules("project")

      maven.assertSources(
          "project",
          "src/main/groovy",
          "src/main/java",
          "target/generated-sources/xxx"
      )
      maven.assertTestSources("project", "src/test/groovy", "src/test/java")

      maven.assertDefaultResources("project")
      maven.assertDefaultTestResources("project")

      maven.assertExcludes("project", "target")
  }

  @Test
  fun testDoNotAddCustomGroovySpecificGeneratedSources() = runBlocking {
      maven.createProjectSubDirs(
          "target/generated-sources/xxx/yyy",
          "target/generated-sources/foo/aaa",
          "target/generated-sources/bar/bbb"
      )

      maven.importProjectAsync(
          """
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.groovy.maven</groupId>
                          <artifactId>gmaven-plugin</artifactId>
                          <executions>
                            <execution>
                              <id>one</id>
                              <goals>
                                <goal>generateStubs</goal>
                              </goals>
                              <configuration>
                                <outputDirectory>${'$'}{project.build.directory}/generated-sources/foo</outputDirectory>
                              </configuration>
                            </execution>
                            <execution>
                              <id>two</id>
                              <goals>
                                <goal>generateTestStubs</goal>
                              </goals>
                              <configuration>
                                <outputDirectory>${'$'}{project.build.directory}/generated-sources/bar</outputDirectory>
                              </configuration>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent()
      )

      maven.assertModules("project")

      maven.assertSources(
          "project",
          "src/main/groovy",
          "src/main/java",
          "target/generated-sources/xxx"
      )
      maven.assertTestSources("project", "src/test/groovy", "src/test/java")

      maven.assertDefaultResources("project")
      maven.assertDefaultTestResources("project")

      maven.assertExcludes("project", "target")
  }

  @Test
  fun testDoNotAddCustomGroovySpecificGeneratedSourcesByRelativePath() = runBlocking {
      maven.createProjectSubDirs(
          "target/generated-sources/xxx/yyy",
          "target/generated-sources/foo/aaa",
          "target/generated-sources/bar/bbb"
      )

      maven.importProjectAsync(
          """
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.groovy.maven</groupId>
                          <artifactId>gmaven-plugin</artifactId>
                          <executions>
                            <execution>
                              <id>one</id>
                              <goals>
                                <goal>generateStubs</goal>
                              </goals>
                              <configuration>
                                <outputDirectory>target/generated-sources/foo</outputDirectory>
                              </configuration>
                            </execution>
                            <execution>
                              <id>two</id>
                              <goals>
                                <goal>generateTestStubs</goal>
                              </goals>
                              <configuration>
                                <outputDirectory>target/generated-sources/bar</outputDirectory>
                              </configuration>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent()
      )

      maven.assertModules("project")

      maven.assertSources(
          "project",
          "src/main/groovy",
          "src/main/java",
          "target/generated-sources/xxx"
      )
      maven.assertTestSources(
          "project",
          "src/test/groovy",
          "src/test/java"
      )

      maven.assertExcludes("project", "target")
  }

  @Test
  fun testUpdatingGroovySpecificGeneratedSourcesOnFoldersUpdate() = runBlocking {
      try {
          maven.importProjectAsync(
              """
                      <groupId>test</groupId>
                      <artifactId>project</artifactId>
                      <version>1</version>
                      <build>
                        <plugins>
                          <plugin>
                            <groupId>org.codehaus.groovy.maven</groupId>
                            <artifactId>gmaven-plugin</artifactId>
                            <version>1.0</version>
                            <executions>
                              <execution>
                                <goals>
                                  <goal>generateStubs</goal>
                                  <goal>generateTestStubs</goal>
                                </goals>
                              </execution>
                            </executions>
                          </plugin>
                        </plugins>
                      </build>
                      """.trimIndent()
          )

          edtWriteAction {
              val a = MavenRootModelAdapter(
                  MavenRootModelAdapterLegacyImpl(
                      maven.projectsTree.findProject(maven.projectPom)!!,
                      maven.getModule("project"),
                      ProjectDataManager.getInstance().createModifiableModelsProvider(maven.project)
                  )
              )
              a.unregisterAll("${maven.projectPath}/target", true, true)
              a.rootModel.commit()
          }

          maven.assertSources("project", "src/main/groovy", "src/main/java")
          maven.assertTestSources("project", "src/test/groovy", "src/test/java")

          maven.assertExcludes("project")

          maven.createProjectSubDirs(
              "target/generated-sources/xxx/yyy",
              "target/generated-sources/groovy-stubs/main/foo",
              "target/generated-sources/groovy-stubs/test/bar"
          )

          val projectsManager = maven.projectsManager
          MavenFolderResolver(projectsManager.project).resolveFoldersAndImport(projectsManager.projects)

          maven.assertSources(
              "project",
              "src/main/groovy",
              "src/main/java",
              "target/generated-sources/xxx"
          )
          maven.assertTestSources(
              "project",
              "src/test/groovy",
              "src/test/java"
          )

          maven.assertExcludes("project", "target")
      } finally {
          // do not lock files by maven process
          MavenServerManager.getInstance().closeAllConnectorsAndWait()
      }
  }

  @Test
  fun testDoNotAddGroovySpecificGeneratedSourcesForGMaven_1_2() = runBlocking {
      maven.createProjectSubDirs(
          "target/generated-sources/xxx/yyy",
          "target/generated-sources/groovy-stubs/main/foo",
          "target/generated-sources/groovy-stubs/test/bar"
      )

      maven.importProjectAsync(
          """
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.gmaven</groupId>
                          <artifactId>gmaven-plugin</artifactId>
                          <version>1.2</version>
                          <executions>
                            <execution>
                              <goals>
                                <goal>generateStubs</goal>
                                <goal>compile</goal>
                                <goal>generateTestStubs</goal>
                                <goal>testCompile</goal>
                              </goals>
                            </execution>
                          </executions>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent()
      )

      maven.assertModules("project")

      maven.assertSources(
          "project",
          "src/main/groovy",
          "src/main/java",
          "target/generated-sources/xxx"
      )
      maven.assertDefaultResources("project")
      maven.assertTestSources("project", "src/test/groovy", "src/test/java")
      maven.assertDefaultTestResources("project")

      maven.assertExcludes("project", "target")
  }
}
