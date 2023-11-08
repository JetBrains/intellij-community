// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.project.MavenFolderResolver
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.plugins.groovy.compiler.GreclipseIdeaCompilerSettings
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils
import org.junit.Test
import java.io.File
import java.util.*

class GroovyImporterTest : MavenMultiVersionImportingTestCase() {
  private var repoPath: String? = null

  override fun runInDispatchThread() = false

  override fun setUp() {
    super.setUp()
    repoPath = File(myDir, "repo").path
    repositoryPath = repoPath
  }

  @Test
  fun testConfiguringFacetWithoutLibrary() = runBlocking {
    importProjectAsync("""
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
                    """.trimIndent())

    assertModules("project")

    assertUnorderedElementsAreEqual(GroovyConfigUtils.getInstance().getSDKLibrariesByModule(getModule("project")))
  }

  @Test
  fun testConfiguringFacetWithLibrary() = runBlocking {
    importProjectAsync("""
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
                    """.trimIndent())

    assertModules("project")

    val libraries = GroovyConfigUtils.getInstance().getSDKLibrariesByModule(getModule("project"))
    assertTrue("unexpected groovy libs configuration: " + libraries.size, libraries.size > 0)
    val library = libraries[0]
    assertUnorderedPathsAreEqual(
      Arrays.asList(*library.getUrls(OrderRootType.CLASSES)),
      Arrays.asList("jar://" + getRepositoryPath() + "/org/codehaus/groovy/groovy-all-minimal/1.5.6/groovy-all-minimal-1.5.6.jar!/"))
  }

  @Test
  fun testAddingGroovySpecificSources() = runBlocking {
    if (!supportsImportOfNonExistingFolders()) {
      createStdProjectFolders()
      createProjectSubDirs("src/main/groovy",
                           "src/test/groovy")
    }

    importProjectAsync("""
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
                    """.trimIndent())

    assertModules("project")

    assertSources("project",
                  "src/main/groovy",
                  "src/main/java")
    assertDefaultResources("project")
    assertTestSources("project",
                      "src/test/groovy",
                      "src/test/java")
    assertDefaultTestResources("project")
  }

  @Test
  fun testAddingGroovySpecificSources2() = runBlocking {
    if (!supportsImportOfNonExistingFolders()) {
      createStdProjectFolders()
      createProjectSubDirs("src/main/groovy",
                           "src/test/groovy")
    }

    importProjectAsync("""
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
                    """.trimIndent())

    assertModules("project")

    assertSources("project",
                  "src/main/groovy",
                  "src/main/java")
    assertDefaultResources("project")
    assertTestSources("project",
                      "src/test/groovy",
                      "src/test/java")
    assertDefaultTestResources("project")
  }

  @Test
  fun testAddingGroovySpecificSources3GmavenPlus() = runBlocking {
    if (!supportsImportOfNonExistingFolders()) {
      createStdProjectFolders()
      createProjectSubDirs("src/main/groovy",
                           "src/test/groovy")
    }

    importProjectAsync("""
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
                    """.trimIndent())

    assertModules("project")

    assertSources("project",
                  "src/main/groovy",
                  "src/main/java")
    assertDefaultResources("project")
    assertTestSources("project",
                      "src/test/groovy",
                      "src/test/java")
    assertDefaultTestResources("project")
  }

  @Test
  fun testGroovyEclipsePlugin() = runBlocking {
    val batchDir = File(repoPath, "org/codehaus/groovy/groovy-eclipse-batch/2.1.3-01/")
    batchDir.mkdirs()
    val batchJar = File(batchDir, "groovy-eclipse-batch-2.1.3-01.jar")
    batchJar.createNewFile()

    if (!supportsImportOfNonExistingFolders()) {
      createStdProjectFolders()
      createProjectSubDirs("src/main/groovy",
                           "src/test/groovy")
    }

    importProjectAsync("""
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
                    """.trimIndent())

    assertModules("project")

    assertSources("project",
                  "src/main/groovy",
                  "src/main/java")
    assertDefaultResources("project")
    assertTestSources("project",
                      "src/test/groovy",
                      "src/test/java")
    assertDefaultTestResources("project")

    val compilerSettings = myProject.getService(
      GreclipseIdeaCompilerSettings::class.java)
    assertEquals(LocalFileSystem.getInstance().findFileByIoFile(batchJar)!!.getPath(), compilerSettings.state!!.greclipsePath)
  }

