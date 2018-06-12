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

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.MavenImportingTestCase;

import java.io.File;
import java.util.Arrays;

public class DependenciesManagementTest extends MavenImportingTestCase {
  public void testImportingDependencies() throws Exception {
    if (!hasMavenInstallation()) return;

    setRepositoryPath(new File(myDir, "/repo").getPath());
    updateSettingsXml("<localRepository>" + getRepositoryPath() + "</localRepository>");

    createModulePom("__temp",
                    "<groupId>test</groupId>" +
                    "<artifactId>bom</artifactId>" +
                    "<packaging>pom</packaging>" +
                    "<version>1</version>" +

                    "<dependencyManagement>" +
                    "  <dependencies>" +
                    "    <dependency>" +
                    "      <groupId>junit</groupId>" +
                    "      <artifactId>junit</artifactId>" +
                    "      <version>4.0</version>" +
                    "    </dependency>" +
                    "  </dependencies>" +
                    "</dependencyManagement>");

    executeGoal("__temp", "install");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencyManagement>" +
                  "  <dependencies>" +
                  "    <dependency>" +
                  "      <groupId>test</groupId>" +
                  "      <artifactId>bom</artifactId>" +
                  "      <version>1</version>" +
                  "      <type>pom</type>" +
                  "      <scope>import</scope>" +
                  "    </dependency>" +
                  "  </dependencies>" +
                  "</dependencyManagement>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertModuleLibDeps("project", "Maven: junit:junit:4.0");
  }

  public void testImportingNotInstalledDependencies() throws Exception {
    if (ignore()) return;

    setRepositoryPath(new File(myDir, "/repo").getPath());
    updateSettingsXml("<localRepository>" + getRepositoryPath() + "</localRepository>");

    VirtualFile bom = createModulePom("bom",
                                      "<groupId>test</groupId>" +
                                      "<artifactId>bom</artifactId>" +
                                      "<packaging>pom</packaging>" +
                                      "<version>1</version>" +

                                      "<dependencyManagement>" +
                                      "  <dependencies>" +
                                      "    <dependency>" +
                                      "      <groupId>junit</groupId>" +
                                      "      <artifactId>junit</artifactId>" +
                                      "      <version>4.0</version>" +
                                      "    </dependency>" +
                                      "  </dependencies>" +
                                      "</dependencyManagement>");

    VirtualFile project = createModulePom("project",
                                          "<groupId>test</groupId>" +
                                          "<artifactId>project</artifactId>" +
                                          "<version>1</version>" +

                                          "<dependencyManagement>" +
                                          "  <dependencies>" +
                                          "    <dependency>" +
                                          "      <groupId>test</groupId>" +
                                          "      <artifactId>bom</artifactId>" +
                                          "      <version>1</version>" +
                                          "      <type>pom</type>" +
                                          "      <scope>import</scope>" +
                                          "    </dependency>" +
                                          "  </dependencies>" +
                                          "</dependencyManagement>" +

                                          "<dependencies>" +
                                          "  <dependency>" +
                                          "    <groupId>junit</groupId>" +
                                          "    <artifactId>junit</artifactId>" +
                                          "  </dependency>" +
                                          "</dependencies>");
    importProjectsWithErrors(bom, project);
    assertModules("bom", "project");

    // reset embedders and try to resolve project from scratch in specific order - imported one goes first
    // to make maven cache it. we have to ensure that dependent project will be resolved correctly after that
    myProjectsManager.getEmbeddersManager().releaseForcefullyInTests();
    myProjectsManager.scheduleResolveInTests(Arrays.asList(myProjectsManager.findProject(bom),
                                                           myProjectsManager.findProject(project)));
    myProjectsManager.waitForResolvingCompletion();

    // maven doesn't expect imported pom to be in the reactor,
    // when it is fixed, let us know
    assertTrue(myProjectsManager.findProject(project).hasReadingProblems());
    assertModuleLibDeps("project");

    // actually should be
    // assertModuleLibDeps("project", "Maven: junit:junit:4.0");
  }

  public void testCheckThatOrderDoesntMatterForMaven() throws Exception {
    // this is a check that in general importing a dependent project after its dependency (parent in this case) works fine.
    // see previous test for more information

    setRepositoryPath(new File(myDir, "/repo").getPath());
    updateSettingsXml("<localRepository>" + getRepositoryPath() + "</localRepository>");

    VirtualFile parent = createModulePom("parent",
                                         "<groupId>test</groupId>" +
                                         "<artifactId>parent</artifactId>" +
                                         "<packaging>pom</packaging>" +
                                         "<version>1</version>" +

                                         "<dependencyManagement>" +
                                         "  <dependencies>" +
                                         "    <dependency>" +
                                         "      <groupId>junit</groupId>" +
                                         "      <artifactId>junit</artifactId>" +
                                         "      <version>4.0</version>" +
                                         "    </dependency>" +
                                         "  </dependencies>" +
                                         "</dependencyManagement>");

    VirtualFile project = createModulePom("project",
                                          "<groupId>test</groupId>" +
                                          "<artifactId>project</artifactId>" +
                                          "<version>1</version>" +

                                          "<parent>" +
                                          "  <groupId>test</groupId>" +
                                          "  <artifactId>parent</artifactId>" +
                                          "  <version>1</version>" +
                                          "</parent>" +

                                          "<dependencies>" +
                                          "  <dependency>" +
                                          "    <groupId>junit</groupId>" +
                                          "    <artifactId>junit</artifactId>" +
                                          "  </dependency>" +
                                          "</dependencies>");
    importProjects(parent, project);
    assertModules("parent", "project");

    assertModuleLibDeps("project", "Maven: junit:junit:4.0");

    myProjectsManager.getEmbeddersManager().reset();
    myProjectsManager.scheduleResolveInTests(Arrays.asList(myProjectsManager.findProject(parent),
                                                           myProjectsManager.findProject(project)));
    myProjectsManager.waitForResolvingCompletion();

    assertModuleLibDeps("project", "Maven: junit:junit:4.0");
  }
}