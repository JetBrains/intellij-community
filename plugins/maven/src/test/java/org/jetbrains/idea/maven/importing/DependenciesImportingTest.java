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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.project.MavenProject;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DependenciesImportingTest extends MavenImportingTestCase {
  public void testLibraryDependency() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertModules("project");
    assertModuleLibDep("project", "Maven: junit:junit:4.0",
                       "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar!/",
                       "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-sources.jar!/",
                       "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-javadoc.jar!/");
  }

  public void testSystemDependency() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "    <scope>system</scope>" +
                  "    <systemPath>" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar</systemPath>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertModules("project");
    assertModuleLibDep("project", "Maven: junit:junit:4.0",
                       Arrays.asList("jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar!/"),
                       Collections.<String>emptyList(), Collections.<String>emptyList());
  }

  public void testTestJarDependencies() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "    <type>test-jar</type>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertModules("project");
    assertModuleLibDep("project", "Maven: junit:junit:test-jar:tests:4.0",
                       "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-tests.jar!/",
                       "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-test-sources.jar!/",
                       "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-test-javadoc.jar!/");
  }

  public void testDependencyWithClassifier() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "    <classifier>bar</classifier>" +
                  "  </dependency>" +
                  "</dependencies>");
    assertModules("project");
    assertModuleLibDep("project", "Maven: junit:junit:bar:4.0",
                       "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-bar.jar!/",
                       "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-sources.jar!/",
                       "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-javadoc.jar!/");
  }

  public void testSystemDependencyWithoutPath() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "    <scope>system</scope>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertModules("project");
    assertModuleLibDeps("project"); // dependency was not added due to reported pom model problem. 
  }

  public void testPreservingDependenciesOrder() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>B</groupId>" +
                  "    <artifactId>B</artifactId>" +
                  "    <version>2</version>" +
                  "  </dependency>" +
                  "  <dependency>" +
                  "    <groupId>A</groupId>" +
                  "    <artifactId>A</artifactId>" +
                  "    <version>1</version>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertModules("project");
    assertModuleLibDeps("project", "Maven: B:B:2", "Maven: A:A:1");
  }

  public void testDoNotResetDependenciesIfProjectIsInvalid() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>group</groupId>" +
                     "    <artifactId>lib</artifactId>" +
                     "    <version>1</version>" +
                     "  </dependency>" +
                     "</dependencies>");

    importProject();
    assertModuleLibDeps("project", "Maven: group:lib:1");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version" + // incomplete tag

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>group</groupId>" +
                     "    <artifactId>lib</artifactId>" +
                     "    <version>1</version>" +
                     "  </dependency>" +
                     "</dependencies>");

    importProject();
    assertModuleLibDeps("project", "Maven: group:lib:1");
  }

  public void testInterModuleDependencies() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m2</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>");

    importProject();
    assertModules("project", "m1", "m2");

    assertModuleModuleDeps("m1", "m2");
  }

  public void testInterModuleDependenciesWithoutModuleVersions() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m2</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +

                          "<parent>" +
                          "  <groupId>test</groupId>" +
                          "  <artifactId>project</artifactId>" +
                          "  <version>1</version>" +
                          "</parent>");

    importProject();
    assertModules("project", "m1", "m2");

    assertModuleModuleDeps("m1", "m2");
  }

  public void testInterModuleDependenciesWithVersionRanges() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m2</artifactId>" +
                          "    <version>[1, 2]</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>");

    importProject();
    assertModules("project", "m1", "m2");

    assertModuleModuleDeps("m1", "m2");
  }

  public void testInterModuleDependenciesWithoutModuleGroup() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m2</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<artifactId>m2</artifactId>" +
                          "<version>1</version>" +

                          "<parent>" +
                          "  <groupId>test</groupId>" +
                          "  <artifactId>project</artifactId>" +
                          "  <version>1</version>" +
                          "</parent>");

    importProject();
    assertModules("project", "m1", "m2");

    assertModuleModuleDeps("m1", "m2");
  }

  public void testInterModuleDependenciesIfThereArePropertiesInArtifactHeader() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<properties>" +
                     "  <module2Name>m2</module2Name>" +
                     "</properties>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m2</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>${module2Name}</artifactId>" +
                          "<version>${project.parent.version}</version>" +

                          "<parent>" +
                          "  <groupId>test</groupId>" +
                          "  <artifactId>project</artifactId>" +
                          "  <version>1</version>" +
                          "</parent>");

    importProject();
    assertModules("project", "m1", "m2");

    assertModuleModuleDeps("m1", "m2");
  }

  public void testInterModuleDependenciesIfThereArePropertiesInArtifactHeaderDefinedInParent() throws Exception {
    createProjectPom("<groupId>${groupProp}</groupId>" +
                     "<artifactId>parent</artifactId>" +
                     "<version>${versionProp}</version>" +
                     "<packaging>pom</packaging>" +

                     "<properties>" +
                     "  <groupProp>test</groupProp>" +
                     "  <versionProp>1</versionProp>" +
                     "</properties>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1",
                    "<parent>" +
                    "  <groupId>${groupProp}</groupId>" +
                    "  <artifactId>parent</artifactId>" +
                    "  <version>${versionProp}</version>" +
                    "</parent>" +
                    "<artifactId>m1</artifactId>" +

                    "<dependencies>" +
                    "  <dependency>" +
                    "    <groupId>${groupProp}</groupId>" +
                    "    <artifactId>m2</artifactId>" +
                    "    <version>${versionProp}</version>" +
                    "  </dependency>" +
                    "</dependencies>");

    createModulePom("m2",
                    "<parent>" +
                    "  <groupId>${groupProp}</groupId>" +
                    "  <artifactId>parent</artifactId>" +
                    "  <version>${versionProp}</version>" +
                    "</parent>" +
                    "<artifactId>m2</artifactId>");

    importProject();
    assertModules("parent", "m1", "m2");

    assertModuleModuleDeps("m1", "m2");
  }

  public void testDependencyOnSelf() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>test</groupId>" +
                  "    <artifactId>project</artifactId>" +
                  "    <version>1</version>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertModuleModuleDeps("project");
  }

  public void testIntermoduleDependencyOnTheSameModuleWithDifferentTypes() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m2</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m2</artifactId>" +
                          "    <version>1</version>" +
                          "    <type>test-jar</type>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>");

    importProject();
    assertModules("project", "m1", "m2");

    assertModuleModuleDeps("m1", "m2", "m2");
  }

  public void testDependencyScopes() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>test</groupId>" +
                  "    <artifactId>foo1</artifactId>" +
                  "    <version>1</version>" +
                  "  </dependency>" +
                  "  <dependency>" +
                  "    <groupId>test</groupId>" +
                  "    <artifactId>foo2</artifactId>" +
                  "    <version>1</version>" +
                  "    <scope>runtime</scope>" +
                  "  </dependency>" +
                  "  <dependency>" +
                  "    <groupId>test</groupId>" +
                  "    <artifactId>foo3</artifactId>" +
                  "    <version>1</version>" +
                  "    <scope>test</scope>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertModuleLibDepScope("project", "Maven: test:foo1:1", DependencyScope.COMPILE);
    assertModuleLibDepScope("project", "Maven: test:foo2:1", DependencyScope.RUNTIME);
    assertModuleLibDepScope("project", "Maven: test:foo3:1", DependencyScope.TEST);
  }

  public void testModuleDependencyScopes() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "  <module>m3</module>" +
                     "  <module>m4</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m2</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m3</artifactId>" +
                          "    <version>1</version>" +
                          "    <scope>runtime</scope>" +
                          "  </dependency>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m4</artifactId>" +
                          "    <version>1</version>" +
                          "    <scope>test</scope>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>");
    createModulePom("m3", "<groupId>test</groupId>" +
                          "<artifactId>m3</artifactId>" +
                          "<version>1</version>");
    createModulePom("m4", "<groupId>test</groupId>" +
                          "<artifactId>m4</artifactId>" +
                          "<version>1</version>");

    importProject();
    assertModules("project", "m1", "m2", "m3", "m4");

    assertModuleModuleDepScope("m1", "m2", DependencyScope.COMPILE);
    assertModuleModuleDepScope("m1", "m3", DependencyScope.RUNTIME);
    assertModuleModuleDepScope("m1", "m4", DependencyScope.TEST);
  }

  public void testDependenciesAreNotExported() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m2</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "  <dependency>" +
                          "    <groupId>lib</groupId>" +
                          "    <artifactId>lib</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>");

    importProject();
    assertExportedDeps("m1");
  }

  public void testTransitiveDependencies() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m2</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>group</groupId>" +
                          "    <artifactId>id</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    importProject();
    resolveDependenciesAndImport();
    assertModules("project", "m1", "m2");

    assertModuleLibDeps("m2", "Maven: group:id:1");
    assertModuleLibDeps("m1", "Maven: group:id:1");
  }

  public void testTransitiveLibraryDependencyVersionResolution() throws Exception {
    // this test hanles the case when the particular dependency list cause embedder set
    // the versionRange for the xml-apis:xml-apis:1.0.b2 artifact to null.
    // see http://jira.codehaus.org/browse/MNG-3386

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>dom4j</groupId>" +
                  "    <artifactId>dom4j</artifactId>" +
                  "    <version>1.6.1</version>" +
                  "    <scope>runtime</scope>" +
                  "  </dependency>" +
                  "  <dependency>" +
                  "     <groupId>org.apache.ws.commons.util</groupId>" +
                  "     <artifactId>ws-commons-util</artifactId>" +
                  "     <version>1.0.2</version>" +
                  "  </dependency>" +
                  "</dependencies>");
    resolveDependenciesAndImport();

    assertModules("project");
    assertModuleLibDep("project", "Maven: xml-apis:xml-apis:1.0.b2");
  }

  public void testExclusionOfTransitiveDependencies() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m2</artifactId>" +
                          "    <version>1</version>" +
                          "      <exclusions>" +
                          "        <exclusion>" +
                          "          <groupId>group</groupId>" +
                          "          <artifactId>id</artifactId>" +
                          "        </exclusion>" +
                          "      </exclusions>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>group</groupId>" +
                          "    <artifactId>id</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "</dependencies>");
    importProject();

    assertModuleLibDeps("m2", "Maven: group:id:1");

    assertModuleModuleDeps("m1", "m2");
    assertModuleLibDeps("m1");
  }

  public void testDependencyWithEnvironmentProperty() throws Exception {
    String javaHome = FileUtil.toSystemIndependentName(System.getProperty("java.home"));

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>direct-system-dependency</groupId>" +
                  "    <artifactId>direct-system-dependency</artifactId>" +
                  "    <version>1.0</version>" +
                  "    <scope>system</scope>" +
                  "    <systemPath>${java.home}/lib/tools.jar</systemPath>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertModules("project");
    assertModuleLibDep("project",
                       "Maven: direct-system-dependency:direct-system-dependency:1.0",
                       "jar://" + javaHome + "/lib/tools.jar!/");
  }

  public void testDependencyWithEnvironmentENVProperty() throws Exception {
    String envDir = FileUtil.toSystemIndependentName(System.getenv(getEnvVar()));
    if (envDir.endsWith("/")) envDir = envDir.substring(0, envDir.length() - 1);

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>direct-system-dependency</groupId>" +
                  "    <artifactId>direct-system-dependency</artifactId>" +
                  "    <version>1.0</version>" +
                  "    <scope>system</scope>" +
                  "    <systemPath>${env." + getEnvVar() + "}/lib/tools.jar</systemPath>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertModules("project");
    assertModuleLibDep("project",
                       "Maven: direct-system-dependency:direct-system-dependency:1.0",
                       "jar://" + envDir + "/lib/tools.jar!/");
  }

  public void testDependencyWithVersionRangeOnModule() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m2</artifactId>" +
                          "    <version>[1, 3]</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>2</version>");

    importProject();

    assertModules("project", "m1", "m2");

    if (ignore()) return;

    assertModuleModuleDeps("m1", "m2");
    assertModuleLibDeps("m1");
  }

  public void testPropertiesInInheritedDependencies() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>group</groupId>" +
                     "    <artifactId>lib</artifactId>" +
                     "    <version>${project.version}</version>" +
                     "  </dependency>" +
                     "</dependencies>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    createModulePom("m", "<groupId>test</groupId>" +
                         "<artifactId>m</artifactId>" +
                         "<version>2</version>" +

                         "<parent>" +
                         "  <groupId>test</groupId>" +
                         "  <artifactId>project</artifactId>" +
                         "  <version>1</version>" +
                         "</parent>");

    importProject();

    assertModuleLibDep("m", "Maven: group:lib:2");
  }

  public void testPropertyInTheModuleDependency() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<properties>" +
                     "  <dep-version>1.2.3</dep-version>" +
                     "</properties>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>"
    );

    createModulePom("m", "<groupId>test</groupId>" +
                         "<artifactId>m</artifactId>" +

                         "<parent>" +
                         "  <groupId>test</groupId>" +
                         "  <artifactId>project</artifactId>" +
                         "  <version>1</version>" +
                         "</parent>" +

                         "<dependencies>" +
                         "  <dependency>" +
                         "    <groupId>group</groupId>" +
                         "    <artifactId>id</artifactId>" +
                         "    <version>${dep-version}</version>" +
                         "  </dependency>" +
                         "</dependencies>");

    importProject();

    assertModules("project", "m");
    assertModuleLibDeps("m", "Maven: group:id:1.2.3");
  }

  public void testManagedModuleDependency() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<dependencyManagement>" +
                     "  <dependencies>" +
                     "    <dependency>" +
                     "      <groupId>group</groupId>" +
                     "      <artifactId>id</artifactId>" +
                     "      <version>1</version>" +
                     "    </dependency>" +
                     "  </dependencies>" +
                     "</dependencyManagement>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    createModulePom("m", "<groupId>test</groupId>" +
                         "<artifactId>m</artifactId>" +

                         "<parent>" +
                         "  <groupId>test</groupId>" +
                         "  <artifactId>project</artifactId>" +
                         "  <version>1</version>" +
                         "</parent>" +

                         "<dependencies>" +
                         "  <dependency>" +
                         "    <groupId>group</groupId>" +
                         "    <artifactId>id</artifactId>" +
                         "  </dependency>" +
                         "</dependencies>");

    importProject();
    assertModuleLibDeps("m", "Maven: group:id:1");
  }

  public void testPropertyInTheManagedModuleDependencyVersion() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<properties>" +
                     "  <dep-version>1</dep-version>" +
                     "</properties>" +

                     "<dependencyManagement>" +
                     "  <dependencies>" +
                     "    <dependency>" +
                     "      <groupId>group</groupId>" +
                     "      <artifactId>id</artifactId>" +
                     "      <version>${dep-version}</version>" +
                     "    </dependency>" +
                     "  </dependencies>" +
                     "</dependencyManagement>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    createModulePom("m", "<groupId>test</groupId>" +
                         "<artifactId>m</artifactId>" +

                         "<parent>" +
                         "  <groupId>test</groupId>" +
                         "  <artifactId>project</artifactId>" +
                         "  <version>1</version>" +
                         "</parent>" +

                         "<dependencies>" +
                         "  <dependency>" +
                         "    <groupId>group</groupId>" +
                         "    <artifactId>id</artifactId>" +
                         "  </dependency>" +
                         "</dependencies>");

    importProject();

    assertModules("project", "m");
    assertModuleLibDeps("m", "Maven: group:id:1");
  }

  public void testPomTypeDependency() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>junit</groupId>" +
                     "    <artifactId>junit</artifactId>" +
                     "    <version>4.0</version>" +
                     "    <type>pom</type>" +
                     "  </dependency>" +
                     "</dependencies>");

    importProject(); // shouldn't throw any exception
  }

  public void testPropertyInTheManagedModuleDependencyVersionOfPomType() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<properties>" +
                     "  <version>1</version>" +
                     "</properties>" +

                     "<dependencyManagement>" +
                     "  <dependencies>" +
                     "    <dependency>" +
                     "      <groupId>xxx</groupId>" +
                     "      <artifactId>yyy</artifactId>" +
                     "      <version>${version}</version>" +
                     "      <type>pom</type>" +
                     "    </dependency>" +
                     "  </dependencies>" +
                     "</dependencyManagement>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    createModulePom("m", "<groupId>test</groupId>" +
                         "<artifactId>m</artifactId>" +

                         "<parent>" +
                         "  <groupId>test</groupId>" +
                         "  <artifactId>project</artifactId>" +
                         "  <version>1</version>" +
                         "</parent>" +

                         "<dependencies>" +
                         "  <dependency>" +
                         "    <groupId>xxx</groupId>" +
                         "    <artifactId>yyy</artifactId>" +
                         "    <type>pom</type>" +
                         "  </dependency>" +
                         "</dependencies>");

    importProject();

    assertModules("project", "m");
    assertModuleLibDeps("m");

    if (ignore()) return;

    MavenProject root = myProjectsTree.getRootProjects().get(0);
    List<MavenProject> modules = myProjectsTree.getModules(root);

    assertOrderedElementsAreEqual(root.getProblems());
    assertOrderedElementsAreEqual(modules.get(0).getProblems(),
                                  "Unresolved dependency: xxx:yyy:pom:1:compile");
  }

  public void testResolvingFromRepositoriesIfSeveral() throws Exception {
    MavenCustomRepositoryHelper fixture = new MavenCustomRepositoryHelper(myDir, "local1");
    setRepositoryPath(fixture.getTestDataPath("local1"));
    removeFromLocalRepository("junit");

    File file = fixture.getTestData("local1/junit/junit/4.0/junit-4.0.pom");
    assertFalse(file.exists());

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "  <repositories>" +
                  "    <repository>" +
                  "      <id>foo</id>" +
                  "      <url>http://foo.bar</url>" +
                  "    </repository>" +
                  "  </repositories>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertTrue(file.exists());
  }

  public void testUsingMirrors() throws Exception {
    setRepositoryPath(myDir.getPath() + "/repo");
    String mirrorPath = FileUtil.toSystemIndependentName(myDir.getPath() + "/mirror");

    updateSettingsXmlFully("<settings>" +
                           "  <mirrors>" +
                           "    <mirror>" +
                           "      <id>foo</id>" +
                           "      <url>file://" + mirrorPath + "</url>" +
                           "      <mirrorOf>*</mirrorOf>" +
                           "    </mirror>" +
                           "  </mirrors>" +
                           "</settings>");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertFalse(new File(getRepositoryFile(), "junit").exists());
    assertTrue(myProjectsTree.findProject(myProjectPom).hasUnresolvedArtifacts());
  }

  public void testCanResolveDependenciesWhenExtensionPluginNotFound() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "  </dependency>" +
                  "</dependencies>" +

                  "<build>" +
                  " <plugins>" +
                  "   <plugin>" +
                  "     <groupId>xxx</groupId>" +
                  "     <artifactId>yyy</artifactId>" +
                  "     <version>1</version>" +
                  "     <extensions>true</extensions>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    assertModuleLibDep("project", "Maven: junit:junit:4.0");
  }

  public void testDoNotRemoveLibrariesOnImportIfProjectWasNotChanged() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertProjectLibraries("Maven: junit:junit:4.0");
    assertModuleLibDeps("project", "Maven: junit:junit:4.0");

    myProjectsManager.importProjects();

    assertProjectLibraries("Maven: junit:junit:4.0");
    assertModuleLibDeps("project", "Maven: junit:junit:4.0");
  }

  public void testDoNotCreateSameLibraryTwice() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "  </dependency>" +
                  "</dependencies>");

    importProject();

    assertProjectLibraries("Maven: junit:junit:4.0");
    assertModuleLibDeps("project", "Maven: junit:junit:4.0");
  }

  public void testCreateSeparateLibraryForDifferentArtifactTypeAndClassifier() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "  </dependency>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "    <type>test-jar</type>" +
                  "  </dependency>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "    <classifier>jdk5</classifier>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertProjectLibraries("Maven: junit:junit:4.0",
                           "Maven: junit:junit:test-jar:tests:4.0",
                           "Maven: junit:junit:jdk5:4.0");
    assertModuleLibDeps("project",
                        "Maven: junit:junit:4.0",
                        "Maven: junit:junit:test-jar:tests:4.0",
                        "Maven: junit:junit:jdk5:4.0");
  }

  public void testDoNotResetUserLibraryDependencies() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertProjectLibraries("Maven: junit:junit:4.0");
    assertModuleLibDeps("project", "Maven: junit:junit:4.0");

    createAndAddProjectLibrary("project", "My Library");

    assertProjectLibraries("Maven: junit:junit:4.0", "My Library");
    assertModuleLibDeps("project", "Maven: junit:junit:4.0", "My Library");

    importProject();

    assertProjectLibraries("Maven: junit:junit:4.0", "My Library");
    // todo should keep deps' order
    assertModuleLibDeps("project", "My Library", "Maven: junit:junit:4.0");
  }

  public void testDoNotResetUserModuleDependencies() throws Exception {
    VirtualFile m1 = createModulePom("m1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m1</artifactId>" +
                                     "<version>1</version>" +

                                     "<dependencies>" +
                                     "  <dependency>" +
                                     "    <groupId>test</groupId>" +
                                     "    <artifactId>m2</artifactId>" +
                                     "    <version>1</version>" +
                                     "  </dependency>" +
                                     "</dependencies>");
    VirtualFile m2 = createModulePom("m2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m2</artifactId>" +
                                     "<version>1</version>");
    importProjects(m1, m2);
    assertModuleModuleDeps("m1", "m2");

    Module module = createModule("my-module");

    ModifiableRootModel model = ModuleRootManager.getInstance(getModule("m1")).getModifiableModel();
    model.addModuleOrderEntry(module);
    model.commit();

    assertModuleModuleDeps("m1", "m2", "my-module");

    importProjects(m1, m2);

    assertModuleModuleDeps("m1", "my-module", "m2");
  }

  public void testRemoveUnnecessaryMavenizedModuleDepsOnRepomport() throws Exception {
    VirtualFile m1 = createModulePom("m1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m1</artifactId>" +
                                     "<version>1</version>" +

                                     "<dependencies>" +
                                     "  <dependency>" +
                                     "    <groupId>test</groupId>" +
                                     "    <artifactId>m2</artifactId>" +
                                     "    <version>1</version>" +
                                     "  </dependency>" +
                                     "</dependencies>");
    VirtualFile m2 = createModulePom("m2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m2</artifactId>" +
                                     "<version>1</version>");
    importProjects(m1, m2);
    assertModuleModuleDeps("m1", "m2");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>");

    importProjects(m1, m2);
    assertModuleModuleDeps("m1");
  }

  public void testDoNotResetCustomRootEntries() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertProjectLibraries("Maven: junit:junit:4.0");
    assertModuleLibDeps("project", "Maven: junit:junit:4.0");

    addLibraryRoot("Maven: junit:junit:4.0", OrderRootType.CLASSES, "file://foo.classes");
    addLibraryRoot("Maven: junit:junit:4.0", OrderRootType.SOURCES, "file://foo.sources");
    addLibraryRoot("Maven: junit:junit:4.0", JavadocOrderRootType.getInstance(), "file://foo.javadoc");

    assertModuleLibDep("project", "Maven: junit:junit:4.0",
                       Arrays.asList("jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar!/", "file://foo.classes"),
                       Arrays.asList("jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-sources.jar!/", "file://foo.sources"),
                       Arrays.asList("jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-javadoc.jar!/", "file://foo.javadoc"));

    scheduleResolveAll();
    resolveDependenciesAndImport();

    assertModuleLibDep("project", "Maven: junit:junit:4.0",
                       Arrays.asList("jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar!/", "file://foo.classes"),
                       Arrays.asList("jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-sources.jar!/", "file://foo.sources"),
                       Arrays.asList("jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-javadoc.jar!/", "file://foo.javadoc"));
  }

  public void testDoNotResetCustomRootForSystemLibraries() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>xxx</groupId>" +
                  "    <artifactId>yyy</artifactId>" +
                  "    <version>1</version>" +
                  "    <scope>system</scope>" +
                  "    <systemPath>" + getRoot() + "/foo/foo.jar</systemPath>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertProjectLibraries("Maven: xxx:yyy:1");
    assertModuleLibDeps("project", "Maven: xxx:yyy:1");

    // add sources and javadoc near the jar, just like in the local repository
    addLibraryRoot("Maven: xxx:yyy:1", OrderRootType.SOURCES, "jar://" + getRoot() + "/foo/foo-sources.jar!/");
    addLibraryRoot("Maven: xxx:yyy:1", JavadocOrderRootType.getInstance(), "jar://" + getRoot() + "/foo/foo-javadoc.jar!/");

    assertModuleLibDep("project", "Maven: xxx:yyy:1",
                       Arrays.asList("jar://" + getRoot() + "/foo/foo.jar!/"),
                       Arrays.asList("jar://" + getRoot() + "/foo/foo-sources.jar!/"),
                       Arrays.asList("jar://" + getRoot() + "/foo/foo-javadoc.jar!/"));

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>xxx</groupId>" +
                     "    <artifactId>yyy</artifactId>" +
                     "    <version>1</version>" +
                     "    <scope>system</scope>" +
                     "    <systemPath>" + getRoot() + "/bar/bar.jar</systemPath>" +
                     "  </dependency>" +
                     "</dependencies>");
    scheduleResolveAll();
    resolveDependenciesAndImport();

    assertModuleLibDep("project", "Maven: xxx:yyy:1",
                       Arrays.asList("jar://" + getRoot() + "/bar/bar.jar!/"),
                       Arrays.asList("jar://" + getRoot() + "/foo/foo-sources.jar!/"),
                       Arrays.asList("jar://" + getRoot() + "/foo/foo-javadoc.jar!/"));
  }

  public void testUpdateRootEntriesWithActualPath() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertProjectLibraries("Maven: junit:junit:4.0");
    assertModuleLibDeps("project", "Maven: junit:junit:4.0");

    assertModuleLibDep("project", "Maven: junit:junit:4.0",
                       "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar!/",
                       "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-sources.jar!/",
                       "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-javadoc.jar!/");

    setRepositoryPath(new File(myDir, "__repo").getPath());
    myProjectsManager.getEmbeddersManager().reset(); // to recognize repository change

    scheduleResolveAll();
    resolveDependenciesAndImport();

    assertModuleLibDep("project", "Maven: junit:junit:4.0",
                       "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar!/",
                       "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-sources.jar!/",
                       "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-javadoc.jar!/");
  }

  public void testUpdateRootEntriesWithActualPathForDependenciesWithClassifiers() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>org.testng</groupId>" +
                  "    <artifactId>testng</artifactId>" +
                  "    <version>5.8</version>" +
                  "    <classifier>jdk15</classifier>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertModuleLibDeps("project", "Maven: org.testng:testng:jdk15:5.8", "Maven: junit:junit:3.8.1");
    assertModuleLibDep("project", "Maven: org.testng:testng:jdk15:5.8",
                       "jar://" + getRepositoryPath() + "/org/testng/testng/5.8/testng-5.8-jdk15.jar!/",
                       "jar://" + getRepositoryPath() + "/org/testng/testng/5.8/testng-5.8-sources.jar!/",
                       "jar://" + getRepositoryPath() + "/org/testng/testng/5.8/testng-5.8-javadoc.jar!/");

    setRepositoryPath(new File(myDir, "__repo").getPath());
    myProjectsManager.getEmbeddersManager().reset(); // to recognize repository change

    scheduleResolveAll();

    resolveDependenciesAndImport();

    assertModuleLibDep("project", "Maven: org.testng:testng:jdk15:5.8",
                       "jar://" + getRepositoryPath() + "/org/testng/testng/5.8/testng-5.8-jdk15.jar!/",
                       "jar://" + getRepositoryPath() + "/org/testng/testng/5.8/testng-5.8-sources.jar!/",
                       "jar://" + getRepositoryPath() + "/org/testng/testng/5.8/testng-5.8-javadoc.jar!/");
  }

  public void testDoNotPopulateSameRootEntriesOnEveryImport() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>4.0</version>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertModuleLibDep("project", "Maven: junit:junit:4.0",
                       Arrays.asList("jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar!/"),
                       Arrays.asList("jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-sources.jar!/"),
                       Arrays.asList("jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-javadoc.jar!/"));

    scheduleResolveAll();
    resolveDependenciesAndImport();
    scheduleResolveAll();
    resolveDependenciesAndImport();

    assertModuleLibDep("project", "Maven: junit:junit:4.0",
                       Arrays.asList("jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar!/"),
                       Arrays.asList("jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-sources.jar!/"),
                       Arrays.asList("jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-javadoc.jar!/"));
  }

  public void testDoNotPopulateSameRootEntriesOnEveryImportForSystemLibraries() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>xxx</groupId>" +
                  "    <artifactId>yyy</artifactId>" +
                  "    <version>1</version>" +
                  "    <scope>system</scope>" +
                  "    <systemPath>" + getRoot() + "/foo/bar.jar</systemPath>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertModuleLibDep("project", "Maven: xxx:yyy:1",
                       Arrays.asList("jar://" + getRoot() + "/foo/bar.jar!/"),
                       Collections.<String>emptyList(),
                       Collections.<String>emptyList());

    scheduleResolveAll();
    resolveDependenciesAndImport();
    scheduleResolveAll();
    resolveDependenciesAndImport();

    assertModuleLibDep("project", "Maven: xxx:yyy:1",
                       Arrays.asList("jar://" + getRoot() + "/foo/bar.jar!/"),
                       Collections.<String>emptyList(),
                       Collections.<String>emptyList());
  }

  public void testRemovingPreviousSystemPathForForSystemLibraries() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>xxx</groupId>" +
                  "    <artifactId>yyy</artifactId>" +
                  "    <version>1</version>" +
                  "    <scope>system</scope>" +
                  "    <systemPath>" + getRoot() + "/foo/bar.jar</systemPath>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertModuleLibDep("project", "Maven: xxx:yyy:1",
                       Arrays.asList("jar://" + getRoot() + "/foo/bar.jar!/"),
                       Collections.<String>emptyList(),
                       Collections.<String>emptyList());

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>xxx</groupId>" +
                     "    <artifactId>yyy</artifactId>" +
                     "    <version>1</version>" +
                     "    <scope>system</scope>" +
                     "    <systemPath>" + getRoot() + "/foo/xxx.jar</systemPath>" +
                     "  </dependency>" +
                     "</dependencies>");

    scheduleResolveAll();
    resolveDependenciesAndImport();

    assertModuleLibDep("project", "Maven: xxx:yyy:1",
                       Arrays.asList("jar://" + getRoot() + "/foo/xxx.jar!/"),
                       Collections.<String>emptyList(),
                       Collections.<String>emptyList());
  }

  public void testCustomSourcesAndJavadocPaths() throws Exception {
    if (!checkUltimate()) return;
    
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<packaging>swf</packaging>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>xxx</groupId>" +
                  "    <artifactId>yyy</artifactId>" +
                  "    <version>1</version>" +
                  "    <type>swc</type>" +
                  "  </dependency>" +
                  "</dependencies>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.sonatype.flexmojos</groupId>" +
                  "      <artifactId>flexmojos-maven-plugin</artifactId>" +
                  "      <version>3.5.0</version>" +
                  "      <extensions>true</extensions>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    assertModuleLibDep("project", "Maven: xxx:yyy:swc:1",
                       Arrays.asList("jar://" + getRepositoryPath() + "/xxx/yyy/1/yyy-1.swc!/"),
                       Arrays.asList("jar://" + getRepositoryPath() + "/xxx/yyy/1/yyy-1-sources.jar!/"),
                       Arrays.asList("jar://" + getRepositoryPath() + "/xxx/yyy/1/yyy-1-asdoc.zip!/"));
  }

  public void testRemovingUnusedLibraries() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>group</groupId>" +
                          "    <artifactId>lib1</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "  <dependency>" +
                          "    <groupId>group</groupId>" +
                          "    <artifactId>lib2</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>group</groupId>" +
                          "    <artifactId>lib2</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "  <dependency>" +
                          "    <groupId>group</groupId>" +
                          "    <artifactId>lib3</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "  <dependency>" +
                          "    <groupId>group</groupId>" +
                          "    <artifactId>lib4</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    importProject();
    assertProjectLibraries("Maven: group:lib1:1",
                           "Maven: group:lib2:1",
                           "Maven: group:lib3:1",
                           "Maven: group:lib4:1");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>group</groupId>" +
                          "    <artifactId>lib2</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>group</groupId>" +
                          "    <artifactId>lib3</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    importProject();
    assertProjectLibraries("Maven: group:lib2:1",
                           "Maven: group:lib3:1");
  }

  public void testDoNoRemoveUnusedLibraryIfItWasChanged() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>group</groupId>" +
                  "    <artifactId>lib1</artifactId>" +
                  "    <version>1</version>" +
                  "  </dependency>" +
                  "  <dependency>" +
                  "    <groupId>group</groupId>" +
                  "    <artifactId>lib2</artifactId>" +
                  "    <version>1</version>" +
                  "  </dependency>" +
                  "  <dependency>" +
                  "    <groupId>group</groupId>" +
                  "    <artifactId>lib3</artifactId>" +
                  "    <version>1</version>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertProjectLibraries("Maven: group:lib1:1",
                           "Maven: group:lib2:1",
                           "Maven: group:lib3:1");

    addLibraryRoot("Maven: group:lib1:1", JavadocOrderRootType.getInstance(), "file://foo.bar");
    clearLibraryRoots("Maven: group:lib2:1", JavadocOrderRootType.getInstance());
    addLibraryRoot("Maven: group:lib2:1", JavadocOrderRootType.getInstance(), "file://foo.baz");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertProjectLibraries("Maven: group:lib1:1",
                           "Maven: group:lib2:1");
  }

  public void testDoNoRemoveUserProjectLibraries() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    createAndAddProjectLibrary("project", "lib");

    assertProjectLibraries("lib");
    addLibraryRoot("lib", OrderRootType.CLASSES, "file://" + getRepositoryPath() + "/foo/bar.jar!/");
    assertModuleLibDeps("project", "lib");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertProjectLibraries("lib");
    assertModuleLibDeps("project", "lib");
  }

  public void testDoNoRemoveUnusedUserProjectLibraries() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    createProjectLibrary("lib");
    addLibraryRoot("lib", OrderRootType.CLASSES, "file://" + getRepositoryPath() + "/foo/bar.jar!/");

    assertProjectLibraries("lib");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertProjectLibraries("lib");
  }

  public void testRemovingUnusedLibrariesIfProjectRemoved() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>group</groupId>" +
                          "    <artifactId>lib1</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>group</groupId>" +
                          "    <artifactId>lib2</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    importProject();
    assertProjectLibraries("Maven: group:lib1:1",
                           "Maven: group:lib2:1");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "</modules>");

    configConfirmationForYesAnswer();
    readProjects(Arrays.asList(myProjectPom));
    resolveDependenciesAndImport();
    assertProjectLibraries("Maven: group:lib1:1");
  }

  public void testRemovingUnusedLibraryWithClassifier() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>group</groupId>" +
                  "    <artifactId>lib1</artifactId>" +
                  "    <version>1</version>" +
                  "    <classifier>tests</classifier>" +
                  "  </dependency>" +
                  "  <dependency>" +
                  "    <groupId>group</groupId>" +
                  "    <artifactId>lib2</artifactId>" +
                  "    <version>1</version>" +
                  "    <type>test-jar</type>" +
                  "    <classifier>tests</classifier>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertProjectLibraries("Maven: group:lib1:tests:1",
                           "Maven: group:lib2:test-jar:tests:1");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertProjectLibraries();
  }

  private Library createProjectLibrary(String libraryName) {
    return ProjectLibraryTable.getInstance(myProject).createLibrary(libraryName);
  }

  private void createAndAddProjectLibrary(String moduleName, String libraryName) {
    Library lib = createProjectLibrary(libraryName);
    ModifiableRootModel model = ModuleRootManager.getInstance(getModule(moduleName)).getModifiableModel();
    model.addLibraryEntry(lib);
    model.commit();
  }

  private void clearLibraryRoots(String libraryName, OrderRootType... types) {
    Library lib = ProjectLibraryTable.getInstance(myProject).getLibraryByName(libraryName);
    Library.ModifiableModel model = lib.getModifiableModel();
    for (OrderRootType eachType : types) {
      for (String each : model.getUrls(eachType)) {
        model.removeRoot(each, eachType);
      }
    }
    model.commit();
  }

  private void addLibraryRoot(String libraryName, OrderRootType type, String path) {
    Library lib = ProjectLibraryTable.getInstance(myProject).getLibraryByName(libraryName);
    Library.ModifiableModel model = lib.getModifiableModel();
    model.addRoot(path, type);
    model.commit();
  }

  public void testEjbDependenciesInJarProject() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>foo</groupId>" +
                  "    <artifactId>foo</artifactId>" +
                  "    <version>1</version>" +
                  "    <type>ejb</type>" +
                  "  </dependency>" +
                  "  <dependency>" +
                  "    <groupId>foo</groupId>" +
                  "    <artifactId>bar</artifactId>" +
                  "    <version>1</version>" +
                  "    <type>ejb-client</type>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertModules("project");
    assertModuleLibDeps("project", "Maven: foo:foo:ejb:1", "Maven: foo:bar:ejb-client:client:1");
  }

  public void testDoNotFailOnAbsentAppLibrary() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    LibraryTable appTable = LibraryTablesRegistrar.getInstance().getLibraryTable();
    Library lib = appTable.createLibrary("foo");
    ModifiableRootModel model = ModuleRootManager.getInstance(getModule("project")).getModifiableModel();
    model.addLibraryEntry(lib);
    model.commit();
    appTable.removeLibrary(lib);

    importProject(); // should not fail;
  }

  public void testDoNotFailToConfigureUnresolvedVersionRangeDependencies() throws Exception {
    // should not throw NPE when accessing CustomArtifact.getPath();
    MavenCustomRepositoryHelper helper = new MavenCustomRepositoryHelper(myDir, "local1");
    String repoPath = helper.getTestDataPath("local1");
    setRepositoryPath(repoPath);

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>junit</groupId>" +
                  "    <artifactId>junit</artifactId>" +
                  "    <version>[3.8.1,3.8.2]</version>" +
                  "  </dependency>" +
                  "  <dependency>" +
                  "    <groupId>org.apache.maven.errortest</groupId>" +
                  "    <artifactId>dep</artifactId>" +
                  "    <version>1</version>" +
                  "    <type>pom</type>" +
                  "  </dependency>" +
                  "</dependencies>" +

                  "<repositories>" +
                  "  <repository>" +
                  "    <id>central</id>" +
                  "    <url>file://localhost/${basedir}/repo</url>" +
                  "  </repository>" +
                  "</repositories>");

    assertModuleLibDeps("project", "Maven: junit:junit:3.8.2");
    assertModuleLibDep("project", "Maven: junit:junit:3.8.2",
                       "jar://" + repoPath + "/junit/junit/3.8.2/junit-3.8.2.jar!/");
  }
}