  @Test
  fun testGroovyEclipsePluginWhenOnlyCompilerDependency() = runBlocking {
    if (!supportsImportOfNonExistingFolders()) {
      createStdProjectFolders()
      createProjectSubDirs("src/main/groovy",
                           "src/test/groovy")
    }

    val batchDir = File(repoPath, "org/codehaus/groovy/groovy-eclipse-batch/2.1.3-01/")
    batchDir.mkdirs()
    val batchJar = File(batchDir, "groovy-eclipse-batch-2.1.3-01.jar")
    batchJar.createNewFile()


    importProjectAsync("""
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
                    """.trimIndent())

    assertModules("project")

    assertSources("project",
                  "src/main/groovy",
                  "src/main/java")
    assertDefaultResources("project")
    assertTestSources("project",
                      "src/test/groovy",
                      "src/test/java")
    assertDefaultTestResources("project")

    val compilerSettings = myProject.getService(
      GreclipseIdeaCompilerSettings::class.java)
    assertEquals(LocalFileSystem.getInstance().findFileByIoFile(batchJar)!!.getPath(), compilerSettings.state!!.greclipsePath)
  }

  @Test
  fun testAddingCustomGroovySpecificSources() = runBlocking {
    if (!supportsImportOfNonExistingFolders()) {
      createStdProjectFolders()
      createProjectSubDirs("src/main/groovy",
                           "src/foo1",
                           "src/foo2",
                           "src/test/groovy",
                           "src/test-foo1",
                           "src/test-foo2")
    }

    importProjectAsync("""
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
                    """.trimIndent())

    assertModules("project")

    assertSources("project",
                  "src/foo1",
                  "src/foo2",
                  "src/main/java")
    assertDefaultResources("project")
    assertTestSources("project",
                      "src/test-foo1",
                      "src/test-foo2",
                      "src/test/java")
    assertDefaultTestResources("project")
  }

  @Test
  fun testAddingCustomGroovySpecificSources2GmavenPlus() = runBlocking {
    if (!supportsImportOfNonExistingFolders()) {
      createStdProjectFolders()
      createProjectSubDirs("src/main/groovy",
                           "src/foo1",
                           "src/foo2",
                           "src/test/groovy",
                           "src/test-foo1",
                           "src/test-foo2")
    }

    importProjectAsync("""
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
                    """.trimIndent())

    assertModules("project")

    assertSources("project",
                  "src/foo1",
                  "src/foo2",
                  "src/main/java")
    assertDefaultResources("project")
    assertTestSources("project",
                      "src/test-foo1",
                      "src/test-foo2",
                      "src/test/java")
    assertDefaultTestResources("project")
  }

  @Test
  fun testAddingCustomGroovySpecificSourcesByRelativePath() = runBlocking {
    if (!supportsImportOfNonExistingFolders()) {
      createProjectSubDirs("src/foo",
                           "src/test-foo")
    }

    importProjectAsync("""
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
                    """.trimIndent())

    assertModules("project")

    if (supportsImportOfNonExistingFolders()) {
      assertSources("project", "src/foo", "src/main/java")
      assertTestSources("project", "src/test-foo", "src/test/java")
    }
    else {
      assertSources("project", "src/foo")
      assertTestSources("project", "src/test-foo")
    }
  }

  @Test
  fun testDoNotAddGroovySpecificGeneratedSources() = runBlocking {
    if (!supportsImportOfNonExistingFolders()) {
      createStdProjectFolders()
    }
    createProjectSubDirs("target/generated-sources/xxx/yyy",
                         "target/generated-sources/groovy-stubs/main/foo",
                         "target/generated-sources/groovy-stubs/test/bar")

    importProjectAsync("""
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
                    """.trimIndent())

    assertModules("project")

    if (supportsImportOfNonExistingFolders()) {
      assertSources("project",
                    "src/main/groovy",
                    "src/main/java",
                    "target/generated-sources/xxx")
      assertTestSources("project", "src/test/groovy", "src/test/java")
    }
    else {
      assertSources("project",
                    "src/main/java",
                    "target/generated-sources/xxx")
      assertTestSources("project",
                        "src/test/java")
    }
    assertDefaultResources("project")
    assertDefaultTestResources("project")

    assertExcludes("project", "target")
  }

