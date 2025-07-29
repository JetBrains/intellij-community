/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.importing

import com.intellij.execution.CommonProgramRunConfigurationParameters
import com.intellij.execution.util.ProgramParametersUtil
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.module.ModuleManager.Companion.getInstance
import com.intellij.openapi.project.Project
import com.intellij.util.ArrayUtil
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper
import org.jetbrains.idea.maven.project.MavenFolderResolver
import org.jetbrains.idea.maven.project.MavenImportingSettings
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.utils.MavenPathWrapper
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.function.Consumer
import kotlin.io.path.exists

class FoldersImportingTest : MavenMultiVersionImportingTestCase() {

  override fun setUp() {
    super.setUp()
    projectsManager.initForTests()
    projectsManager.listenForExternalChanges()
  }

  @Test
  fun testSimpleProjectStructure() = runBlocking {
    createStdProjectFolders()
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    assertModules("project")
    assertContentRoots("project", projectPath)
    assertSources("project", "src/main/java")
    assertDefaultResources("project")
    assertTestSources("project", "src/test/java")
    assertDefaultTestResources("project")
  }

  @Test
  fun testInvalidProjectHasContentRoot() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1
                       """.trimIndent())
    importProjectAsync()
    assertModules("project")
    assertContentRoots("project", projectPath)
  }

  @Test
  fun testDoNotResetFoldersAfterResolveIfProjectIsInvalid() = runBlocking {
    createStdProjectFolders()
    importProjectAsync("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>""")
    assertModules("project")
    assertSources("project", "src/main/java")
    assertDefaultResources("project")
    assertTestSources("project", "src/test/java")
    assertDefaultTestResources("project")

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <extensions>
                           <extension>
                             <groupId>xxx</groupId>
                             <artifactId>xxx</artifactId>
                             <version>xxx</version>
                           </extension>
                         </extensions>
                       </build>
                       """.trimIndent())
    importProjectAsync()
    assertModules("project")
    assertSources("project", "src/main/java")
    assertDefaultResources("project")
    assertTestSources("project", "src/test/java")
    assertDefaultTestResources("project")
  }

  @Test
  fun testDoesNotResetUserFolders() = runBlocking {
    val dir1 = createProjectSubDir("userSourceFolder")
    val dir2 = createProjectSubDir("userExcludedFolder")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    edtWriteAction {
      val adapter = MavenRootModelAdapter(MavenRootModelAdapterLegacyImpl(
        projectsTree.findProject(projectPom)!!,
        getModule("project"),
        ProjectDataManager.getInstance().createModifiableModelsProvider(project)))
      adapter.addSourceFolder(dir1.getPath(), JavaSourceRootType.SOURCE)
      adapter.addExcludedFolder(dir2.getPath())
      adapter.rootModel.commit()
    }
    assertSources("project", "userSourceFolder", "src/main/java")
    assertExcludes("project", "target", "userExcludedFolder")

    // incremental sync doesn't support updating source folders if effective pom dependencies haven't changed
    updateAllProjectsFullSync()
    assertSources("project", "src/main/java")
    assertExcludes("project", "target", "userExcludedFolder")
    resolveFoldersAndImport()
    assertSources("project", "src/main/java")
    assertExcludes("project", "target", "userExcludedFolder")
  }

  @Test
  fun testClearParentAndSubFoldersOfNewlyImportedFolders() = runBlocking {
    createStdProjectFolders()
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    assertSources("project", "src/main/java")
    assertDefaultResources("project")
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <sourceDirectory>src</sourceDirectory>
                       </build>
                       """.trimIndent())
    resolveFoldersAndImport()
    assertSources("project", "src")
    createProjectPom("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    updateAllProjects()
    assertSources("project", "src/main/java")
    assertDefaultResources("project")
  }

  @Test
  fun testSourceFoldersOnReimport() = runBlocking {
    createProjectSubDirs("src1", "src2")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <sourceDirectory>src1</sourceDirectory>
                    </build>
                    """.trimIndent())
    assertSources("project", "src1")
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <sourceDirectory>src2</sourceDirectory>
                       </build>
                       """.trimIndent())
    resolveFoldersAndImport()
    assertSources("project", "src2")
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <sourceDirectory>src1</sourceDirectory>
                       </build>
                       """.trimIndent())
    resolveFoldersAndImport()
    assertSources("project", "src1")
  }

  @Test
  fun testCustomSourceFolders() = runBlocking {
    createStdProjectFolders()
    createProjectSubDirs("src", "test", "res1", "res2", "testRes1", "testRes2")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <sourceDirectory>src</sourceDirectory>
                      <testSourceDirectory>test</testSourceDirectory>
                      <resources>
                        <resource><directory>res1</directory></resource>
                        <resource><directory>res2</directory></resource>
                      </resources>
                      <testResources>
                        <testResource><directory>testRes1</directory></testResource>
                        <testResource><directory>testRes2</directory></testResource>
                      </testResources>
                    </build>
                    """.trimIndent())
    assertModules("project")
    assertContentRoots("project", projectPath)
    assertSources("project", "src")
    assertResources("project", "res1", "res2")
    assertTestSources("project", "test")
    assertTestResources("project", "testRes1", "testRes2")
  }

  @Test
  fun testCustomSourceFoldersOutsideOfContentRoot() = runBlocking {
    createStdProjectFolders()
    createProjectSubDirs("m",
                         "src",
                         "test",
                         "res",
                         "testRes")
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())
    createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      <build>
        <sourceDirectory>../src</sourceDirectory>
        <testSourceDirectory>../test</testSourceDirectory>
        <resources>
          <resource><directory>../res</directory></resource>
        </resources>
        <testResources>
          <testResource><directory>../testRes</directory></testResource>
        </testResources>
      </build>
      """.trimIndent())
    importProjectAsync()
    assertModules("project", "m")
    assertContentRoots("project",
                       projectPath)
    assertContentRoots("m",
                       "$projectPath/m",
                       "$projectPath/src",
                       "$projectPath/test",
                       "$projectPath/res",
                       "$projectPath/testRes")
  }

  @Test
  fun testSourceFolderPointsToProjectRoot() = runBlocking {
    createStdProjectFolders()
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <sourceDirectory>${'$'}{basedir}</sourceDirectory>
                    </build>
                    """.trimIndent())
    assertModules("project")
    assertContentRoots("project", projectPath)
    assertSources("project", "")
    assertTestSources("project")
    assertResources("project")
    assertTestResources("project")
  }

  @Test
  fun testResourceFolderPointsToProjectRoot() = runBlocking {
    createStdProjectFolders()
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource><directory>${'$'}{basedir}</directory></resource>
                      </resources>
                    </build>
                    """.trimIndent())
    assertModules("project")
    assertContentRoots("project", projectPath)
    assertSources("project", "src/main/java")
    assertTestSources("project", "src/test/java")
    assertResources("project")
    assertDefaultTestResources("project")
  }

  @Test
  fun testResourceFolderPointsToProjectRootParent() = runBlocking {
    createStdProjectFolders()
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource><directory>${'$'}{basedir}/..</directory></resource>
                      </resources>
                    </build>
                    """.trimIndent())
    assertModules("project")
    assertContentRoots("project", projectPath)
    assertSources("project", "src/main/java")
    assertTestSources("project", "src/test/java")
    assertResources("project")
    assertDefaultTestResources("project")
  }

  @Test
  fun testSourceFolderPointsToProjectRootParent() = runBlocking {
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
  fun testAddingExistingGeneratedSources() = runBlocking {
    createStdProjectFolders()
    createProjectSubFile("target/generated-sources/src1/com/A.java", "package com; class A {}")
    createProjectSubFile("target/generated-sources/src2/com/B.java", "package com; class B {}")
    createProjectSubFile("target/generated-test-sources/test1/com/test/A.java", "package com.test; class A {}")
    createProjectSubFile("target/generated-test-sources/test2/com/test/B.java", "package com.test; class B {}")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    assertSources("project",
                  "src/main/java",
                  "target/generated-sources/src1",
                  "target/generated-sources/src2")
    assertDefaultResources("project")
    assertTestSources("project",
                      "src/test/java",
                      "target/generated-test-sources/test1",
                      "target/generated-test-sources/test2")
    assertDefaultTestResources("project")
  }

  @Test
  fun testAddingExistingGeneratedSourcesInPerSourceTypeModules() = runBlocking {
    createStdProjectFolders()
    createProjectSubFile("target/generated-sources/src1/com/A.java", "package com; class A {}")
    createProjectSubFile("target/generated-sources/src2/com/B.java", "package com; class B {}")
    createProjectSubFile("target/generated-test-sources/test1/com/test/A.java", "package com.test; class A {}")
    createProjectSubFile("target/generated-test-sources/test2/com/test/B.java", "package com.test; class B {}")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                      <maven.compiler.source>8</maven.compiler.source>
                      <maven.compiler.target>8</maven.compiler.target>
                      <maven.compiler.testSource>11</maven.compiler.testSource>
                      <maven.compiler.testTarget>11</maven.compiler.testTarget>
                    </properties>
                    """.trimIndent())
    assertModules("project", "project.main", "project.test")
    assertContentRoots("project", projectPath)
    assertSources("project")
    assertResources("project")
    assertTestSources("project")
    assertTestResources("project")
    assertExcludes("project", "target")

    val mainSources = arrayOfNotNull(
      "$projectPath/src/main/java",
      "$projectPath/target/generated-sources/src1",
      "$projectPath/target/generated-sources/src2"
    )
    val testSources = arrayOfNotNull(
      "$projectPath/src/test/java",
      "$projectPath/target/generated-test-sources/test1",
      "$projectPath/target/generated-test-sources/test2"
    )

    assertSources("project.main", *mainSources)
    assertDefaultResources("project.main")
    assertTestSources("project.main")
    assertTestResources("project.main")

    assertSources("project.test")
    assertResources("project.test")
    assertTestSources("project.test", *testSources)
    assertDefaultTestResources("project.test")
  }

  @Test
  fun testContentRootOutsideOfModuleDirInPerSourceTypeImport() = runBlocking {
    createModulePom("m1",
                    """
                      <groupId>test</groupId>
                      <artifactId>m1</artifactId>
                      <version>1</version>
                      <parent>
                        <groupId>test</groupId>
                        <artifactId>project</artifactId>
                        <version>1</version>
                      </parent>
                      <build>
                        <sourceDirectory>../custom-sources</sourceDirectory>
                      </build>
                      """.trimIndent())
    createProjectSubFile("custom-sources/com/CustomSource.java", "package com; class CustomSource {}")
    createProjectSubFile("m1/src/main/resources/test.txt", "resource")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <properties>
                      <maven.compiler.source>8</maven.compiler.source>
                      <maven.compiler.target>8</maven.compiler.target>
                      <maven.compiler.testSource>11</maven.compiler.testSource>
                      <maven.compiler.testTarget>11</maven.compiler.testTarget>
                    </properties>
                    <modules>
                      <module>m1</module>
                    </modules>
                    """.trimIndent())
    assertModules("project",
                  mn("project", "m1"),
                  mn("project", "m1.main"),
                  mn("project", "m1.test"))
    assertSources("m1.main", "../custom-sources")
    assertDefaultResources("m1.main")
  }

  @Test
  fun testAddingExistingGeneratedSources2() = runBlocking {
    createStdProjectFolders()
    createProjectSubFile("target/generated-sources/com/A.java", "package com; class A {}")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    assertSources("project",
                  "src/main/java",
                  "target/generated-sources")
    assertDefaultResources("project")
  }

  @Test
  fun testAddingExistingGeneratedSources3() = runBlocking {
    createStdProjectFolders()
    MavenProjectsManager.getInstance(project).importingSettings.setGeneratedSourcesFolder(
      MavenImportingSettings.GeneratedSourcesFolder.SUBFOLDER)
    createProjectSubFile("target/generated-sources/com/A.java", "package com; class A {}")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    assertSources("project",
                  "src/main/java",
                  "target/generated-sources/com")
    assertDefaultResources("project")
  }

  @Test
  fun testOverrideAnnotationSources() = runBlocking {
    createStdProjectFolders()
    MavenProjectsManager.getInstance(project).importingSettings.setGeneratedSourcesFolder(
      MavenImportingSettings.GeneratedSourcesFolder.GENERATED_SOURCE_FOLDER)
    createProjectSubFile("target/generated-sources/com/A.java", "package com; class A {}")
    createProjectSubFile("target/generated-sources/annotations/com/B.java", "package com; class B {}")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    assertSources("project",
                  "src/main/java",
                  "target/generated-sources")
    assertDefaultResources("project")
  }

  @Test
  fun testOverrideAnnotationSourcesWhenAutodetect() = runBlocking {
    createStdProjectFolders()
    MavenProjectsManager.getInstance(project).importingSettings.setGeneratedSourcesFolder(
      MavenImportingSettings.GeneratedSourcesFolder.AUTODETECT)
    createProjectSubFile("target/generated-sources/com/A.java", "package com; class A {}")
    createProjectSubFile("target/generated-sources/annotations/com/B.java", "package com; class B {}")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    assertSources("project",
                  "src/main/java",
                  "target/generated-sources")
    assertDefaultResources("project")
  }

  @Test
  fun testOverrideTestAnnotationSourcesWhenAutodetect() = runBlocking {
    createStdProjectFolders()
    MavenProjectsManager.getInstance(project).importingSettings.setGeneratedSourcesFolder(
      MavenImportingSettings.GeneratedSourcesFolder.AUTODETECT)
    createProjectSubFile("target/generated-test-sources/com/A.java", "package com; class A {}")
    createProjectSubFile("target/generated-test-sources/test-annotations/com/B.java", "package com; class B {}")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    assertSources("project", "src/main/java")
    assertTestSources("project",
                      "src/test/java",
                      "target/generated-test-sources")
    assertDefaultResources("project")
  }

  @Test
  fun testIgnoreGeneratedSources() = runBlocking {
    createStdProjectFolders()
    MavenProjectsManager.getInstance(project).importingSettings.setGeneratedSourcesFolder(
      MavenImportingSettings.GeneratedSourcesFolder.IGNORE)
    createProjectSubFile("target/generated-sources/annotations/A.java", "package com; class A {}")
    createProjectSubFile("target/generated-sources/annotations/com/B.java", "package com; class B {}")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    assertSources("project", "src/main/java")
    assertDefaultResources("project")
  }

  @Test
  fun testAddingExistingGeneratedSources4() = runBlocking {
    createStdProjectFolders()
    createProjectSubFile("target/generated-sources/A1/B1/com/A1.java", "package com; class A1 {}")
    createProjectSubFile("target/generated-sources/A1/B2/com/A2.java", "package com; class A2 {}")
    createProjectSubFile("target/generated-sources/A2/com/A3.java", "package com; class A3 {}")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    assertSources("project",
                  "src/main/java",
                  "target/generated-sources/A1/B1",
                  "target/generated-sources/A1/B2",
                  "target/generated-sources/A2")
    assertDefaultResources("project")
  }

  @Test
  fun testAddingExistingGeneratedSources5() = runBlocking {
    createStdProjectFolders()
    createProjectSubFile("target/generated-sources/A1/B1/com/A1.java", "package com; class A1 {}")
    createProjectSubFile("target/generated-sources/A2.java", "class A2 {}")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    assertSources("project",
                  "src/main/java",
                  "target/generated-sources")
    assertDefaultResources("project")
  }

  @Test
  fun testAddingExistingGeneratedSourcesWithCustomTargetDir() = runBlocking {
    createStdProjectFolders()
    createProjectSubDirsWithFile("targetCustom/generated-sources/src",
                                 "targetCustom/generated-test-sources/test")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <directory>targetCustom</directory>
                    </build>
                    """.trimIndent())
    assertSources("project",
                  "src/main/java",
                  "targetCustom/generated-sources/src")
    assertDefaultResources("project")
    assertTestSources("project",
                      "src/test/java",
                      "targetCustom/generated-test-sources/test")
    assertDefaultTestResources("project")
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

  @Test
  fun testIgnoringFilesRightUnderGeneratedSources() = runBlocking {
    createProjectSubFile("target/generated-sources/f.txt")
    createProjectSubFile("target/generated-test-sources/f.txt")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    assertSources("project", "src/main/java")
    assertDefaultResources("project")
    assertTestSources("project", "src/test/java")
    assertDefaultTestResources("project")
    assertExcludes("project", "target")
  }

  @Test
  fun testExcludingOutputDirectories() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    assertModules("project")
    assertExcludes("project", "target")
    assertModuleOutput("project",
                       "$projectPath/target/classes",
                       "$projectPath/target/test-classes")
  }

  @Test
  fun testExcludingOutputDirectoriesIfProjectOutputIsUsed() = runBlocking {
    mavenImporterSettings.isUseMavenOutput = false
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <directory>foo</directory>
                    </build>
                    """.trimIndent())
    assertModules("project")
    assertExcludes("project", "foo")
    assertProjectOutput("project")
  }

  @Test
  fun testUnloadedModules() = runBlocking {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +
                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>")
    createModulePom("m1",
                    "<groupId>test</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>1</version>")
    createModulePom("m2",
                    "<groupId>test</groupId>" +
                    "<artifactId>m2</artifactId>" +
                    "<version>1</version>")
    importProjectAsync()
    assertModules("project", "m1", "m2")
    getInstance(project).setUnloadedModulesSync(listOf("m2"))
    assertModules("project", "m1")
    importProjectAsync()
    assertModules("project", "m1")
    val m2 = getInstance(project).getUnloadedModuleDescription("m2")
    assertNotNull(m2)
    assertEquals("m2", m2!!.getName())
  }

  @Test
  fun testExcludingCustomOutputDirectories() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <directory>targetCustom</directory>
                      <outputDirectory>outputCustom</outputDirectory>
                      <testOutputDirectory>testCustom</testOutputDirectory>
                    </build>
                    """.trimIndent())
    assertModules("project")
    assertExcludes("project",
                   "outputCustom",
                   "targetCustom",
                   "testCustom")
    assertModuleOutput("project",
                       "$projectPath/outputCustom",
                       "$projectPath/testCustom")
  }

  @Test
  fun testExcludingCustomOutputUnderTargetUsingStandardVariable() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <outputDirectory>${'$'}{project.build.directory}/outputCustom</outputDirectory>
                      <testOutputDirectory>${'$'}{project.build.directory}/testCustom</testOutputDirectory>
                    </build>
                    """.trimIndent())
    assertModules("project")
    assertExcludes("project", "target")
    assertModuleOutput("project",
                       "$projectPath/target/outputCustom",
                       "$projectPath/target/testCustom")
  }

  @Test
  fun testDoNotExcludeExcludeOutputDirectoryWhenItPointstoRoot() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <outputDirectory>.</outputDirectory>
                      <testOutputDirectory>.</testOutputDirectory>
                    </build>
                    """.trimIndent())
    assertModules("project")
    assertExcludes("project",
                   "target")
    assertModuleOutput("project",
                       projectPath,
                       projectPath)
  }

  @Test
  fun testOutputDirsOutsideOfContentRoot() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <directory>../target</directory>
                      <outputDirectory>../target/classes</outputDirectory>
                      <testOutputDirectory>../target/test-classes</testOutputDirectory>
                    </build>
                    """.trimIndent())
    val targetPath = "$parentPath/target"
    val targetUrl = MavenPathWrapper(targetPath).toUrl().url
    assertContentRoots("project", projectPath)
    assertModuleOutput("project",
                       "$parentPath/target/classes",
                       "$parentPath/target/test-classes")
  }

  @Test
  fun testCustomPomFileNameDefaultContentRoots() = runBlocking {
    createProjectSubFile("m1/customName.xml", createPomXml(
      """
        <artifactId>m1</artifactId>
        <version>1</version>
        <parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
        </parent>
        <build>
          <sourceDirectory>sources</sourceDirectory>
          <testSourceDirectory>tests</testSourceDirectory>
        </build>
        """.trimIndent()))
    File(projectRoot.getPath(), "m1/sources").mkdirs()
    File(projectRoot.getPath(), "m1/tests").mkdirs()
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <packaging>pom</packaging>
                    <version>1</version>
                    <modules>
                      <module>m1/customName.xml</module>
                    </modules>
                    """.trimIndent())
    assertContentRoots(mn("project", "m1"), "$projectPath/m1")
  }

  @Test
  fun testCustomPomFileNameCustomContentRoots() = runBlocking {
    createProjectSubFile("m1/pom.xml", createPomXml(
      """
        <artifactId>m1-pom</artifactId>
        <version>1</version>
        <parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
        </parent>
        """.trimIndent()))
    createProjectSubFile("m1/custom.xml", createPomXml(
      """
        <artifactId>m1-custom</artifactId>
        <version>1</version>
        <parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
        </parent>
        <build>
          <resources><resource><directory>sources/resources</directory></resource></resources>
          <sourceDirectory>sources</sourceDirectory>
          <testSourceDirectory>tests</testSourceDirectory>
        </build>
        """.trimIndent()))
    createStdProjectFolders("m1")
    createProjectSubDirs("m1/sources/resources",
                         "m1/tests")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <packaging>pom</packaging>
                    <version>1</version>
                    <modules>
                      <module>m1</module>
                      <module>m1/custom.xml</module>
                    </modules>
                    """.trimIndent())
    val m1_pom_module = mn("project", "m1-pom")
    val m1_custom_module = mn("project", "m1-custom")
    assertModules("project", m1_pom_module, m1_custom_module)
    val m1_pom_root = "$projectPath/m1"
    assertContentRoots(m1_pom_module, m1_pom_root)
    assertContentRootSources(m1_pom_module, m1_pom_root, "src/main/java")
    val expectedResources = ArrayList<String>()
    expectedResources.add("src/main/resources")
    if (isModel410()) {
      expectedResources.add("src/main/resources-filtered")
    }
    assertContentRootResources(m1_pom_module, m1_pom_root, *ArrayUtil.toStringArray(expectedResources))
    assertContentRootTestSources(m1_pom_module, m1_pom_root, "src/test/java")
    val expectedTestResources = ArrayList<String>()
    expectedTestResources.add("src/test/resources")
    if (isModel410()) {
      expectedTestResources.add("src/test/resources-filtered")
    }
    assertContentRootTestResources(m1_pom_module, m1_pom_root, *ArrayUtil.toStringArray(expectedTestResources))
    val m1_custom_sources_root = "$projectPath/m1/sources"
    val m1_custom_tests_root = "$projectPath/m1/tests"
    val m1_standard_test_resources = "$projectPath/m1/src/test/resources"
    val m1_standard_test_resources_list = ArrayList<String>()

    // [anton] The next folder doesn't look correct, as it intersects with 'pom.xml' module folders,
    // but I'm testing the behavior as is in order to preserve it in the new Workspace import
    m1_standard_test_resources_list.add(m1_standard_test_resources)
    if (isModel410()) {
      m1_standard_test_resources_list.add("$m1_standard_test_resources-filtered")
    }
    assertSources(m1_custom_module, m1_custom_sources_root)
    assertResources(m1_custom_module)
    assertTestSources(m1_custom_module, m1_custom_tests_root)
    assertTestResources(m1_custom_module, *m1_standard_test_resources_list.toTypedArray())
  }

  @Test
  fun testContentRootOutsideOfModuleDir() = runBlocking {
    createProjectSubFile("m1/pom.xml", createPomXml(
      """
        <artifactId>m1-pom</artifactId>
        <version>1</version>
        <parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
        </parent>
        <build>
          <sourceDirectory>../pom-sources</sourceDirectory>
        </build>
        """.trimIndent()))
    createProjectSubFile("m1/custom.xml", createPomXml(
      """
        <artifactId>m1-custom</artifactId>
        <version>1</version>
        <parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
        </parent>
        <build>
          <sourceDirectory>../custom-sources</sourceDirectory>
        </build>
        """.trimIndent()))
    File(projectRoot.getPath(), "pom-sources").mkdirs()
    File(projectRoot.getPath(), "custom-sources").mkdirs()
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <packaging>pom</packaging>
                    <version>1</version>
                    <modules>
                      <module>m1</module>
                      <module>m1/custom.xml</module>
                    </modules>
                    """.trimIndent())
    assertModules("project", mn("project", "m1-pom"), mn("project", "m1-custom"))
    assertContentRoots(mn("project", "m1-pom"),
                       "$projectPath/m1", "$projectPath/pom-sources")
    assertContentRootSources(mn("project", "m1-pom"), "$projectPath/m1")
    assertContentRootTestSources(mn("project", "m1-pom"), "$projectPath/m1", "src/test/java")
    assertContentRootSources(mn("project", "m1-pom"), "$projectPath/pom-sources", "")
    assertContentRootTestSources(mn("project", "m1-pom"), "$projectPath/pom-sources")

    // this is not quite correct behavior, since we have both modules (m1-pom and m2-custom) pointing at the same folders
    // (Though, it somehow works in IJ, and it's a rare case anyway).
    // The assertions are only to make sure the behavior is 'stable'. Should be updates once the behavior changes intentionally
    assertSources("m1-custom", "$projectPath/custom-sources")
    assertTestSources("m1-custom", "$projectPath/m1/src/test/java")
    assertDefaultResources("m1-custom")
    assertDefaultTestResources("m1-custom")
  }

  @Test
  fun testDoesNotExcludeGeneratedSourcesUnderTargetDir() = runBlocking {
    createStdProjectFolders()
    createProjectSubDirsWithFile("target/foo",
                                 "target/bar",
                                 "target/generated-sources/baz",
                                 "target/generated-test-sources/bazz")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    assertExcludes("project", "target")
    assertSources("project",
                  "src/main/java",
                  "target/generated-sources/baz")
    assertDefaultResources("project")
    assertTestSources("project",
                      "src/test/java",
                      "target/generated-test-sources/bazz")
    assertDefaultTestResources("project")
  }

  @Test
  fun testDoesNotExcludeSourcesUnderTargetDir() = runBlocking {
    createStdProjectFolders()
    createProjectSubDirs("target/src",
                         "target/test",
                         "target/xxx")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <sourceDirectory>target/src</sourceDirectory>
                      <testSourceDirectory>target/test</testSourceDirectory>
                    </build>
                    """.trimIndent())
    assertModules("project")
    assertExcludes("project", "target")
  }

  @Test
  fun testDoesNotExcludeSourcesUnderTargetDirWithProperties() = runBlocking {
    createProjectSubDirs("target/src", "target/xxx")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <sourceDirectory>${'$'}{project.build.directory}/src</sourceDirectory>
                    </build>
                    """.trimIndent())
    assertModules("project")
    assertSources("project", "target/src")
    assertExcludes("project", "target")
  }

  @Test
  fun testDoesNotExcludeFoldersWithSourcesUnderTargetDir() = runBlocking {
    createStdProjectFolders()
    createProjectSubDirs("target/src/main",
                         "target/foo")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <sourceDirectory>target/src/main</sourceDirectory>
                    </build>
                    """.trimIndent())
    assertModules("project")
    assertExcludes("project", "target")
    assertSources("project", "target/src/main")
    assertDefaultResources("project")
  }

  @Test
  fun testDoesNotUnExcludeFoldersOnRemoval() = runBlocking {
    createStdProjectFolders()
    val subDir = createProjectSubDir("target/foo")
    createProjectSubDirsWithFile("target/generated-sources/baz")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    assertExcludes("project", "target")
    assertSources("project",
                  "src/main/java",
                  "target/generated-sources/baz")
    assertDefaultResources("project")
    edtWriteAction {
      try {
        subDir.delete(this)
      }
      catch (e: IOException) {
        fail("Unable to delete the file: " + e.message)
      }
    }
    importProjectAsync()
    assertExcludes("project", "target")
  }

  @Test
  fun testSourceFoldersOrder() = runBlocking {
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
  fun testUnexcludeNewSources() = runBlocking {
    createProjectSubDirs("target/foo")
    createProjectSubDirs("target/src")
    createProjectSubDirs("target/test/subFolder")
    importProjectAsync("""
                   <groupId>test</groupId>
                   <artifactId>project</artifactId>
                   <version>1</version>
                   """.trimIndent())
    assertExcludes("project", "target")
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <sourceDirectory>target/src</sourceDirectory>
                         <testSourceDirectory>target/test/subFolder</testSourceDirectory>
                       </build>
                       """.trimIndent())
    importProjectAsync()
    //resolveFoldersAndImport();
    assertSources("project", "target/src")
    assertTestSources("project", "target/test/subFolder")
    assertExcludes("project", "target")
  }

  @Test
  fun testUnexcludeNewSourcesUnderCompilerOutputDir() = runBlocking {
    createProjectSubDirs("target/classes/src")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    assertExcludes("project", "target")
    //assertTrue(getCompilerExtension("project").isExcludeOutput());
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <sourceDirectory>target/classes/src</sourceDirectory>
                       </build>
                       """.trimIndent())
    resolveFoldersAndImport()
    assertSources("project", "target/classes/src")
    assertExcludes("project", "target")

    //assertFalse(getCompilerExtension("project").isExcludeOutput());
  }

  @Test
  fun testAnnotationProcessorSources() = runBlocking {
    createStdProjectFolders()
    createProjectSubDirsWithFile("target/generated-sources/foo",
                                 "target/generated-sources/annotations",
                                 "target/generated-test-sources/test-annotations",
                                 "target/generated-test-sources/foo")
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    assertSources("project",
                  "src/main/java",
                  "target/generated-sources/annotations",
                  "target/generated-sources/foo")
    assertDefaultResources("project")
    assertTestSources("project",
                      "src/test/java",
                      "target/generated-test-sources/foo",
                      "target/generated-test-sources/test-annotations")
    assertDefaultTestResources("project")
  }

  @Test
  fun testCustomAnnotationProcessorSources() = runBlocking {
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
  fun testModuleWorkingDirWithMultiplyContentRoots() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>AA</module>
                         <module>BB</module>
                       </modules>
                       """.trimIndent())
    createModulePom("AA", """
      <parent>
              <artifactId>project</artifactId>
              <groupId>test</groupId>
              <version>1</version>
          </parent>
      <artifactId>AA</artifactId>
      """.trimIndent())
    val pomBB = createModulePom("BB", """
      <parent>
              <artifactId>project</artifactId>
              <groupId>test</groupId>
              <version>1</version>
          </parent>
      <artifactId>BB</artifactId>
       <build>
              <testResources>
                  <testResource>
                      <targetPath>${'$'}{project.build.testOutputDirectory}</targetPath>
                      <directory>
                          ${'$'}{project.basedir}/src/test/resources                </directory>
                  </testResource>
                  <testResource>
                      <targetPath>${'$'}{project.build.testOutputDirectory}</targetPath>
                      <directory>
                           ${'$'}{project.basedir}/../AA/src/test/resources                </directory>
                  </testResource>
              </testResources>
          </build>
          """
      .trimIndent()
    )
    createProjectSubDirs("AA/src/test/resources")
    createProjectSubDirs("BB/src/test/resources")
    importProjectAsync()
    val parameters: CommonProgramRunConfigurationParameters = object : CommonProgramRunConfigurationParameters {
      override fun getProject(): Project {
        return project
      }

      override fun setProgramParameters(value: String?) {}
      override fun getProgramParameters(): String? {
        return null
      }

      override fun setWorkingDirectory(value: String?) {}
      override fun getWorkingDirectory(): String? {
        return "\$MODULE_WORKING_DIR$"
      }

      override fun setEnvs(envs: Map<String, String>) {}
      override fun getEnvs(): Map<String, String> {
        return HashMap()
      }

      override fun setPassParentEnvs(passParentEnvs: Boolean) {}
      override fun isPassParentEnvs(): Boolean {
        return false
      }
    }
    assertModules("project", mn("project", "AA"), mn("project", "BB"))
    val workingDir = ProgramParametersUtil.getWorkingDir(parameters, project, getModule(mn("project", "BB")))
    assertEquals(pomBB.canonicalFile!!.getParent().getPath(), workingDir)
  }

  @Test
  fun testExcludeTargetForAggregator() = runBlocking {
    importProjectAsync("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       """.trimIndent())
    assertModules("project")
    assertExcludes("project", "target")
  }

  private suspend fun resolveFoldersAndImport() {
    MavenFolderResolver(projectsManager.project).resolveFoldersAndImport(projectsManager.projects)
  }

  private fun createProjectSubDirsWithFile(vararg dirs: String) {
    for (dir in dirs) {
      createProjectSubFile("$dir/a.txt")
    }
  }
}
