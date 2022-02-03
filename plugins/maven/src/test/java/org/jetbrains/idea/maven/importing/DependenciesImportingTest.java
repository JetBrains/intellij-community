// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper;
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import org.jetbrains.idea.maven.project.MavenProject;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DependenciesImportingTest extends MavenMultiVersionImportingTestCase {
  @Test
  public void testLibraryDependency() {
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

  @Test
  public void testSystemDependency() {
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
                       Collections.singletonList("jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar!/"),
                       Collections.emptyList(), Collections.emptyList());
  }

  @Test
  public void testTestJarDependencies() {
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

  @Test
  public void testDependencyWithClassifier() {
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

  @Test
  public void testSystemDependencyWithoutPath() {
    createProjectPom("<groupId>test</groupId>" +
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
    importProjectWithErrors();

    assertModules("project");
    assertModuleLibDeps("project"); // dependency was not added due to reported pom model problem.
  }

  @Test
  public void testPreservingDependenciesOrder() {
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

  @Test
  public void testDoNotResetDependenciesIfProjectIsInvalid() {
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

    importProjectWithErrors();
    assertModuleLibDeps("project", "Maven: group:lib:1");
  }

  @Test
  public void testInterModuleDependencies() {
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

  @Test
  public void testInterModuleDependenciesWithClassifier() {
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
                          "    <classifier>client</classifier>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>");

    importProject();

    assertModuleModuleDeps("m1", "m2");
    assertModuleLibDep("m1", "Maven: test:m2:client:1",
                       "jar://" + getRepositoryPath() + "/test/m2/1/m2-1-client.jar!/",
                       "jar://" + getRepositoryPath() + "/test/m2/1/m2-1-sources.jar!/",
                       "jar://" + getRepositoryPath() + "/test/m2/1/m2-1-javadoc.jar!/");
  }

  @Test
  public void testDoNotAddInterModuleDependenciesFoUnsupportedDependencyType() {
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
                          "    <type>xxx</type>" +
                          "  </dependency>" +
                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>");

    importProject();
    assertModules("project", "m1", "m2");

    assertModuleModuleDeps("m1");
  }

  @Test
  public void testInterModuleDependenciesWithoutModuleVersions() {
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

  @Test
  public void testInterModuleDependenciesWithVersionRanges() {
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

  @Test
  public void testInterModuleDependenciesWithoutModuleGroup() {
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

  @Test
  public void testInterModuleDependenciesIfThereArePropertiesInArtifactHeader() {
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

  @Test
  public void testInterModuleDependenciesIfThereArePropertiesInArtifactHeaderDefinedInParent() {
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

  @Test
  public void testDependencyOnSelf() {
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

  @Test
  public void testDependencyOnSelfWithPomPackaging() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<packaging>pom</packaging>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>test</groupId>" +
                  "    <artifactId>project</artifactId>" +
                  "    <version>1</version>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertModuleModuleDeps("project");
  }

  @Test
  public void testIntermoduleDependencyOnTheSameModuleWithDifferentTypes() {
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

  @Test
  public void testDependencyScopes() {
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

  @Test
  public void testModuleDependencyScopes() {
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

  @Test
  public void testDependenciesAreNotExported() {
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

  @Test
  public void testTransitiveDependencies() {
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

  @Test
  public void testTransitiveLibraryDependencyVersionResolution() {
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

  @Test
  public void testExclusionOfTransitiveDependencies() {
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

  @Test
  public void testDependencyWithEnvironmentProperty() {
    String javaHome = FileUtil.toSystemIndependentName(System.getProperty("java.home"));

    createProjectPom("<groupId>test</groupId>" +
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
    importProjectWithErrors();

    assertModules("project");
    assertModuleLibDep("project",
                       "Maven: direct-system-dependency:direct-system-dependency:1.0",
                       "jar://" + javaHome + "/lib/tools.jar!/");
  }

  @Test
  public void testDependencyWithEnvironmentENVProperty() {
    String envDir = FileUtil.toSystemIndependentName(System.getenv(getEnvVar()));
    envDir = StringUtil.trimEnd(envDir, "/");

    createProjectPom("<groupId>test</groupId>" +
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
    importProjectWithErrors();

    assertModules("project");
    assertModuleLibDep("project",
                       "Maven: direct-system-dependency:direct-system-dependency:1.0",
                       "jar://" + envDir + "/lib/tools.jar!/");
  }

  @Test
  public void testDependencyWithVersionRangeOnModule() {
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

  @Test
  public void testPropertiesInInheritedDependencies() {
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

  @Test
  public void testPropertyInTheModuleDependency() {
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

  @Test
  public void testManagedModuleDependency() {
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

  @Test
  public void testPropertyInTheManagedModuleDependencyVersion() {
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

  @Test
  public void testPomTypeDependency() {
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

  @Test
  public void testPropertyInTheManagedModuleDependencyVersionOfPomType() {
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

    importProjectWithErrors();

    assertModules("project", "m");
    assertModuleLibDeps("m");


    MavenProject root = myProjectsTree.getRootProjects().get(0);
    List<MavenProject> modules = myProjectsTree.getModules(root);

    assertOrderedElementsAreEqual(root.getProblems());
    assertTrue(modules.get(0).getProblems().get(0).getDescription().contains("Unresolved dependency: 'xxx:yyy:pom:1'"));
  }

  @Test
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

  @Test
  public void testUsingMirrors() throws Exception {
    setRepositoryPath(myDir.getPath() + "/repo");
    String mirrorPath = myPathTransformer.toRemotePath(FileUtil.toSystemIndependentName(myDir.getPath() + "/mirror"));

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

    assertTrue(myProjectsTree.findProject(myProjectPom).hasUnresolvedArtifacts());
  }

  @Test
  public void testCanResolveDependenciesWhenExtensionPluginNotFound() {
    createProjectPom("<groupId>test</groupId>" +
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
    importProjectWithErrors();

    assertModuleLibDep("project", "Maven: junit:junit:4.0");
  }

  @Test
  public void testDoNotRemoveLibrariesOnImportIfProjectWasNotChanged() {
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

    importProject();

    assertProjectLibraries("Maven: junit:junit:4.0");
    assertModuleLibDeps("project", "Maven: junit:junit:4.0");
  }

  @Test
  public void testDoNotCreateSameLibraryTwice() {
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

  @Test
  public void testCreateSeparateLibraryForDifferentArtifactTypeAndClassifier() {
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

  @Test
  public void testDoNotResetUserLibraryDependencies() {
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

  @Test
  public void testDoNotResetUserModuleDependencies() {
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

    final Module module = createModule("my-module");

    ModuleRootModificationUtil.addDependency(getModule("m1"), module);

    assertModuleModuleDeps("m1", "m2", "my-module");

    importProjects(m1, m2);

    assertModuleModuleDeps("m1", "my-module", "m2");
  }

  @Test
  public void testRemoveUnnecessaryMavenizedModuleDepsOnRepomport() {
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

  @Test
  public void testDoNotResetCustomRootEntries() {
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
    addLibraryRoot("Maven: junit:junit:4.0", OrderRootType.SOURCES,
                   "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-sources.jar!/aaa");
    addLibraryRoot("Maven: junit:junit:4.0", JavadocOrderRootType.getInstance(),
                   "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-javadoc.jar!/bbb");

    assertModuleLibDep("project", "Maven: junit:junit:4.0",
                       Arrays.asList("jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar!/", "file://foo.classes"),
                       Arrays.asList("jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-sources.jar!/",
                                     "file://foo.sources",
                                     "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-sources.jar!/aaa"),
                       Arrays.asList("jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-javadoc.jar!/",
                                     "file://foo.javadoc",
                                     "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-javadoc.jar!/bbb"));

    scheduleResolveAll();
    resolveDependenciesAndImport();

    assertModuleLibDep("project", "Maven: junit:junit:4.0",
                       Arrays.asList("jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar!/", "file://foo.classes"),
                       Arrays.asList("jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-sources.jar!/", "file://foo.sources",
                                     "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-sources.jar!/aaa"),
                       Arrays.asList("jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-javadoc.jar!/", "file://foo.javadoc",
                                     "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-javadoc.jar!/bbb"));
  }

  @Test
  public void testDifferentSystemDependenciesWithSameId() {
    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>xxx</groupId>" +
                          "    <artifactId>yyy</artifactId>" +
                          "    <version>1</version>" +
                          "    <scope>system</scope>" +
                          "    <systemPath>" + getRoot() + "/m1/foo.jar</systemPath>" +
                          "  </dependency>" +
                          "</dependencies>");
    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>xxx</groupId>" +
                          "    <artifactId>yyy</artifactId>" +
                          "    <version>1</version>" +
                          "    <scope>system</scope>" +
                          "    <systemPath>" + getRoot() + "/m2/foo.jar</systemPath>" +
                          "  </dependency>" +
                          "</dependencies>");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");
    importProjectWithErrors();

    //    assertProjectLibraries("Maven: xxx:yyy:1");
    assertModuleLibDep("m1", "Maven: xxx:yyy:1", "jar://" + getRoot() + "/m1/foo.jar!/");
    assertModuleLibDep("m2", "Maven: xxx:yyy:1", "jar://" + getRoot() + "/m2/foo.jar!/");
  }

  @Test
  public void testUpdateRootEntriesWithActualPath() {
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

  @Test
  public void testUpdateRootEntriesWithActualPathForDependenciesWithClassifiers() {
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

  @Test
  public void testDoNotPopulateSameRootEntriesOnEveryImport() {
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

  @Test
  public void testDoNotPopulateSameRootEntriesOnEveryImportForSystemLibraries() {
    createProjectPom("<groupId>test</groupId>" +
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
    importProjectWithErrors();

    assertModuleLibDep("project", "Maven: xxx:yyy:1",
                       Arrays.asList("jar://" + getRoot() + "/foo/bar.jar!/"),
                       Collections.emptyList(),
                       Collections.emptyList());

    scheduleResolveAll();
    resolveDependenciesAndImport();
    scheduleResolveAll();
    resolveDependenciesAndImport();

    assertModuleLibDep("project", "Maven: xxx:yyy:1",
                       Arrays.asList("jar://" + getRoot() + "/foo/bar.jar!/"),
                       Collections.emptyList(),
                       Collections.emptyList());
  }

  @Test
  public void testRemovingPreviousSystemPathForForSystemLibraries() {
    createProjectPom("<groupId>test</groupId>" +
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
    importProjectWithErrors();

    assertModuleLibDep("project", "Maven: xxx:yyy:1",
                       Arrays.asList("jar://" + getRoot() + "/foo/bar.jar!/"),
                       Collections.emptyList(),
                       Collections.emptyList());

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

    importProjectWithErrors();

    assertModuleLibDep("project", "Maven: xxx:yyy:1",
                       Arrays.asList("jar://" + getRoot() + "/foo/xxx.jar!/"),
                       Collections.emptyList(),
                       Collections.emptyList());
  }

  @Test
  public void testRemovingUnusedLibraries() {
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

  @Test
  public void testDoNoRemoveUnusedLibraryIfItWasChanged() {
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

  @Test
  public void testDoNoRemoveUserProjectLibraries() {
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

  @Test
  public void testDoNoRemoveUnusedUserProjectLibraries() {
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

  @Test
  public void testRemovingUnusedLibrariesIfProjectRemoved() {
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
    importProject();
    assertProjectLibraries("Maven: group:lib1:1");
  }

  @Test
  public void testRemovingUnusedLibraryWithClassifier() {
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

  private Library createProjectLibrary(@NotNull String libraryName) {
    LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject);
    return WriteAction.computeAndWait(() -> {
      return libraryTable.createLibrary(libraryName);
    });
  }

  private void createAndAddProjectLibrary(@NotNull String moduleName, final String libraryName) {
    WriteAction.runAndWait(() -> {
      Library lib = createProjectLibrary(libraryName);
      ModuleRootModificationUtil.addDependency(getModule(moduleName), lib);
    });
  }

  private void clearLibraryRoots(@NotNull String libraryName, OrderRootType... types) {
    Library lib = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject).getLibraryByName(libraryName);
    WriteAction.runAndWait(() -> {
      Library.ModifiableModel model = lib.getModifiableModel();
      for (OrderRootType eachType : types) {
        for (String each : model.getUrls(eachType)) {
          model.removeRoot(each, eachType);
        }
      }
      model.commit();
    });
  }

  private void addLibraryRoot(@NotNull String libraryName, @NotNull OrderRootType type, @NotNull String path) {
    Library lib = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject).getLibraryByName(libraryName);
    WriteAction.runAndWait(() -> {
      Library.ModifiableModel model = lib.getModifiableModel();
      model.addRoot(path, type);
      model.commit();
    });
  }

  @Test
  public void testEjbDependenciesInJarProject() {
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

  @Test
  public void testDoNotFailOnAbsentAppLibrary() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    ApplicationManager.getApplication().invokeAndWait(() ->
                                                        ApplicationManager.getApplication().runWriteAction(() -> {
                                                          LibraryTable appTable = LibraryTablesRegistrar.getInstance().getLibraryTable();
                                                          Library lib = appTable.createLibrary("foo");
                                                          ModuleRootModificationUtil.addDependency(getModule("project"), lib);
                                                          appTable.removeLibrary(lib);
                                                        })
    );

    importProject(); // should not fail;
  }

  @Test
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
                  "    <url>file://localhost/" + repoPath + "</url>" +
                  "  </repository>" +
                  "</repositories>");

    assertModuleLibDeps("project", "Maven: junit:junit:3.8.2");
    assertModuleLibDep("project", "Maven: junit:junit:3.8.2",
                       "jar://" + repoPath + "/junit/junit/3.8.2/junit-3.8.2.jar!/");
  }

  //@Test
  //public void testVersionRangeDoesntBreakIndirectDependency() throws Exception {
  //  createProjectPom("<groupId>test</groupId>" +
  //                   "<artifactId>project</artifactId>" +
  //                   "<version>1</version>" +
  //                   "<packaging>pom</packaging>" +
  //
  //                   "<modules>" +
  //                   "  <module>m1</module>" +
  //                   "  <module>m2</module>" +
  //                   "</modules>");
  //
  //  createModulePom("m1", "<groupId>test</groupId>" +
  //                        "<artifactId>m1</artifactId>" +
  //                        "<version>1</version>" +
  //
  //                        "<dependencies>" +
  //                        "  <dependency>" +
  //                        "    <groupId>asm</groupId>" +
  //                        "    <artifactId>asm</artifactId>" +
  //                        "    <version>2.2.3</version>" +
  //                        "  </dependency>" +
  //                        "</dependencies>");
  //
  //  createModulePom("m2", "<groupId>test</groupId>" +
  //                        "<artifactId>m2</artifactId>" +
  //                        "<version>1</version>" +
  //
  //                        "<dependencies>" +
  //                        "  <dependency>" +
  //                        "    <groupId>test</groupId>" +
  //                        "    <artifactId>m1</artifactId>" +
  //                        "    <version>1</version>" +
  //                        "  </dependency>" +
  //                        "  <dependency>" +
  //                        "    <groupId>asm</groupId>" +
  //                        "    <artifactId>asm</artifactId>" +
  //                        "    <version>[2.2.3]</version>" +
  //                        "  </dependency>" +
  //                        "</dependencies>");
  //
  //  importProject();
  //
  //  assertModuleModuleDeps("m2", "m1");
  //  assertModuleLibDeps("m1", "Maven: asm:asm:2.2.3");
  //  assertModuleLibDeps("m2", "Maven: asm:asm:2.2.3");
  //}
  //
  @Test
  public void testVersionRangeInDependencyManagementDoesntBreakIndirectDependency() throws Exception {
    MavenCustomRepositoryHelper helper = new MavenCustomRepositoryHelper(myDir, "local1");
    String repoPath = helper.getTestDataPath("local1");
    setRepositoryPath(repoPath);

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>" +

                     "     <dependencyManagement>\n" +
                     "         <dependencies>\n" +
                     "              <dependency>\n" +
                     "                 <artifactId>asm</artifactId>\n" +
                     "                 <groupId>asm</groupId>\n" +
                     "                 <version>[2.2.1]</version>\n" +
                     "                 <scope>runtime</scope>\n" +
                     "             </dependency>\n" +
                     "             <dependency>\n" +
                     "                 <artifactId>asm-attrs</artifactId>\n" +
                     "                 <groupId>asm</groupId>\n" +
                     "                 <version>[2.2.1]</version>\n" +
                     "                 <scope>runtime</scope>\n" +
                     "             </dependency>\n" +
                     "         </dependencies>\n" +
                     "     </dependencyManagement>");

    createModulePom("m", "<groupId>test</groupId>" +
                         "<artifactId>m</artifactId>" +
                         "<version>1</version>" +
                         "" +
                         "    <parent>\n" +
                         "        <groupId>test</groupId>\n" +
                         "        <artifactId>project</artifactId>\n" +
                         "        <version>1</version>\n" +
                         "    </parent>" +

                         "<dependencies>" +
                         "  <dependency>" +
                         "            <artifactId>asm-attrs</artifactId>\n" +
                         "            <groupId>asm</groupId>\n" +
                         "            <scope>test</scope>" +
                         "  </dependency>" +
                         "</dependencies>");

    importProject();

    assertModuleLibDeps("m", "Maven: asm:asm-attrs:2.2.1", "Maven: asm:asm:2.2.1");
  }

  @Test
  public void testDependencyToIgnoredProject() {
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
                          "    <version>2</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    VirtualFile m2 = createModulePom("m2", "<groupId>test</groupId>" +
                                           "<artifactId>m2</artifactId>" +
                                           "<version>2</version>");

    importProject();

    assertModules("project", "m1", "m2");
    assertModuleModuleDeps("m1", "m2");

    configConfirmationForYesAnswer();


    setIgnoredFilesPathForNextImport(Collections.singletonList(m2.getPath()));

    if(!isNewImportingProcess) {
      myProjectsManager.forceUpdateProjects(myProjectsManager.getProjects());
    }
    importProject();

    assertModules("project", "m1");
    assertModuleLibDeps("m1", "Maven: test:m2:2");

    assertModuleLibDep("m1", "Maven: test:m2:2",
                       "jar://" + getRepositoryPath() + "/test/m2/2/m2-2.jar!/",
                       "jar://" + getRepositoryPath() + "/test/m2/2/m2-2-sources.jar!/",
                       "jar://" + getRepositoryPath() + "/test/m2/2/m2-2-javadoc.jar!/");
  }

  @Test
  public void testSaveJdkPosition() {
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
                          "    <groupId>junit</groupId>" +
                          "    <artifactId>junit</artifactId>" +
                          "    <version>4.0</version>" +
                          "  </dependency>" +

                          "</dependencies>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>");

    importProject();

    WriteAction.runAndWait(() -> {
      ModifiableRootModel rootModel = ModuleRootManager.getInstance(getModule("m1")).getModifiableModel();
      OrderEntry[] orderEntries = rootModel.getOrderEntries().clone();
      assert orderEntries.length == 4;
      assert orderEntries[0] instanceof JdkOrderEntry;
      assert orderEntries[1] instanceof ModuleSourceOrderEntry;
      assert ((ModuleOrderEntry)orderEntries[2]).getModuleName().equals("m2");
      assert "Maven: junit:junit:4.0".equals(((LibraryOrderEntry)orderEntries[3]).getLibraryName());

      rootModel.rearrangeOrderEntries(new OrderEntry[]{orderEntries[2], orderEntries[3], orderEntries[0], orderEntries[1]});

      rootModel.commit();
    });

    resolveDependenciesAndImport();

    // JDK position was saved
    OrderEntry[] orderEntries = ModuleRootManager.getInstance(getModule("m1")).getOrderEntries();
    assert orderEntries.length == 4;
    assert ((ModuleOrderEntry)orderEntries[0]).getModuleName().equals("m2");
    assert "Maven: junit:junit:4.0".equals(((LibraryOrderEntry)orderEntries[1]).getLibraryName());
    assert orderEntries[2] instanceof JdkOrderEntry;
    assert orderEntries[3] instanceof ModuleSourceOrderEntry;
  }

  @Test
  public void testSaveJdkPositionSystemDependency() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>m1</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>test</groupId>" +
                     "    <artifactId>systemDep</artifactId>" +
                     "    <version>1</version>" +
                     "    <scope>system</scope>" +
                     "    <systemPath>${java.home}/lib/rt.jar</systemPath>" +
                     "  </dependency>" +
                     "  <dependency>" +
                     "    <groupId>junit</groupId>" +
                     "    <artifactId>junit</artifactId>" +
                     "    <version>4.0</version>" +
                     "  </dependency>" +

                     "</dependencies>");
    importProjectWithErrors();

    WriteAction.runAndWait(() -> {
      ModifiableRootModel rootModel = ModuleRootManager.getInstance(getModule("m1")).getModifiableModel();
      OrderEntry[] orderEntries = rootModel.getOrderEntries().clone();
      assert orderEntries.length == 4;
      assert orderEntries[0] instanceof JdkOrderEntry;
      assert orderEntries[1] instanceof ModuleSourceOrderEntry;
      assert "Maven: test:systemDep:1".equals(((LibraryOrderEntry)orderEntries[2]).getLibraryName());
      assert "Maven: junit:junit:4.0".equals(((LibraryOrderEntry)orderEntries[3]).getLibraryName());

      rootModel.rearrangeOrderEntries(new OrderEntry[]{orderEntries[2], orderEntries[3], orderEntries[0], orderEntries[1]});

      rootModel.commit();
    });

    resolveDependenciesAndImport();

    // JDK position was saved
    OrderEntry[] orderEntries = ModuleRootManager.getInstance(getModule("m1")).getOrderEntries();
    assert orderEntries.length == 4;
    assert "Maven: test:systemDep:1".equals(((LibraryOrderEntry)orderEntries[0]).getLibraryName());
    assert "Maven: junit:junit:4.0".equals(((LibraryOrderEntry)orderEntries[1]).getLibraryName());
    assert orderEntries[2] instanceof JdkOrderEntry;
    assert orderEntries[3] instanceof ModuleSourceOrderEntry;
  }

  @Test
  public void testBundleDependencyType() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<dependencies>" +
                  "  <dependency>" +
                  "    <groupId>com.google.guava</groupId>" +
                  "    <artifactId>guava</artifactId>" +
                  "    <version>15.0</version>" +
                  "    <type>bundle</type>" +
                  "  </dependency>" +
                  "</dependencies>");

    assertProjectLibraries("Maven: com.google.guava:guava:15.0");
    assertModuleLibDep("project", "Maven: com.google.guava:guava:15.0",
                       "jar://" + getRepositoryPath() + "/com/google/guava/guava/15.0/guava-15.0.jar!/",
                       "jar://" + getRepositoryPath() + "/com/google/guava/guava/15.0/guava-15.0-sources.jar!/",
                       "jar://" + getRepositoryPath() + "/com/google/guava/guava/15.0/guava-15.0-javadoc.jar!/");
  }

  @Test
  public void testReimportingInheritedLibrary() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "</modules>" +
                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>junit1</groupId><artifactId>junit1</artifactId><version>3.3</version>" +
                     "  </dependency>" +
                     "</dependencies>");

    createModulePom("m1", "<parent>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>project</artifactId>" +
                          "    " +
                          "    <version>1</version>" +
                          "  </parent>" +
                          "<artifactId>m1</artifactId>");


    importProject();

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "</modules>" +
                     "<dependencies>" +
                     "  <dependency>" +
                     "    <groupId>junit</groupId><artifactId>junit</artifactId><version>4.0</version>" +
                     "  </dependency>" +
                     "</dependencies>");

    importProject();

    assertModuleLibDep("project", "Maven: junit:junit:4.0",
                       "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar!/",
                       "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-sources.jar!/",
                       "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-javadoc.jar!/");

    assertModuleLibDep("m1", "Maven: junit:junit:4.0",
                       "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar!/",
                       "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-sources.jar!/",
                       "jar://" + getRepositoryPath() + "/junit/junit/4.0/junit-4.0-javadoc.jar!/");
  }

  @Test
  public void testRemoveInvalidOrderEntry() {
    RegistryValue value = Registry.get("maven.always.remove.bad.entries");
    try {
      value.setValue(true);
      createProjectPom("<groupId>test</groupId>" +
                       "<artifactId>project</artifactId>" +
                       "<packaging>pom</packaging>" +
                       "<version>1</version>" +
                       "<dependencies>" +
                       "  <dependency>" +
                       "    <groupId>junit</groupId>" +
                       "    <artifactId>junit</artifactId>" +
                       "    <version>4.0</version>" +
                       "  </dependency>" +
                       "</dependencies>");
      importProject();

      WriteAction.runAndWait(() -> {
        ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(getModule("project")).getModifiableModel();
        modifiableModel.addInvalidLibrary("SomeLibrary", LibraryTablesRegistrar.PROJECT_LEVEL);
        modifiableModel
          .addInvalidLibrary("Maven: AnotherLibrary", LibraryTablesRegistrar.PROJECT_LEVEL);
        modifiableModel.commit();
      });

      importProject();

      List<String> librariesDepNames = Arrays.stream(ModuleRootManager.getInstance(getModule("project"))
                                                       .getOrderEntries())
        .filter(LibraryOrderEntry.class::isInstance)
        .map(LibraryOrderEntry.class::cast)
        .map(loe -> loe.getLibraryName())
        .collect(Collectors.toList());

      assertContain(librariesDepNames, "SomeLibrary", "Maven: junit:junit:4.0");
      assertDoesntContain(librariesDepNames, "Maven: AnotherLibrary");
    }
    finally {
      value.resetToDefault();
    }
  }

  @Test
  public void testTransitiveProfileDependency() {
    assumeVersionMoreThan("3.1.0");
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

                          "<profiles>" +
                          "  <profile>" +
                          "    <id>test</id>" +
                          "    <dependencies>" +
                          "      <dependency>" +
                          "        <groupId>junit</groupId>" +
                          "        <artifactId>junit</artifactId>" +
                          "        <version>4.0</version>" +
                          "      </dependency>" +
                          "    </dependencies>" +
                          "  </profile>" +
                          "</profiles>");

    createModulePom("m2", "<groupId>test</groupId>" +
                          "<artifactId>m2</artifactId>" +
                          "<version>1</version>" +

                          "<dependencies>" +
                          "  <dependency>" +
                          "    <groupId>test</groupId>" +
                          "    <artifactId>m1</artifactId>" +
                          "    <version>1</version>" +
                          "  </dependency>" +
                          "</dependencies>");

    importProjectWithProfiles("test");
    assertModuleLibDeps("m2", "Maven: junit:junit:4.0");
  }
}