  @Test
  fun testDoNotAddCustomGroovySpecificGeneratedSources() = runBlocking {
    if (!supportsImportOfNonExistingFolders()) {
      createStdProjectFolders()
    }
    createProjectSubDirs("target/generated-sources/xxx/yyy",
                         "target/generated-sources/foo/aaa",
                         "target/generated-sources/bar/bbb")

    importProjectAsync("""
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
                    """.trimIndent())

    assertModules("project")

    if (supportsImportOfNonExistingFolders()) {
      assertSources("project",
                    "src/main/groovy",
                    "src/main/java",
                    "target/generated-sources/xxx")
      assertTestSources("project", "src/test/groovy", "src/test/java")
    }
    else {
      assertSources("project",
                    "src/main/java",
                    "target/generated-sources/xxx")
      assertTestSources("project",
                        "src/test/java")
    }
    assertDefaultResources("project")
    assertDefaultTestResources("project")

    assertExcludes("project", "target")
  }

  @Test
  fun testDoNotAddCustomGroovySpecificGeneratedSourcesByRelativePath() = runBlocking {
    createProjectSubDirs("target/generated-sources/xxx/yyy",
                         "target/generated-sources/foo/aaa",
                         "target/generated-sources/bar/bbb")

    importProjectAsync("""
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
                    """.trimIndent())

    assertModules("project")

    if (supportsImportOfNonExistingFolders()) {
      assertSources("project",
                    "src/main/groovy",
                    "src/main/java",
                    "target/generated-sources/xxx")
      assertTestSources("project",
                        "src/test/groovy",
                        "src/test/java")
    }
    else {
      assertSources("project",
                    "target/generated-sources/xxx")
      assertTestSources("project")
    }

    assertExcludes("project", "target")
  }

  @Test
  fun testUpdatingGroovySpecificGeneratedSourcesOnFoldersUpdate() = runBlocking {
    try {
      importProjectAsync("""
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
                                  <goal>generateTestStubs</goal>
                                </goals>
                              </execution>
                            </executions>
                          </plugin>
                        </plugins>
                      </build>
                      """.trimIndent())

      writeAction {
        val a = MavenRootModelAdapter(
          MavenRootModelAdapterLegacyImpl(projectsTree.findProject(myProjectPom)!!,
                                          getModule("project"),
                                          ProjectDataManager.getInstance().createModifiableModelsProvider(myProject)))
        a.unregisterAll("$projectPath/target", true, true)
        a.rootModel.commit()
      }

      if (supportsImportOfNonExistingFolders()) {
        assertSources("project", "src/main/groovy", "src/main/java")
        assertTestSources("project", "src/test/groovy", "src/test/java")
      }
      else {
        assertSources("project")
        assertTestSources("project")
      }
      assertExcludes("project")

      if (!supportsImportOfNonExistingFolders()) {
        createProjectSubDirs("src/main/groovy",
                             "src/test/groovy")
      }

      createProjectSubDirs("target/generated-sources/xxx/yyy",
                           "target/generated-sources/groovy-stubs/main/foo",
                           "target/generated-sources/groovy-stubs/test/bar")

      val projectsManager = projectsManager
      MavenFolderResolver(projectsManager.project).resolveFoldersAndImport(projectsManager.projects)

      if (supportsImportOfNonExistingFolders()) {
        assertSources("project",
                      "src/main/groovy",
                      "src/main/java",
                      "target/generated-sources/xxx")
        assertTestSources("project",
                          "src/test/groovy",
                          "src/test/java")
      }
      else {
        assertSources("project",
                      "src/main/groovy",
                      "target/generated-sources/xxx")
        assertTestSources("project",
                          "src/test/groovy")
      }

      assertExcludes("project", "target")
    }
    finally {
      // do not lock files by maven process
      MavenServerManager.getInstance().shutdown(true)
    }
  }

  @Test
  fun testDoNotAddGroovySpecificGeneratedSourcesForGMaven_1_2() = runBlocking {
    if (!supportsImportOfNonExistingFolders()) {
      createStdProjectFolders()
    }

    createProjectSubDirs("target/generated-sources/xxx/yyy",
                         "target/generated-sources/groovy-stubs/main/foo",
                         "target/generated-sources/groovy-stubs/test/bar")

    importProjectAsync("""
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
                    """.trimIndent())

    assertModules("project")

    if (supportsImportOfNonExistingFolders()) {
      assertSources("project",
                    "src/main/groovy",
                    "src/main/java",
                    "target/generated-sources/xxx")
      assertDefaultResources("project")
      assertTestSources("project", "src/test/groovy", "src/test/java")
      assertDefaultTestResources("project")
    }
    else {
      assertSources("project",
                    "src/main/java",
                    "target/generated-sources/xxx")
      assertDefaultResources("project")
      assertTestSources("project", "src/test/java")
      assertDefaultTestResources("project")
    }

    assertExcludes("project", "target")
  }
}
