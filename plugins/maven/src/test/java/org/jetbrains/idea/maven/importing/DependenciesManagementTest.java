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

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Test;

import java.io.File;

public class DependenciesManagementTest extends MavenMultiVersionImportingTestCase {

  @Test
  public void testImportingDependencies() throws Exception {
    if (!hasMavenInstallation()) return;

    setRepositoryPath(new File(myDir, "/repo").getPath());
    updateSettingsXml("<localRepository>\n" + getRepositoryPath() + "</localRepository>");

    createModulePom("__temp",
                    """
                      <groupId>test</groupId>
                      <artifactId>bom</artifactId>
                      <packaging>pom</packaging>
                      <version>1</version>
                      <dependencyManagement>
                        <dependencies>
                          <dependency>
                            <groupId>junit</groupId>
                            <artifactId>junit</artifactId>
                            <version>4.0</version>
                          </dependency>
                        </dependencies>
                      </dependencyManagement>
                      """);

    executeGoal("__temp", "install");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencyManagement>
                      <dependencies>
                        <dependency>
                          <groupId>test</groupId>
                          <artifactId>bom</artifactId>
                          <version>1</version>
                          <type>pom</type>
                          <scope>import</scope>
                        </dependency>
                      </dependencies>
                    </dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                      </dependency>
                    </dependencies>
                    """);

    assertModuleLibDeps("project", "Maven: junit:junit:4.0");
  }

  @Test
  public void testImportingNotInstalledDependencies() throws Exception {
    if (ignore()) return;

    setRepositoryPath(new File(myDir, "/repo").getPath());
    updateSettingsXml("<localRepository>\n" + getRepositoryPath() + "</localRepository>");

    VirtualFile bom = createModulePom("bom",
                                      """
                                        <groupId>test</groupId>
                                        <artifactId>bom</artifactId>
                                        <packaging>pom</packaging>
                                        <version>1</version>
                                        <dependencyManagement>
                                          <dependencies>
                                            <dependency>
                                              <groupId>junit</groupId>
                                              <artifactId>junit</artifactId>
                                              <version>4.0</version>
                                            </dependency>
                                          </dependencies>
                                        </dependencyManagement>
                                        """);

    VirtualFile project = createModulePom("project",
                                          """
                                            <groupId>test</groupId>
                                            <artifactId>project</artifactId>
                                            <version>1</version>
                                            <dependencyManagement>
                                              <dependencies>
                                                <dependency>
                                                  <groupId>test</groupId>
                                                  <artifactId>bom</artifactId>
                                                  <version>1</version>
                                                  <type>pom</type>
                                                  <scope>import</scope>
                                                </dependency>
                                              </dependencies>
                                            </dependencyManagement>
                                            <dependencies>
                                              <dependency>
                                                <groupId>junit</groupId>
                                                <artifactId>junit</artifactId>
                                              </dependency>
                                            </dependencies>
                                            """);
    importProjectsWithErrors(bom, project);
    assertModules("bom", "project");

    // reset embedders and try to update projects from scratch
    myProjectsManager.getEmbeddersManager().releaseForcefullyInTests();

    updateAllProjects();

    assertModuleLibDeps("project", "Maven: junit:junit:4.0");
  }
}