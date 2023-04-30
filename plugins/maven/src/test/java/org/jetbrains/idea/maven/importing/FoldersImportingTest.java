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
package org.jetbrains.idea.maven.importing;

import com.intellij.execution.CommonProgramRunConfigurationParameters;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper;
import org.jetbrains.idea.maven.project.MavenImportingSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.jetbrains.idea.maven.utils.Path;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class FoldersImportingTest extends MavenMultiVersionImportingTestCase {
  @Test
  public void testSimpleProjectStructure() {
    createStdProjectFolders();

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);

    assertModules("project");
    assertContentRoots("project", getProjectPath());

    assertSources("project", "src/main/java");
    assertDefaultResources("project");
    assertTestSources("project", "src/test/java");
    assertDefaultTestResources("project");
  }

  @Test
  public void testInvalidProjectHasContentRoot() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1""");
    importProjectWithErrors();

    assertModules("project");
    assertContentRoots("project", getProjectPath());
  }

  @Test
  public void testDoNotResetFoldersAfterResolveIfProjectIsInvalid() {
    createStdProjectFolders();

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
                       """);
    importProjectWithErrors();

    assertModules("project");
    assertSources("project", "src/main/java");
    assertDefaultResources("project");
    assertTestSources("project", "src/test/java");
    assertDefaultTestResources("project");
  }

  @Test
  public void testDoesNotResetUserFolders() {
    final VirtualFile dir1 = createProjectSubDir("userSourceFolder");
    final VirtualFile dir2 = createProjectSubDir("userExcludedFolder");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);

    ApplicationManager.getApplication().runWriteAction(() -> {
      MavenRootModelAdapter adapter =
        new MavenRootModelAdapter(new MavenRootModelAdapterLegacyImpl(getProjectsTree().findProject(myProjectPom),
                                                                      getModule("project"),
                                                                      ProjectDataManager.getInstance().createModifiableModelsProvider(myProject)));
      adapter.addSourceFolder(dir1.getPath(), JavaSourceRootType.SOURCE);
      adapter.addExcludedFolder(dir2.getPath());
      adapter.getRootModel().commit();
    });


    if (supportsImportOfNonExistingFolders()) {
      assertSources("project", "userSourceFolder", "src/main/java");
    } else {
      assertSources("project", "userSourceFolder");
    }
      assertExcludes("project", "target", "userExcludedFolder");

    importProject();

    if (supportsImportOfNonExistingFolders()) {
      assertSources("project", "src/main/java");
    } else {
      assertSources("project", "userSourceFolder");
    }
    assertExcludes("project", "target", "userExcludedFolder");

    resolveFoldersAndImport();

    if (supportsImportOfNonExistingFolders()) {
      assertSources("project", "src/main/java");
    } else {
      assertSources("project", "userSourceFolder");
    }
    assertExcludes("project", "target", "userExcludedFolder");
  }

  @Test
  public void testClearParentAndSubFoldersOfNewlyImportedFolders() {
    createProjectSubDirs("src/main/java", "src/main/resources");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);

    assertSources("project", "src/main/java");
    assertDefaultResources("project");

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <sourceDirectory>src</sourceDirectory>
                       </build>
                       """);
    resolveFoldersAndImport();

    assertSources("project", "src");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);

    assertSources("project", "src/main/java");
    assertDefaultResources("project");
  }

  @Test
  public void testSourceFoldersOnReimport() {
    createProjectSubDirs("src1", "src2");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <sourceDirectory>src1</sourceDirectory>
                    </build>
                    """);

    assertSources("project", "src1");

    getMavenImporterSettings().setKeepSourceFolders(false);
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <sourceDirectory>src2</sourceDirectory>
                       </build>
                       """);
    resolveFoldersAndImport();

    assertSources("project", "src2");

    getMavenImporterSettings().setKeepSourceFolders(true);
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <sourceDirectory>src1</sourceDirectory>
                       </build>
                       """);
    resolveFoldersAndImport();

    if (supportsLegacyKeepingFoldersFromPreviousImport()) {
      assertSources("project", "src2", "src1");
    }
    else {
      assertSources("project", "src1");
    }
  }

  @Test
  public void testCustomSourceFolders() {
    createStdProjectFolders();
    createProjectSubDirs("src", "test", "res1", "res2", "testRes1", "testRes2");

    importProject("""
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
                    """);

    assertModules("project");
    assertContentRoots("project", getProjectPath());

    assertSources("project", "src");
    assertResources("project", "res1", "res2");
    assertTestSources("project", "test");
    assertTestResources("project", "testRes1", "testRes2");
  }

  @Test
  public void testCustomSourceFoldersOutsideOfContentRoot() {
    createStdProjectFolders();
    createProjectSubDirs("m",
                         "src",
                         "test",
                         "res",
                         "testRes");

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m</module>
                       </modules>
                       """);

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
      """);
    importProject();
    assertModules("project", "m");

    assertContentRoots("project",
                       getProjectPath());

    assertContentRoots("m",
                       getProjectPath() + "/m",
                       getProjectPath() + "/src",
                       getProjectPath() + "/test",
                       getProjectPath() + "/res",
                       getProjectPath() + "/testRes");
  }

  @Test
  public void testSourceFolderPointsToProjectRoot() {
    createStdProjectFolders();

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <sourceDirectory>${basedir}</sourceDirectory>
                    </build>
                    """);

    assertModules("project");
    assertContentRoots("project", getProjectPath());

    assertSources("project", "");
    assertTestSources("project");
    assertResources("project");
    assertTestResources("project");
  }

  @Test
  public void testResourceFolderPointsToProjectRoot() {
    createStdProjectFolders();

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource><directory>${basedir}</directory></resource>
                      </resources>
                    </build>
                    """);

    assertModules("project");
    assertContentRoots("project", getProjectPath());

    assertSources("project", "src/main/java");
    assertTestSources("project", "src/test/java");
    assertResources("project");
    assertDefaultTestResources("project");
  }

  @Test
  public void testResourceFolderPointsToProjectRootParent() {
    createStdProjectFolders();

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <resources>
                        <resource><directory>${basedir}/..</directory></resource>
                      </resources>
                    </build>
                    """);

    assertModules("project");
    assertContentRoots("project", getProjectPath());

    assertSources("project", "src/main/java");
    assertTestSources("project", "src/test/java");
    assertResources("project");
    assertDefaultTestResources("project");
  }

  @Test
  public void testSourceFolderPointsToProjectRootParent() {
    createStdProjectFolders();

    importProject("""
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
                    """);

    assertModules("project");
    assertContentRoots("project", getProjectPath());

    assertSources("project", "src/main/java");
    assertTestSources("project", "src/test/java");
    assertDefaultResources("project");
    assertDefaultTestResources("project");
  }

  @Test
  public void testPluginSources() {
    createStdProjectFolders();
    createProjectSubDirs("src1", "src2");

    importProject("""
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
                    """);
    resolveFoldersAndImport();
    assertModules("project");

    assertSources("project", "src/main/java", "src1", "src2");
    assertDefaultResources("project");
  }

  @Test
  public void testPluginSourceDuringGenerateResourcesPhase() {
    createStdProjectFolders();
    createProjectSubDirs("extraResources");

    importProject("""
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
                    """);
    resolveFoldersAndImport();
    assertModules("project");

    assertSources("project", "extraResources", "src/main/java");
    assertDefaultResources("project");
  }

  @Test
  public void testPluginTestSourcesDuringGenerateTestResourcesPhase() {
    createStdProjectFolders();
    createProjectSubDirs("extraTestResources");

    getMavenImporterSettings().setUpdateFoldersOnImportPhase("generate-test-resources");

    importProject("""
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
                    """);
    resolveFoldersAndImport();
    assertModules("project");

    assertTestSources("project", "extraTestResources", "src/test/java");
    assertDefaultTestResources("project");
  }

  @Test
  public void testPluginSourcesWithRelativePath() {
    createStdProjectFolders();
    createProjectSubDirs("relativePath");

    importProject("""
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
                    """);
    resolveFoldersAndImport();
    assertModules("project");

    assertSources("project", "relativePath", "src/main/java");
    assertDefaultResources("project");
  }

  @Test
  public void testPluginSourcesWithVariables() {
    createStdProjectFolders();
    createProjectSubDirs("target/src");

    importProject("""
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
                    """);
    resolveFoldersAndImport();
    assertModules("project");

    assertSources("project", "src/main/java", "target/src");
    assertDefaultResources("project");
  }

  @Test
  public void testPluginSourcesWithIntermoduleDependency() {
    createProjectSubDirs("m1/src/main/java",
                         "m1/src/main/resources",
                         "m1/src/foo");

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """);

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
                      """);

    createModulePom("m2",
                    """
                      <groupId>test</groupId>
                      <artifactId>m2</artifactId>
                      <version>1</version>
                      """);
    importProject();
    assertModules("project", "m1", "m2");

    resolveFoldersAndImport();
    assertSources("m1", "src/foo", "src/main/java");
    assertDefaultResources("m1");
  }

  @Test
  public void testPluginExtraFilesInMultipleExecutions() {
    createStdProjectFolders();
    createProjectSubDirs("src1", "src2");
    createProjectSubDirs("resources1", "resources2");
    createProjectSubDirs("test1", "test2");
    createProjectSubDirs("test-resources1", "test-resources2");

    importProject("""
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
                    """);
    resolveFoldersAndImport();
    assertModules("project");

    assertSources("project", "src/main/java", "src1", "src2");
    assertDefaultResources("project", "resources1", "resources2");
    assertTestSources("project", "src/test/java", "test1", "test2");
    assertDefaultTestResources("project", "test-resources1", "test-resources2");
  }

  @Test
  public void testDownloadingNecessaryPlugins() throws Exception {
    try {
      MavenCustomRepositoryHelper helper = new MavenCustomRepositoryHelper(myDir, "local1");
      setRepositoryPath(helper.getTestDataPath("local1"));

      File pluginFile = new File(getRepositoryPath(),
                                 "org/codehaus/mojo/build-helper-maven-plugin/1.2/build-helper-maven-plugin-1.2.jar");
      assertFalse(pluginFile.exists());

      importProject("""
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
                      """);
      resolveDependenciesAndImport();
      resolveFoldersAndImport();

      assertTrue(pluginFile.exists());
    }
    finally {
      // do not lock files by maven process
      MavenServerManager.getInstance().shutdown(true);
    }
  }

  @Test
  public void testAddingExistingGeneratedSources() throws Exception {
    createStdProjectFolders();

    createProjectSubFile("target/generated-sources/src1/com/A.java", "package com; class A {}");
    createProjectSubFile("target/generated-sources/src2/com/B.java", "package com; class B {}");
    createProjectSubFile("target/generated-test-sources/test1/com/test/A.java", "package com.test; class A {}");
    createProjectSubFile("target/generated-test-sources/test2/com/test/B.java", "package com.test; class B {}");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);

    assertSources("project",
                  "src/main/java",
                  "target/generated-sources/src1",
                  "target/generated-sources/src2");
    assertDefaultResources("project");

    assertTestSources("project",
                      "src/test/java",
                      "target/generated-test-sources/test1",
                      "target/generated-test-sources/test2");
    assertDefaultTestResources("project");
  }

  @Test
  public void testAddingExistingGeneratedSourcesInPerSourceTypeModules() throws Exception {
    Assume.assumeTrue(isWorkspaceImport());

    createStdProjectFolders();

    createProjectSubFile("target/generated-sources/src1/com/A.java", "package com; class A {}");
    createProjectSubFile("target/generated-sources/src2/com/B.java", "package com; class B {}");
    createProjectSubFile("target/generated-test-sources/test1/com/test/A.java", "package com.test; class A {}");
    createProjectSubFile("target/generated-test-sources/test2/com/test/B.java", "package com.test; class B {}");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                      <maven.compiler.source>8</maven.compiler.source>
                      <maven.compiler.target>8</maven.compiler.target>
                      <maven.compiler.testSource>11</maven.compiler.testSource>
                      <maven.compiler.testTarget>11</maven.compiler.testTarget>
                    </properties>
                    """);

    assertModules("project", "project.main", "project.test");

    assertContentRoots("project", getProjectPath());
    assertSources("project");
    assertResources("project");
    assertTestSources("project");
    assertTestResources("project");
    assertExcludes("project", "target");

    String mainResources = getProjectPath() + "/src/main/resources";
    var mainSources = new String[]{
      getProjectPath() + "/src/main/java",
      mainResources,
      getProjectPath() + "/target/generated-sources/src1",
      getProjectPath() + "/target/generated-sources/src2"
    };
    String testResources = getProjectPath() + "/src/test/resources";
    var testSources = new String[]{
      getProjectPath() + "/src/test/java",
      testResources,
      getProjectPath() + "/target/generated-test-sources/test1",
      getProjectPath() + "/target/generated-test-sources/test2"
    };

    assertContentRoots("project.main", mainSources);
    assertContentRoots("project.test", testSources);

    for (String main : mainSources) {
      if (main.equals(mainResources)) {
        assertContentRootResources("project.main", main, "");
      }
      else {
        assertContentRootSources("project.main", main, "");
      }
      assertContentRootTestSources("project.main", main);
      assertContentRootTestResources("project.main", main);
      assertContentRootExcludes("project.main", main);
    }

    for (String test : testSources) {
      assertContentRootResources("project.test", test);
      assertContentRootSources("project.test", test);

      if (test.equals(testResources)) {
        assertContentRootTestResources("project.test", test, "");
      }
      else {
        assertContentRootTestSources("project.test", test, "");
      }
      assertContentRootExcludes("project.test", test);
    }
  }

  @Test
  public void testContentRootOutsideOfModuleDirInPerSourceTypeImport() throws Exception {
    Assume.assumeTrue(isWorkspaceImport());

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
                      """);

    createProjectSubFile("custom-sources/com/CustomSource.java", "package com; class CustomSource {}");
    createProjectSubFile("m1/src/main/resources/test.txt", "resource");

    importProject("""
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
                    """);

    assertModules("project",
                  mn("project", "m1"),
                  mn("project", "m1.main"),
                  mn("project", "m1.test"));

    var expectedRoots = new ArrayList<String>();
    expectedRoots.add(getProjectPath() + "/custom-sources");

    expectedRoots.add(getProjectPath() + "/m1/src/main/resources");
    if (isMaven4()) {
      expectedRoots.add(getProjectPath() + "/m1/src/main/resources-filtered");
    }

    assertContentRoots(mn("project", "m1.main"), ArrayUtil.toStringArray(expectedRoots));
  }

  @Test
  public void testAddingExistingGeneratedSources2() throws Exception {
    createStdProjectFolders();

    createProjectSubFile("target/generated-sources/com/A.java", "package com; class A {}");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);

    assertSources("project",
                  "src/main/java",
                  "target/generated-sources");
    assertDefaultResources("project");
  }

  @Test
  public void testAddingExistingGeneratedSources3() throws Exception {
    createStdProjectFolders();

    MavenProjectsManager.getInstance(myProject).getImportingSettings().setGeneratedSourcesFolder(
      MavenImportingSettings.GeneratedSourcesFolder.SUBFOLDER);

    createProjectSubFile("target/generated-sources/com/A.java", "package com; class A {}");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);

    assertSources("project",
                  "src/main/java",
                  "target/generated-sources/com");
    assertDefaultResources("project");
  }

  @Test
  public void testOverrideAnnotationSources() throws Exception {
    createStdProjectFolders();

    MavenProjectsManager.getInstance(myProject).getImportingSettings().setGeneratedSourcesFolder(
      MavenImportingSettings.GeneratedSourcesFolder.GENERATED_SOURCE_FOLDER);

    createProjectSubFile("target/generated-sources/com/A.java", "package com; class A {}");
    createProjectSubFile("target/generated-sources/annotations/com/B.java", "package com; class B {}");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);

    assertSources("project",
                  "src/main/java",
                  "target/generated-sources");
    assertDefaultResources("project");
  }

  @Test
  public void testOverrideAnnotationSourcesWhenAutodetect() throws Exception {
    createStdProjectFolders();

    MavenProjectsManager.getInstance(myProject).getImportingSettings().setGeneratedSourcesFolder(
      MavenImportingSettings.GeneratedSourcesFolder.AUTODETECT);

    createProjectSubFile("target/generated-sources/com/A.java", "package com; class A {}");
    createProjectSubFile("target/generated-sources/annotations/com/B.java", "package com; class B {}");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);

    assertSources("project",
                  "src/main/java",
                  "target/generated-sources");
    assertDefaultResources("project");
  }


  @Test
  public void testOverrideTestAnnotationSourcesWhenAutodetect() throws Exception {
    createStdProjectFolders();

    MavenProjectsManager.getInstance(myProject).getImportingSettings().setGeneratedSourcesFolder(
      MavenImportingSettings.GeneratedSourcesFolder.AUTODETECT);

    createProjectSubFile("target/generated-test-sources/com/A.java", "package com; class A {}");
    createProjectSubFile("target/generated-test-sources/test-annotations/com/B.java", "package com; class B {}");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);

    assertSources("project", "src/main/java");
    assertTestSources("project",
                      "src/test/java",
                      "target/generated-test-sources");
    assertDefaultResources("project");
  }

  @Test
  public void testIgnoreGeneratedSources() throws Exception {
    createStdProjectFolders();

    MavenProjectsManager.getInstance(myProject).getImportingSettings().setGeneratedSourcesFolder(
      MavenImportingSettings.GeneratedSourcesFolder.IGNORE);

    createProjectSubFile("target/generated-sources/annotations/A.java", "package com; class A {}");
    createProjectSubFile("target/generated-sources/annotations/com/B.java", "package com; class B {}");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);

    assertSources("project", "src/main/java");
    assertDefaultResources("project");
  }


  @Test
  public void testAddingExistingGeneratedSources4() throws Exception {
    createStdProjectFolders();

    createProjectSubFile("target/generated-sources/A1/B1/com/A1.java", "package com; class A1 {}");
    createProjectSubFile("target/generated-sources/A1/B2/com/A2.java", "package com; class A2 {}");
    createProjectSubFile("target/generated-sources/A2/com/A3.java", "package com; class A3 {}");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);

    assertSources("project",
                  "src/main/java",
                  "target/generated-sources/A1/B1",
                  "target/generated-sources/A1/B2",
                  "target/generated-sources/A2");
    assertDefaultResources("project");
  }

  @Test
  public void testAddingExistingGeneratedSources5() throws Exception {
    createStdProjectFolders();

    createProjectSubFile("target/generated-sources/A1/B1/com/A1.java", "package com; class A1 {}");
    createProjectSubFile("target/generated-sources/A2.java", "class A2 {}");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);

    assertSources("project",
                  "src/main/java",
                  "target/generated-sources");
    assertDefaultResources("project");
  }


  @Test
  public void testAddingExistingGeneratedSourcesWithCustomTargetDir() throws Exception {
    createStdProjectFolders();
    createProjectSubDirsWithFile("targetCustom/generated-sources/src",
                                 "targetCustom/generated-test-sources/test");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <directory>targetCustom</directory>
                    </build>
                    """);

    assertSources("project",
                  "src/main/java",
                  "targetCustom/generated-sources/src");
    assertDefaultResources("project");

    assertTestSources("project",
                      "src/test/java",
                      "targetCustom/generated-test-sources/test");
    assertDefaultTestResources("project");
  }

  @Test
  public void testDoesNotAddAlreadyRegisteredSourcesUnderGeneratedDir() {
    createStdProjectFolders();
    createProjectSubDirs("target/generated-sources/main/src",
                         "target/generated-test-sources/test/src");

    importProject("""
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
                    """);
    resolveFoldersAndImport();

    assertSources("project",
                  "src/main/java",
                  "target/generated-sources/main/src");
    assertDefaultResources("project");

    assertTestSources("project",
                      "src/test/java",
                      "target/generated-test-sources/test/src");
    assertDefaultTestResources("project");
  }

  @Test
  public void testIgnoringFilesRightUnderGeneratedSources() throws Exception {
    if (!supportsImportOfNonExistingFolders()) {
      createStdProjectFolders();
    }
    createProjectSubFile("target/generated-sources/f.txt");
    createProjectSubFile("target/generated-test-sources/f.txt");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);

    assertSources("project", "src/main/java");
    assertDefaultResources("project");
    assertTestSources("project", "src/test/java");
    assertDefaultTestResources("project");

    assertExcludes("project", "target");
  }

  @Test
  public void testExcludingOutputDirectories() {
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);
    assertModules("project");

    assertExcludes("project", "target");
    assertModuleOutput("project",
                       getProjectPath() + "/target/classes",
                       getProjectPath() + "/target/test-classes");
  }

  @Test
  public void testExcludingOutputDirectoriesIfProjectOutputIsUsed() {
    getMavenImporterSettings().setUseMavenOutput(false);

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <directory>foo</directory>
                    </build>
                    """);
    assertModules("project");

    assertExcludes("project", "foo");
    assertProjectOutput("project");
  }

  @Test
  public void testUnloadedModules() {
    Assume.assumeTrue(isWorkspaceImport());
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

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
    assertModules("project", "m1", "m2");

    ModuleManager.getInstance(myProject).setUnloadedModulesSync(List.of("m2"));
    assertModules("project", "m1");

    importProject();
    assertModules("project", "m1");
    UnloadedModuleDescription m2 = ModuleManager.getInstance(myProject).getUnloadedModuleDescription("m2");
    assertNotNull(m2);
    assertEquals("m2", m2.getName());
  }

  @Test
  public void testExcludingCustomOutputDirectories() {
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <directory>targetCustom</directory>
                      <outputDirectory>outputCustom</outputDirectory>
                      <testOutputDirectory>testCustom</testOutputDirectory>
                    </build>
                    """);

    assertModules("project");

    assertExcludes("project",
                   "outputCustom",
                   "targetCustom",
                   "testCustom");
    assertModuleOutput("project",
                       getProjectPath() + "/outputCustom",
                       getProjectPath() + "/testCustom");
  }

  @Test
  public void testExcludingCustomOutputUnderTargetUsingStandardVariable() {
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <outputDirectory>${project.build.directory}/outputCustom</outputDirectory>
                      <testOutputDirectory>${project.build.directory}/testCustom</testOutputDirectory>
                    </build>
                    """);

    assertModules("project");

    assertExcludes("project", "target");
    assertModuleOutput("project",
                       getProjectPath() + "/target/outputCustom",
                       getProjectPath() + "/target/testCustom");
  }

  @Test
  public void testDoNotExcludeExcludeOutputDirectoryWhenItPointstoRoot() {
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <outputDirectory>.</outputDirectory>
                      <testOutputDirectory>.</testOutputDirectory>
                    </build>
                    """);

    assertModules("project");

    assertExcludes("project",
                   "target");
    assertModuleOutput("project",
                       getProjectPath(),
                       getProjectPath());
  }

  @Test
  public void testOutputDirsOutsideOfContentRoot() {
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <directory>../target</directory>
                      <outputDirectory>../target/classes</outputDirectory>
                      <testOutputDirectory>../target/test-classes</testOutputDirectory>
                    </build>
                    """);

    String targetPath = getParentPath() + "/target";
    String targetUrl = new Path(targetPath).toUrl().getUrl();

    assertContentRoots("project", getProjectPath());
    assertModuleOutput("project",
                       getParentPath() + "/target/classes",
                       getParentPath() + "/target/test-classes");
  }

  @Test
  public void testCustomPomFileNameDefaultContentRoots() throws Exception {
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
        """));

    new File(myProjectRoot.getPath(), "m1/sources").mkdirs();
    new File(myProjectRoot.getPath(), "m1/tests").mkdirs();

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <packaging>pom</packaging>
                    <version>1</version>
                    <modules>
                      <module>m1/customName.xml</module>
                    </modules>
                    """);

    assertContentRoots(mn("project", "m1"), getProjectPath() + "/m1");
  }

  @Test
  public void testCustomPomFileNameCustomContentRoots() throws Exception {
    createProjectSubFile("m1/pom.xml", createPomXml(
      """
        <artifactId>m1-pom</artifactId>
        <version>1</version>
        <parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
        </parent>
        """));

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
        """));

    createProjectSubDirs("m1/src/main/java",
                         "m1/src/main/resources",
                         "m1/src/test/java",
                         "m1/src/test/resources",

                         "m1/sources/resources",
                         "m1/tests");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <packaging>pom</packaging>
                    <version>1</version>
                    <modules>
                      <module>m1</module>
                      <module>m1/custom.xml</module>
                    </modules>
                    """);

    String m1_pom_module = mn("project", "m1-pom");
    String m1_custom_module = mn("project", "m1-custom");
    assertModules("project", m1_pom_module, m1_custom_module);

    String m1_pom_root = getProjectPath() + "/m1";
    assertContentRoots(m1_pom_module, m1_pom_root);
    assertContentRootSources(m1_pom_module, m1_pom_root, "src/main/java");
    var expectedResources = new ArrayList<String>();
    expectedResources.add("src/main/resources");
    if (isMaven4()) {
      expectedResources.add("src/main/resources-filtered");
    }
    assertContentRootResources(m1_pom_module, m1_pom_root, ArrayUtil.toStringArray(expectedResources));
    assertContentRootTestSources(m1_pom_module, m1_pom_root, "src/test/java");
    var expectedTestResources = new ArrayList<String>();
    expectedTestResources.add("src/test/resources");
    if (isMaven4()) {
      expectedTestResources.add("src/test/resources-filtered");
    }
    assertContentRootTestResources(m1_pom_module, m1_pom_root, ArrayUtil.toStringArray(expectedTestResources));

    String m1_custom_sources_root = getProjectPath() + "/m1/sources";
    String m1_custom_tests_root = getProjectPath() + "/m1/tests";
    String m1_standard_test_resources = getProjectPath() + "/m1/src/test/resources";

    var m1_content_roots = new ArrayList<String>();
    m1_content_roots.add(m1_custom_sources_root);
    m1_content_roots.add(m1_custom_tests_root);

    // [anton] The next folder doesn't look correct, as it intersects with 'pom.xml' module folders,
    // but I'm testing the behavior as is in order to preserve it in the new Workspace import
    m1_content_roots.add(m1_standard_test_resources);
    if (isMaven4()) {
      m1_content_roots.add(m1_standard_test_resources + "-filtered");
    }

    assertContentRoots(m1_custom_module, ArrayUtil.toStringArray(m1_content_roots));
    assertContentRootSources(m1_custom_module, m1_custom_sources_root, "");
    assertContentRootResources(m1_custom_module, m1_custom_sources_root);
    assertContentRootTestSources(m1_custom_module, m1_custom_tests_root, "");
    assertContentRootTestResources(m1_custom_module, m1_standard_test_resources, "");
  }

  @Test
  public void testContentRootOutsideOfModuleDir() throws Exception {
    if (!supportsImportOfNonExistingFolders()) {
      createStdProjectFolders("m1");
    }

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
        """));

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
        """));

    new File(myProjectRoot.getPath(), "pom-sources").mkdirs();
    new File(myProjectRoot.getPath(), "custom-sources").mkdirs();

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <packaging>pom</packaging>
                    <version>1</version>
                    <modules>
                      <module>m1</module>
                      <module>m1/custom.xml</module>
                    </modules>
                    """);

    assertModules("project", mn("project", "m1-pom"), mn("project", "m1-custom"));

    assertContentRoots(mn("project", "m1-pom"),
                       getProjectPath() + "/m1", getProjectPath() + "/pom-sources");

    assertContentRootSources(mn("project", "m1-pom"), getProjectPath() + "/m1");
    assertContentRootTestSources(mn("project", "m1-pom"), getProjectPath() + "/m1", "src/test/java");
    assertContentRootSources(mn("project", "m1-pom"), getProjectPath() + "/pom-sources", "");
    assertContentRootTestSources(mn("project", "m1-pom"), getProjectPath() + "/pom-sources");

    assertContentRoots(mn("project", "m1-custom"),
                       getProjectPath() + "/custom-sources",
                       getProjectPath() + "/m1/src/main/resources",
                       getProjectPath() + "/m1/src/test/java",
                       getProjectPath() + "/m1/src/test/resources");

    // this is not quite correct behavior, since we have both modules (m1-pom and m2-custom) pointing at the same folders
    // (Though, it somehow works in IJ, and it's a rare case anyway).
    // The assertions are only to make sure the behavior is 'stable'. Should be updates once the behavior changes intentionally
    assertContentRootSources(mn("project", "m1-custom"), getProjectPath() + "/custom-sources", "");
    assertContentRootTestSources(mn("project", "m1-custom"), getProjectPath() + "/m1/src/test/java", "");
    assertContentRootResources(mn("project", "m1-custom"), getProjectPath() + "/m1/src/main/resources", "");
    assertContentRootTestResources(mn("project", "m1-custom"), getProjectPath() + "/m1/src/test/resources", "");
  }

  @Test
  public void testDoesNotExcludeGeneratedSourcesUnderTargetDir() throws Exception {
    createStdProjectFolders();
    createProjectSubDirsWithFile("target/foo",
                                 "target/bar",
                                 "target/generated-sources/baz",
                                 "target/generated-test-sources/bazz");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);

    assertExcludes("project", "target");

    assertSources("project",
                  "src/main/java",
                  "target/generated-sources/baz");
    assertDefaultResources("project");

    assertTestSources("project",
                      "src/test/java",
                      "target/generated-test-sources/bazz");
    assertDefaultTestResources("project");
  }

  @Test
  public void testDoesNotExcludeSourcesUnderTargetDir() {
    createStdProjectFolders();
    createProjectSubDirs("target/src",
                         "target/test",
                         "target/xxx");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <sourceDirectory>target/src</sourceDirectory>
                      <testSourceDirectory>target/test</testSourceDirectory>
                    </build>
                    """);

    assertModules("project");

    assertExcludes("project", "target");
  }

  @Test
  public void testDoesNotExcludeSourcesUnderTargetDirWithProperties() {
    createProjectSubDirs("target/src", "target/xxx");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <sourceDirectory>${project.build.directory}/src</sourceDirectory>
                    </build>
                    """);

    assertModules("project");

    assertSources("project", "target/src");
    assertExcludes("project", "target");
  }

  @Test
  public void testDoesNotExcludeFoldersWithSourcesUnderTargetDir() {
    createStdProjectFolders();
    createProjectSubDirs("target/src/main",
                         "target/foo");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <sourceDirectory>target/src/main</sourceDirectory>
                    </build>
                    """);

    assertModules("project");

    assertExcludes("project", "target");

    assertSources("project", "target/src/main");
    assertDefaultResources("project");
  }

  @Test
  public void testDoesNotUnExcludeFoldersOnRemoval() throws Exception {
    createStdProjectFolders();

    final VirtualFile subDir = createProjectSubDir("target/foo");
    createProjectSubDirsWithFile("target/generated-sources/baz");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);

    assertExcludes("project", "target");
    assertSources("project",
                  "src/main/java",
                  "target/generated-sources/baz");
    assertDefaultResources("project");

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        try {
          subDir.delete(this);
        }
        catch (IOException e) {
          fail("Unable to delete the file: " + e.getMessage());
        }
      }
    });

    importProject();
    assertExcludes("project", "target");
  }

  @Test
  public void testSourceFoldersOrder() throws Exception {
    createStdProjectFolders();

    final VirtualFile target = createProjectSubDir("target");
    createProjectSubDirsWithFile("anno",
                                 "test-anno",
                                 "target/generated-sources/foo",
                                 "target/generated-sources/annotations",
                                 "target/generated-sources/test-annotations",
                                 "target/generated-test-sources/foo");

    importProject("""
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
                    """);

    final Consumer<Boolean> testAssertions = (shouldKeepGeneratedFolders) -> {
      if (shouldKeepGeneratedFolders) {
        assertSources("project",
                      "anno",
                      "src/main/java",
                      "target/generated-sources/annotations",
                      "target/generated-sources/foo",
                      "target/generated-sources/test-annotations");
      }
      else {
        assertSources("project",
                      "anno",
                      "src/main/java");
      }

      assertDefaultResources("project");
      if (shouldKeepGeneratedFolders) {
        assertTestSources("project",
                          "src/test/java",
                          "target/generated-test-sources/foo",
                          "test-anno");
      }
      else {
        assertTestSources("project",
                          "src/test/java",
                          "test-anno");
      }
      assertDefaultTestResources("project");
    };

    testAssertions.accept(true);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        try {
          target.delete(this);
        }
        catch (IOException e) {
          fail("Unable to delete the file: " + e.getMessage());
        }
      }
    });

    testAssertions.accept(true);
    importProject();
    testAssertions.accept(supportsLegacyKeepingFoldersFromPreviousImport());
    resolveFoldersAndImport();
    testAssertions.accept(supportsLegacyKeepingFoldersFromPreviousImport());
  }

  @Test
  public void testUnexcludeNewSources() {
    createProjectSubDirs("target/foo");
    createProjectSubDirs("target/src");
    createProjectSubDirs("target/test/subFolder");

   importProject("""
                   <groupId>test</groupId>
                   <artifactId>project</artifactId>
                   <version>1</version>
                   """);

    assertExcludes("project", "target");

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <sourceDirectory>target/src</sourceDirectory>
                         <testSourceDirectory>target/test/subFolder</testSourceDirectory>
                       </build>
                       """);
    importProject();
    //resolveFoldersAndImport();

    assertSources("project", "target/src");
    assertTestSources("project", "target/test/subFolder");
    assertExcludes("project", "target");
  }

  @Test
  public void testUnexcludeNewSourcesUnderCompilerOutputDir() {
    createProjectSubDirs("target/classes/src");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);

    assertExcludes("project", "target");
    //assertTrue(getCompilerExtension("project").isExcludeOutput());

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <build>
                         <sourceDirectory>target/classes/src</sourceDirectory>
                       </build>
                       """);
    resolveFoldersAndImport();

    assertSources("project", "target/classes/src");
    assertExcludes("project", "target");

    //assertFalse(getCompilerExtension("project").isExcludeOutput());
  }

  @Test
  public void testAnnotationProcessorSources() throws Exception {
    createStdProjectFolders();
    createProjectSubDirsWithFile("target/generated-sources/foo",
                                 "target/generated-sources/annotations",
                                 "target/generated-test-sources/test-annotations",
                                 "target/generated-test-sources/foo");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);

    assertSources("project",
                  "src/main/java",
                  "target/generated-sources/annotations",
                  "target/generated-sources/foo");
    assertDefaultResources("project");

    assertTestSources("project",
                      "src/test/java",
                      "target/generated-test-sources/foo",
                      "target/generated-test-sources/test-annotations");
    assertDefaultTestResources("project");
  }

  @Test
  public void testCustomAnnotationProcessorSources() throws Exception {
    createStdProjectFolders();
    createProjectSubDirsWithFile("custom-annotations",
                                 "custom-test-annotations",
                                 "target/generated-sources/foo",
                                 "target/generated-sources/annotations",
                                 "target/generated-sources/test-annotations",
                                 "target/generated-test-sources/foo");

    importProject("""
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
                    """);

    assertSources("project",
                  "custom-annotations",
                  "src/main/java",
                  "target/generated-sources/annotations",
                  "target/generated-sources/foo",
                  "target/generated-sources/test-annotations");
    assertTestSources("project",
                      "src/test/java",
                      "target/generated-test-sources/foo",
                      "custom-test-annotations");
  }

  @Test
  public void testCustomAnnotationProcessorSourcesUnderMainGeneratedFolder() throws Exception {
    if (!supportsImportOfNonExistingFolders()) {
      createStdProjectFolders();
    }
    createProjectSubDirsWithFile("target/generated-sources/foo",
                                 "target/generated-sources/annotations",
                                 "target/generated-sources/custom-annotations",      // this and...
                                 "target/generated-sources/custom-test-annotations", // this, are explicitly specified as annotation folders
                                 "target/generated-test-sources/foo",
                                 "target/generated-test-sources/test-annotations"
    );

    importProject("""
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
                    """);

    assertSources("project",
                  "src/main/java",
                  "target/generated-sources/foo",
                  "target/generated-sources/annotations",
                  "target/generated-sources/custom-annotations");
    assertTestSources("project",
                      "src/test/java",
                      "target/generated-sources/custom-test-annotations",
                      "target/generated-test-sources/foo",
                      "target/generated-test-sources/test-annotations");
  }

  @Test
  public void testModuleWorkingDirWithMultiplyContentRoots() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>AA</module>
                         <module>BB</module>
                       </modules>
                       """);
    createModulePom("AA", """
      <parent>
              <artifactId>project</artifactId>
              <groupId>test</groupId>
              <version>1</version>
          </parent>
      <artifactId>AA</artifactId>
      """);

    VirtualFile pomBB = createModulePom("BB", """
      <parent>
              <artifactId>project</artifactId>
              <groupId>test</groupId>
              <version>1</version>
          </parent>
      <artifactId>BB</artifactId>
       <build>
              <testResources>
                  <testResource>
                      <targetPath>${project.build.testOutputDirectory}</targetPath>
                      <directory>
                          ${project.basedir}/src/test/resources                </directory>
                  </testResource>
                  <testResource>
                      <targetPath>${project.build.testOutputDirectory}</targetPath>
                      <directory>
                           ${project.basedir}/../AA/src/test/resources                </directory>
                  </testResource>
              </testResources>
          </build>"""
    );
    createProjectSubDirs("AA/src/test/resources");
    createProjectSubDirs("BB/src/test/resources");
    importProject();
    CommonProgramRunConfigurationParameters parameters = new CommonProgramRunConfigurationParameters() {
      @Override
      public Project getProject() {
        return myProject;
      }

      @Override
      public void setProgramParameters(@Nullable String value) {

      }

      @Override
      public @Nullable String getProgramParameters() {
        return null;
      }

      @Override
      public void setWorkingDirectory(@Nullable String value) {
      }

      @Override
      public @Nullable String getWorkingDirectory() {
        return "$MODULE_WORKING_DIR$";
      }

      @Override
      public void setEnvs(@NotNull Map<String, String> envs) {

      }

      @Override
      public @NotNull Map<String, String> getEnvs() {
        return new HashMap<>();
      }

      @Override
      public void setPassParentEnvs(boolean passParentEnvs) {

      }

      @Override
      public boolean isPassParentEnvs() {
        return false;
      }
    };
    assertModules("project", mn("project", "AA"), mn("project", "BB"));
    String workingDir = ProgramParametersUtil.getWorkingDir(parameters, myProject, getModule(mn("project", "BB")));
    assertEquals(pomBB.getCanonicalFile().getParent().getPath(), workingDir);
  }

  private void createProjectSubDirsWithFile(String... dirs) throws IOException {
    for (String dir : dirs) {
      createProjectSubFile(dir + "/a.txt");
    }
  }
}
