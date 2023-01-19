// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import com.intellij.execution.configurations.JavaParameters;
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.scopes.LibraryRuntimeClasspathScope;
import com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.LibraryScopeCache;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.ArtifactsDownloadingTestCase;
import org.jetbrains.idea.maven.importing.MavenLegacyModuleImporter;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class MavenClasspathsAndSearchScopesTest extends MavenMultiVersionImportingTestCase {
  private enum Type {PRODUCTION, TESTS}

  private enum Scope {COMPILE, RUNTIME, MODULE}

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    createProjectSubDirs("m1/src/main/java",
                         "m1/src/test/java",

                         "m2/src/main/java",
                         "m2/src/test/java",

                         "m3/src/main/java",
                         "m3/src/test/java",

                         "m4/src/main/java",
                         "m4/src/test/java");
  }

  @Test
  public void testConfiguringModuleDependencies() throws Exception {
    VirtualFile m1 = createModulePom("m1", """
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
      """);

    VirtualFile m2 = createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m3</artifactId>
          <version>1</version>
        </dependency>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m4</artifactId>
          <version>1</version>
          <optional>true</optional>
        </dependency>
      </dependencies>
      """);

    VirtualFile m3 = createModulePom("m3", """
      <groupId>test</groupId>
      <artifactId>m3</artifactId>
      <version>1</version>
      """);

    VirtualFile m4 = createModulePom("m4", """
      <groupId>test</groupId>
      <artifactId>m4</artifactId>
      <version>1</version>
      """);

    importProjects(m1, m2, m3, m4);
    assertModules("m1", "m2", "m3", "m4");

    assertModuleModuleDeps("m1", "m2", "m3");
    assertModuleModuleDeps("m2", "m3", "m4");

    setupJdkForModules("m1", "m2", "m3", "m4");

    assertModuleScopes("m1", "m2", "m3", "m4");

    assertAllProductionSearchScope("m1",
                                   getProjectPath() + "/m1/src/main/java",
                                   getProjectPath() + "/m2/src/main/java",
                                   getProjectPath() + "/m3/src/main/java");
    assertAllTestsSearchScope("m1",
                              getProjectPath() + "/m1/src/main/java",
                              getProjectPath() + "/m1/src/test/java",
                              getProjectPath() + "/m2/src/main/java",
                              getProjectPath() + "/m3/src/main/java");

    assertAllProductionClasspath("m1",
                                 getProjectPath() + "/m1/target/classes",
                                 getProjectPath() + "/m2/target/classes",
                                 getProjectPath() + "/m3/target/classes");
    assertAllTestsClasspath("m1",
                            getProjectPath() + "/m1/target/test-classes",
                            getProjectPath() + "/m1/target/classes",
                            getProjectPath() + "/m2/target/classes",
                            getProjectPath() + "/m3/target/classes");

    assertAllProductionSearchScope("m2",
                                   getProjectPath() + "/m2/src/main/java",
                                   getProjectPath() + "/m3/src/main/java",
                                   getProjectPath() + "/m4/src/main/java");
    assertAllTestsSearchScope("m2",
                              getProjectPath() + "/m2/src/main/java",
                              getProjectPath() + "/m2/src/test/java",
                              getProjectPath() + "/m3/src/main/java",
                              getProjectPath() + "/m4/src/main/java");

    assertAllProductionClasspath("m2",
                                 getProjectPath() + "/m2/target/classes",
                                 getProjectPath() + "/m3/target/classes",
                                 getProjectPath() + "/m4/target/classes");
    assertAllTestsClasspath("m2",
                            getProjectPath() + "/m2/target/test-classes",
                            getProjectPath() + "/m2/target/classes",
                            getProjectPath() + "/m3/target/classes",
                            getProjectPath() + "/m4/target/classes");
  }

  @Test
  public void testDoNotIncludeTargetDirectoriesOfModuleDependenciesToLibraryClassesRoots() {
    VirtualFile m = createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>dep</artifactId>
          <version>1</version>
        </dependency>
      </dependencies>
      """);

    VirtualFile dep = createModulePom("dep", """
      <groupId>test</groupId>
      <artifactId>dep</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>junit</groupId>
          <artifactId>junit</artifactId>
          <version>4.0</version>
        </dependency>
      </dependencies>
      """);

    importProjects(m, dep);
    assertModules("m", "dep");

    assertModuleModuleDeps("m", "dep");

    setupJdkForModules("m", "dep");

    createOutputDirectories();
    Module module = getModule("m");
    VirtualFile[] jdkRoots = ModuleRootManager.getInstance(module).getSdk().getRootProvider().getFiles(OrderRootType.CLASSES);
    VirtualFile[] junitRoots = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject).getLibraryByName("Maven: junit:junit:4.0")
      .getFiles(OrderRootType.CLASSES);
    assertOrderedEquals(OrderEnumerator.orderEntries(module).getAllLibrariesAndSdkClassesRoots(),
                        ArrayUtil.mergeArrays(jdkRoots, junitRoots));
  }

  @Test
  public void testDoNotIncludeTestClassesWhenConfiguringModuleDependenciesForProductionCode() throws Exception {
    VirtualFile m1 = createModulePom("m1", """
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
      """);

    VirtualFile m2 = createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """);

    importProjects(m1, m2);
    assertModules("m1", "m2");
    assertModuleModuleDeps("m1", "m2");

    setupJdkForModules("m1", "m2");

    assertModuleScopes("m1", "m2");

    assertAllProductionSearchScope("m1",
                                   getProjectPath() + "/m1/src/main/java",
                                   getProjectPath() + "/m2/src/main/java");
    assertAllProductionClasspath("m1",
                                 getProjectPath() + "/m1/target/classes",
                                 getProjectPath() + "/m2/target/classes");

    assertAllProductionSearchScope("m2",
                                   getProjectPath() + "/m2/src/main/java");
    assertAllProductionClasspath("m2",
                                 getProjectPath() + "/m2/target/classes");
  }

  @Test
  public void testConfiguringModuleDependenciesOnTestJar() throws Exception {
    VirtualFile m1 = createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m2</artifactId>
          <version>1</version>
          <type>test-jar</type>
        </dependency>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m3</artifactId>
          <version>1</version>
          <classifier>tests</classifier>
        </dependency>
      </dependencies>
      """);

    VirtualFile m2 = createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """);
    VirtualFile m3 = createModulePom("m3", """
      <groupId>test</groupId>
      <artifactId>m3</artifactId>
      <version>1</version>
      """);

    importProjects(m1, m2, m3);
    assertModules("m1", "m2", "m3");

    setupJdkForModules("m1", "m2", "m3");

    assertModuleScopes("m1", "m2", "m3");

    assertAllProductionSearchScope("m1",
                                   getProjectPath() + "/m1/src/main/java",
                                   getProjectPath() + "/m2/src/test/java",
                                   getProjectPath() + "/m3/src/test/java");
    assertAllTestsSearchScope("m1",
                              getProjectPath() + "/m1/src/main/java",
                              getProjectPath() + "/m1/src/test/java",
                              getProjectPath() + "/m2/src/test/java",
                              getProjectPath() + "/m3/src/test/java");

    assertAllProductionClasspath("m1",
                                 getProjectPath() + "/m1/target/classes",
                                 getProjectPath() + "/m2/target/test-classes",
                                 getProjectPath() + "/m3/target/test-classes");
    assertAllTestsClasspath("m1",
                            getProjectPath() + "/m1/target/test-classes",
                            getProjectPath() + "/m1/target/classes",
                            getProjectPath() + "/m2/target/test-classes",
                            getProjectPath() + "/m3/target/test-classes");
  }

  @Test
  public void testConfiguringModuleDependenciesOnTestJarWithTestScope() throws Exception {
    VirtualFile m1 = createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m2</artifactId>
          <version>1</version>
          <type>test-jar</type>
          <scope>test</scope>
        </dependency>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m3</artifactId>
          <version>1</version>
          <classifier>tests</classifier>
          <scope>test</scope>
        </dependency>
      </dependencies>
      """);

    VirtualFile m2 = createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """);
    VirtualFile m3 = createModulePom("m3", """
      <groupId>test</groupId>
      <artifactId>m3</artifactId>
      <version>1</version>
      """);

    importProjects(m1, m2, m3);
    assertModules("m1", "m2", "m3");

    setupJdkForModules("m1", "m2", "m3");

    assertModuleScopes("m1", "m2", "m3");

    assertAllProductionSearchScope("m1",
                                   getProjectPath() + "/m1/src/main/java");
    assertAllTestsSearchScope("m1",
                              getProjectPath() + "/m1/src/main/java",
                              getProjectPath() + "/m1/src/test/java",
                              getProjectPath() + "/m2/src/test/java",
                              getProjectPath() + "/m3/src/test/java");

    assertAllProductionClasspath("m1",
                                 getProjectPath() + "/m1/target/classes");
    assertAllTestsClasspath("m1",
                            getProjectPath() + "/m1/target/test-classes",
                            getProjectPath() + "/m1/target/classes",
                            getProjectPath() + "/m2/target/test-classes",
                            getProjectPath() + "/m3/target/test-classes");
  }

  @Test
  public void testConfiguringModuleDependenciesOnBothNormalAndTestJar() throws Exception {
    VirtualFile m1 = createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m2</artifactId>
          <version>1</version>
        </dependency>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m2</artifactId>
          <version>1</version>
          <type>test-jar</type>
        </dependency>
      </dependencies>
      """);

    VirtualFile m2 = createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """);

    importProjects(m1, m2);
    assertModules("m1", "m2");

    setupJdkForModules("m1", "m2");

    assertModuleScopes("m1", "m2");

    assertAllProductionSearchScope("m1",
                                   getProjectPath() + "/m1/src/main/java",
                                   getProjectPath() + "/m2/src/main/java",
                                   getProjectPath() + "/m2/src/test/java");
    assertAllTestsSearchScope("m1",
                              getProjectPath() + "/m1/src/main/java",
                              getProjectPath() + "/m1/src/test/java",
                              getProjectPath() + "/m2/src/main/java",
                              getProjectPath() + "/m2/src/test/java");


    assertAllProductionClasspath("m1",
                                 getProjectPath() + "/m1/target/classes",
                                 getProjectPath() + "/m2/target/classes",
                                 getProjectPath() + "/m2/target/test-classes");

    assertAllTestsClasspath("m1",
                            getProjectPath() + "/m1/target/test-classes",
                            getProjectPath() + "/m1/target/classes",
                            getProjectPath() + "/m2/target/classes",
                            getProjectPath() + "/m2/target/test-classes");
  }

  @Test
  public void testConfiguringModuleDependenciesOnNormalAndTestJarWithTestScope() throws Exception {
    VirtualFile m1 = createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m2</artifactId>
          <version>1</version>
        </dependency>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m2</artifactId>
          <version>1</version>
          <type>test-jar</type>
          <scope>test</scope>
        </dependency>
      </dependencies>
      """);

    VirtualFile m2 = createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """);

    importProjects(m1, m2);
    assertModules("m1", "m2");

    setupJdkForModules("m1", "m2");

    assertModuleScopes("m1", "m2");

    assertAllProductionSearchScope("m1",
                                   getProjectPath() + "/m1/src/main/java",
                                   getProjectPath() + "/m2/src/main/java");
    assertAllTestsSearchScope("m1",
                              getProjectPath() + "/m1/src/main/java",
                              getProjectPath() + "/m1/src/test/java",
                              getProjectPath() + "/m2/src/main/java",
                              getProjectPath() + "/m2/src/test/java");

    assertAllProductionClasspath("m1",
                                 getProjectPath() + "/m1/target/classes",
                                 getProjectPath() + "/m2/target/classes");

    assertAllTestsClasspath("m1",
                            getProjectPath() + "/m1/target/test-classes",
                            getProjectPath() + "/m1/target/classes",
                            getProjectPath() + "/m2/target/classes",
                            getProjectPath() + "/m2/target/test-classes");
  }

  @Test
  public void testOptionalLibraryDependencies() throws Exception {
    createRepositoryFile("jmock/jmock/1.0/jmock-1.0.jar");
    VirtualFile m1 = createModulePom("m1", """
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
      """);

    VirtualFile m2 = createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>jmock</groupId>
          <artifactId>jmock</artifactId>
          <version>1.0</version>
        </dependency>
        <dependency>
          <groupId>junit</groupId>
          <artifactId>junit</artifactId>
          <version>4.0</version>
          <optional>true</optional>
        </dependency>
      </dependencies>
      """);

    importProjects(m1, m2);
    assertModules("m1", "m2");

    assertModuleScopes("m1", "m2");

    assertModuleModuleDeps("m1", "m2");
    assertModuleLibDeps("m1", "Maven: jmock:jmock:1.0");
    assertModuleLibDeps("m2", "Maven: jmock:jmock:1.0", "Maven: junit:junit:4.0");

    setupJdkForModules("m1", "m2");

    assertAllProductionSearchScope("m1",
                                   getProjectPath() + "/m1/src/main/java",
                                   getProjectPath() + "/m2/src/main/java",
                                   getRepositoryPath() + "/jmock/jmock/1.0/jmock-1.0.jar");
    assertAllTestsSearchScope("m1",
                              getProjectPath() + "/m1/src/main/java",
                              getProjectPath() + "/m1/src/test/java",
                              getProjectPath() + "/m2/src/main/java",
                              getRepositoryPath() + "/jmock/jmock/1.0/jmock-1.0.jar");

    assertAllProductionClasspath("m1",
                                 getProjectPath() + "/m1/target/classes",
                                 getProjectPath() + "/m2/target/classes",
                                 getRepositoryPath() + "/jmock/jmock/1.0/jmock-1.0.jar");
    assertAllTestsClasspath("m1",
                            getProjectPath() + "/m1/target/test-classes",
                            getProjectPath() + "/m1/target/classes",
                            getProjectPath() + "/m2/target/classes",
                            getRepositoryPath() + "/jmock/jmock/1.0/jmock-1.0.jar");

    assertAllProductionSearchScope("m2",
                                   getProjectPath() + "/m2/src/main/java",
                                   getRepositoryPath() + "/jmock/jmock/1.0/jmock-1.0.jar",
                                   getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar");
    assertAllTestsSearchScope("m2",
                              getProjectPath() + "/m2/src/main/java",
                              getProjectPath() + "/m2/src/test/java",
                              getRepositoryPath() + "/jmock/jmock/1.0/jmock-1.0.jar",
                              getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar");

    assertAllProductionClasspath("m2",
                                 getProjectPath() + "/m2/target/classes",
                                 getRepositoryPath() + "/jmock/jmock/1.0/jmock-1.0.jar",
                                 getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar");
    assertAllTestsClasspath("m2",
                            getProjectPath() + "/m2/target/test-classes",
                            getProjectPath() + "/m2/target/classes",
                            getRepositoryPath() + "/jmock/jmock/1.0/jmock-1.0.jar",
                            getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar");
  }

  @Test
  public void testProvidedAndTestDependencies() throws Exception {
    createRepositoryFile("jmock/jmock/4.0/jmock-4.0.jar");
    VirtualFile m1 = createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m2</artifactId>
          <version>1</version>
          <scope>provided</scope>
        </dependency>
        <dependency>
          <groupId>junit</groupId>
          <artifactId>junit</artifactId>
          <version>4.0</version>
          <scope>provided</scope>
        </dependency>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m3</artifactId>
          <version>1</version>
          <scope>test</scope>
        </dependency>
        <dependency>
          <groupId>jmock</groupId>
          <artifactId>jmock</artifactId>
          <version>4.0</version>
          <scope>test</scope>
        </dependency>
      </dependencies>
      """);

    VirtualFile m2 = createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """);

    VirtualFile m3 = createModulePom("m3", """
      <groupId>test</groupId>
      <artifactId>m3</artifactId>
      <version>1</version>
      """);

    importProjects(m1, m2, m3);
    assertModules("m1", "m2", "m3");

    setupJdkForModules("m1", "m2", "m3");

    assertModuleScopes("m1", "m2", "m3");

    assertCompileProductionSearchScope("m1",
                                       getProjectPath() + "/m1/src/main/java",
                                       getProjectPath() + "/m2/src/main/java",
                                       getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar");
    assertRuntimeProductionSearchScope("m1",
                                       getProjectPath() + "/m1/src/main/java",
                                       getProjectPath() + "/m2/src/main/java",
                                       getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar");
    assertAllTestsSearchScope("m1",
                              getProjectPath() + "/m1/src/main/java",
                              getProjectPath() + "/m1/src/test/java",
                              getProjectPath() + "/m2/src/main/java",
                              getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar",
                              getProjectPath() + "/m3/src/main/java",
                              getRepositoryPath() + "/jmock/jmock/4.0/jmock-4.0.jar");

    assertCompileProductionClasspath("m1",
                                     getProjectPath() + "/m1/target/classes",
                                     getProjectPath() + "/m2/target/classes",
                                     getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar");
    assertRuntimeProductionClasspath("m1",
                                     getProjectPath() + "/m1/target/classes");
    assertAllTestsClasspath("m1",
                            getProjectPath() + "/m1/target/test-classes",
                            getProjectPath() + "/m1/target/classes",
                            getProjectPath() + "/m2/target/classes",
                            getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar",
                            getProjectPath() + "/m3/target/classes",
                            getRepositoryPath() + "/jmock/jmock/4.0/jmock-4.0.jar");
  }

  @Test
  public void testRuntimeDependency() throws Exception {
    createRepositoryFile("jmock/jmock/4.0/jmock-4.0.jar");
    VirtualFile m1 = createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m2</artifactId>
          <version>1</version>
          <scope>runtime</scope>
        </dependency>
        <dependency>
          <groupId>junit</groupId>
          <artifactId>junit</artifactId>
          <version>4.0</version>
          <scope>runtime</scope>
        </dependency>
      </dependencies>
      """);

    VirtualFile m2 = createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """);

    importProjects(m1, m2);
    assertModules("m1", "m2");

    setupJdkForModules("m1", "m2");

    assertModuleScopes("m1", "m2");

    assertCompileProductionSearchScope("m1",
                                       getProjectPath() + "/m1/src/main/java");
    assertRuntimeProductionSearchScope("m1",
                                       getProjectPath() + "/m1/src/main/java",
                                       getProjectPath() + "/m2/src/main/java",
                                       getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar");
    assertAllTestsSearchScope("m1",
                              getProjectPath() + "/m1/src/main/java",
                              getProjectPath() + "/m1/src/test/java",
                              getProjectPath() + "/m2/src/main/java",
                              getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar");

    assertCompileProductionClasspath("m1",
                                     getProjectPath() + "/m1/target/classes");

    assertRuntimeProductionClasspath("m1",
                                     getProjectPath() + "/m1/target/classes",
                                     getProjectPath() + "/m2/target/classes",
                                     getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar");

    assertAllTestsClasspath("m1",
                            getProjectPath() + "/m1/target/test-classes",
                            getProjectPath() + "/m1/target/classes",
                            getProjectPath() + "/m2/target/classes",
                            getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar");
  }

  @Test
  public void testDoNotIncludeProvidedAndTestTransitiveDependencies() throws Exception {
    createRepositoryFile("jmock/jmock/1.0/jmock-1.0.jar");
    VirtualFile m1 = createModulePom("m1", """
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
      """);

    VirtualFile m2 = createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>jmock</groupId>
          <artifactId>jmock</artifactId>
          <version>1.0</version>
          <scope>provided</scope>
        </dependency>
        <dependency>
          <groupId>junit</groupId>
          <artifactId>junit</artifactId>
          <version>4.0</version>
          <scope>test</scope>
        </dependency>
      </dependencies>
      """);

    importProjects(m1, m2);
    assertModules("m1", "m2");

    assertModuleModuleDeps("m1", "m2");
    assertModuleLibDeps("m1");
    assertModuleLibDeps("m2", "Maven: jmock:jmock:1.0", "Maven: junit:junit:4.0");

    setupJdkForModules("m1", "m2");

    assertModuleScopes("m1", "m2");

    assertAllProductionSearchScope("m1",
                                   getProjectPath() + "/m1/src/main/java",
                                   getProjectPath() + "/m2/src/main/java");
    assertAllTestsSearchScope("m1",
                              getProjectPath() + "/m1/src/main/java",
                              getProjectPath() + "/m1/src/test/java",
                              getProjectPath() + "/m2/src/main/java");

    assertAllProductionClasspath("m1",
                                 getProjectPath() + "/m1/target/classes",
                                 getProjectPath() + "/m2/target/classes");
    assertAllTestsClasspath("m1",
                            getProjectPath() + "/m1/target/test-classes",
                            getProjectPath() + "/m1/target/classes",
                            getProjectPath() + "/m2/target/classes");


    assertCompileProductionSearchScope("m2",
                                       getProjectPath() + "/m2/src/main/java",
                                       getRepositoryPath() + "/jmock/jmock/1.0/jmock-1.0.jar");
    assertRuntimeProductionSearchScope("m2",
                                       getProjectPath() + "/m2/src/main/java",
                                       getRepositoryPath() + "/jmock/jmock/1.0/jmock-1.0.jar");
    assertAllTestsSearchScope("m2",
                              getProjectPath() + "/m2/src/main/java",
                              getProjectPath() + "/m2/src/test/java",
                              getRepositoryPath() + "/jmock/jmock/1.0/jmock-1.0.jar",
                              getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar");

    assertCompileProductionClasspath("m2",
                                     getProjectPath() + "/m2/target/classes",
                                     getRepositoryPath() + "/jmock/jmock/1.0/jmock-1.0.jar");
    assertRuntimeProductionClasspath("m2",
                                     getProjectPath() + "/m2/target/classes");
    assertAllTestsClasspath("m2",
                            getProjectPath() + "/m2/target/test-classes",
                            getProjectPath() + "/m2/target/classes",
                            getRepositoryPath() + "/jmock/jmock/1.0/jmock-1.0.jar",
                            getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar");
  }

  @Test
  public void testLibraryScopeForTwoDependentModules() {
    VirtualFile m1 = createModulePom("m1", """
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
      """);

    VirtualFile m2 = createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
          <dependencies>
              <dependency>
                  <groupId>junit</groupId>
                  <artifactId>junit</artifactId>
                  <version>4.0</version>
                  <scope>provided</scope>
              </dependency>
          </dependencies>
      """);
    importProjects(m1, m2);
    assertModules("m1", "m2");

    Module m1m = ModuleManager.getInstance(myProject).findModuleByName("m1");
    List<OrderEntry> modules1 = new ArrayList<>();
    ModuleRootManager.getInstance(m1m).orderEntries().withoutSdk().withoutModuleSourceEntries().forEach(
      new CommonProcessors.CollectProcessor<>(modules1));
    GlobalSearchScope scope1 = LibraryScopeCache.getInstance(myProject).getLibraryScope(modules1);
    assertSearchScope(scope1,
                      getProjectPath() + "/m1/src/main/java",
                      getProjectPath() + "/m1/src/test/java",
                      getProjectPath() + "/m2/src/main/java",
                      getProjectPath() + "/m2/src/test/java"
                      );

    String libraryPath = getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar";
    String librarySrcPath = getRepositoryPath() + "/junit/junit/4.0/junit-4.0-sources.jar";
    Module m2m = ModuleManager.getInstance(myProject).findModuleByName("m2");
    List<OrderEntry> modules2 = new ArrayList<>();
    ModuleRootManager.getInstance(m2m).orderEntries().withoutSdk().withoutModuleSourceEntries().forEach(
      new CommonProcessors.CollectProcessor<>(modules2));
    GlobalSearchScope scope2 = LibraryScopeCache.getInstance(myProject).getLibraryScope(modules2);

    List<String> expectedPaths =
      new ArrayList<>(List.of(getProjectPath() + "/m2/src/main/java", getProjectPath() + "/m2/src/test/java", libraryPath));
    if (new File(librarySrcPath).exists()) {
      expectedPaths.add(librarySrcPath);
    }
    assertSearchScope(scope2, ArrayUtilRt.toStringArray(expectedPaths));
  }

  @Test
  public void testDoNotIncludeConflictingTransitiveDependenciesInTheClasspath() throws Exception {
    VirtualFile m1 = createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m2</artifactId>
          <version>1</version>
        </dependency>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m3</artifactId>
          <version>1</version>
        </dependency>
      </dependencies>
      """);

    VirtualFile m2 = createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>junit</groupId>
          <artifactId>junit</artifactId>
          <version>4.0</version>
        </dependency>
      </dependencies>
      """);

    VirtualFile m3 = createModulePom("m3", """
      <groupId>test</groupId>
      <artifactId>m3</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>junit</groupId>
          <artifactId>junit</artifactId>
          <version>4.5</version>
        </dependency>
      </dependencies>
      """);

    importProjects(m1, m2, m3);
    assertModules("m1", "m2", "m3");

    assertModuleModuleDeps("m1", "m2", "m3");
    assertModuleLibDeps("m1", "Maven: junit:junit:4.0");

    setupJdkForModules("m1", "m2", "m3");

    assertModuleScopes("m1", "m2", "m3");

    assertAllTestsSearchScope("m1",
                              getProjectPath() + "/m1/src/main/java",
                              getProjectPath() + "/m1/src/test/java",
                              getProjectPath() + "/m2/src/main/java",
                              getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar",
                              getProjectPath() + "/m3/src/main/java");

    assertAllTestsClasspath("m1",
                            getProjectPath() + "/m1/target/test-classes",
                            getProjectPath() + "/m1/target/classes",
                            getProjectPath() + "/m2/target/classes",
                            getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar",
                            getProjectPath() + "/m3/target/classes");
  }

  public void _testAdditionalClasspathElementsInTests() throws Exception {
    File iof1 = new File(myDir, "foo/bar1");
    File iof2 = new File(myDir, "foo/bar2");
    iof1.mkdirs();
    iof2.mkdirs();
    VirtualFile f1 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(iof1);
    VirtualFile f2 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(iof2);
    VirtualFile f3 = createProjectSubDir("m1/foo/bar3");

    VirtualFile m1 = createModulePom("m1", "<groupId>test</groupId>\n" +
                                           "<artifactId>m1</artifactId>\n" +
                                           "<version>1</version>\n" +

                                           "<dependencies>\n" +
                                           "  <dependency>\n" +
                                           "    <groupId>junit</groupId>\n" +
                                           "    <artifactId>junit</artifactId>\n" +
                                           "    <version>4.0</version>\n" +
                                           "  </dependency>\n" +
                                           "</dependencies>\n" +

                                           "<build>\n" +
                                           "  <plugins>\n" +
                                           "    <plugin>\n" +
                                           "      <groupId>org.apache.maven.plugins</groupId>\n" +
                                           "      <artifactId>maven-surefire-plugin</artifactId>\n" +
                                           "      <version>2.5</version>\n" +
                                           "      <configuration>\n" +
                                           "        <additionalClasspathElements>\n" +
                                           "          <additionalClasspathElement>\n" + f1.getPath() + "</additionalClasspathElement>\n" +
                                           "          <additionalClasspathElement>\n" + f2.getPath() + "</additionalClasspathElement>\n" +
                                           "          <additionalClasspathElement>${project.basedir}/foo/bar3</additionalClasspathElement>\n" +
                                           "        </additionalClasspathElements>\n" +
                                           "      </configuration>\n" +
                                           "    </plugin>\n" +
                                           "  </plugins>\n" +
                                           "</build>\n");

    importProjects(m1);
    assertModules("m1");

    assertModuleModuleDeps("m1");
    assertModuleLibDeps("m1", "Maven: junit:junit:4.0", MavenLegacyModuleImporter.SUREFIRE_PLUGIN_LIBRARY_NAME);

    setupJdkForModules("m1");

    //assertModuleSearchScope("m1",
    //                        getProjectPath() + "/m1/src/main/java",
    //                        getProjectPath() + "/m1/src/test/java",
    //                        f1.getPath(),
    //                        f2.getPath());

    assertAllProductionSearchScope("m1",
                                   getProjectPath() + "/m1/src/main/java",
                                   getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar");
    assertAllTestsSearchScope("m1",
                              getProjectPath() + "/m1/src/main/java",
                              getProjectPath() + "/m1/src/test/java",
                              getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar",
                              f1.getPath(),
                              f2.getPath(),
                              f3.getPath());

    assertAllProductionClasspath("m1",
                                 getProjectPath() + "/m1/target/classes",
                                 getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar");
    assertAllTestsClasspath("m1",
                            getProjectPath() + "/m1/target/test-classes",
                            getProjectPath() + "/m1/target/classes",
                            getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar",
                            f1.getPath(),
                            f2.getPath(),
                            f3.getPath());
  }

  @Test
  public void testDoNotChangeClasspathForRegularModules() throws Exception {
    VirtualFile m1 = createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m2</artifactId>
          <version>1</version>
          <scope>runtime</scope>
          <optional>true</optional>
        </dependency>
        <dependency>
          <groupId>junit</groupId>
          <artifactId>junit</artifactId>
          <version>4.0</version>
          <scope>provided</scope>
          <optional>true</optional>
        </dependency>
      </dependencies>
      """);

    VirtualFile m2 = createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """);

    importProjects(m1, m2);
    assertModules("m1", "m2");

    final Module user = createModule("user");

    WriteCommandAction.writeCommandAction(myProject).run(() -> {
      ModuleRootModificationUtil.addDependency(user, getModule("m1"));
      VirtualFile out = user.getModuleFile().getParent().createChildDirectory(this, "output");
      VirtualFile testOut = user.getModuleFile().getParent().createChildDirectory(this, "test-output");
      PsiTestUtil.setCompilerOutputPath(user, out.getUrl(), false);
      PsiTestUtil.setCompilerOutputPath(user, testOut.getUrl(), true);
    });


    assertModuleModuleDeps("m1", "m2");
    assertModuleLibDeps("m1", "Maven: junit:junit:4.0");

    assertModuleModuleDeps("user", "m1");
    assertModuleLibDeps("user");

    setupJdkForModules("m1", "m2", "user");

    // todo check search scopes
    assertModuleScopes("m1", "m2");

    assertCompileProductionClasspath("user",
                                     getProjectPath() + "/user/output",
                                     getProjectPath() + "/m1/target/classes",
                                     getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar");

    assertRuntimeProductionClasspath("user",
                                     getProjectPath() + "/user/output",
                                     getProjectPath() + "/m1/target/classes",
                                     getProjectPath() + "/m2/target/classes");

    assertCompileTestsClasspath("user",
                                getProjectPath() + "/user/test-output",
                                getProjectPath() + "/user/output",
                                getProjectPath() + "/m1/target/test-classes",
                                getProjectPath() + "/m1/target/classes",
                                getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar");

    assertRuntimeTestsClasspath("user",
                                getProjectPath() + "/user/test-output",
                                getProjectPath() + "/user/output",
                                getProjectPath() + "/m1/target/test-classes",
                                getProjectPath() + "/m1/target/classes",
                                getProjectPath() + "/m2/target/test-classes",
                                getProjectPath() + "/m2/target/classes",
                                getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar");

    assertCompileProductionClasspath("m1",
                                     getProjectPath() + "/m1/target/classes",
                                     getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar");
    assertRuntimeProductionClasspath("m1",
                                     getProjectPath() + "/m1/target/classes",
                                     getProjectPath() + "/m2/target/classes");
    assertAllTestsClasspath("m1",
                            getProjectPath() + "/m1/target/test-classes",
                            getProjectPath() + "/m1/target/classes",
                            getProjectPath() + "/m2/target/classes",
                            getRepositoryPath() + "/junit/junit/4.0/junit-4.0.jar");
  }

  @Test
  public void testDirIndexOrderEntriesTransitiveCompileScope() throws IOException {
    List<Module> modules = setupDirIndexTestModulesWithScope("compile");
    checkDirIndexTestModulesWithCompileOrRuntimeScope(modules);
  }

  @Test
  public void testDirIndexOrderEntriesTransitiveRuntimeScope() throws IOException {
    List<Module> modules = setupDirIndexTestModulesWithScope("runtime");
    checkDirIndexTestModulesWithCompileOrRuntimeScope(modules);
  }

  // Creates a Maven dependency graph for testing DirectoryIndex#getOrderEntries.
  private List<Module> setupDirIndexTestModulesWithScope(String scope) throws IOException {
    createRepositoryFile("jmock/jmock/1.0/jmock-1.0.jar");
    // Dependency graph:
    //               m4
    //               |
    //               v
    //   m1 -> m2 -> m3-|
    //         |----------> jmock
    //         v
    //         m5 -> m6
    // Dependencies are set up to be under the given scope, except that jmock is under a test scope,
    // and the m5 -> m6 dep is always under a compile scope.
    VirtualFile m1 = createModulePom("m1", "<groupId>test</groupId>\n" +
                                           "<artifactId>m1</artifactId>\n" +
                                           "<version>1</version>\n" +
                                           "<dependencies>\n" +
                                           "  <dependency>\n" +
                                           "    <groupId>test</groupId>\n" +
                                           "    <artifactId>m2</artifactId>\n" +
                                           "    <version>1</version>\n" +
                                           "    <scope>\n" + scope + "</scope>\n" +
                                           "  </dependency>\n" +
                                           "</dependencies>\n");
    VirtualFile m2 = createModulePom("m2", "<groupId>test</groupId>\n" +
                                           "<artifactId>m2</artifactId>\n" +
                                           "<version>1</version>\n" +
                                           "<dependencies>\n" +
                                           "  <dependency>\n" +
                                           "    <groupId>test</groupId>\n" +
                                           "    <artifactId>m3</artifactId>\n" +
                                           "    <version>1</version>\n" +
                                           "    <scope>\n" + scope + "</scope>\n" +
                                           "  </dependency>\n" +
                                           "  <dependency>\n" +
                                           "    <groupId>jmock</groupId>\n" +
                                           "    <artifactId>jmock</artifactId>\n" +
                                           "    <version>1.0</version>\n" +
                                           "    <scope>test</scope>\n" +
                                           "  </dependency>\n" +
                                           "  <dependency>\n" +
                                           "    <groupId>test</groupId>\n" +
                                           "    <artifactId>m5</artifactId>\n" +
                                           "    <version>1</version>\n" +
                                           "    <scope>\n" + scope + "</scope>\n" +
                                           "  </dependency>\n" +
                                           "</dependencies>\n");
    VirtualFile m3 = createModulePom("m3", """
      <groupId>test</groupId>
      <artifactId>m3</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>jmock</groupId>
          <artifactId>jmock</artifactId>
          <version>1.0</version>
          <scope>test</scope>
        </dependency>
      </dependencies>
      """);
    VirtualFile m4 = createModulePom("m4", "<groupId>test</groupId>\n" +
                                           "<artifactId>m4</artifactId>\n" +
                                           "<version>1</version>\n" +
                                           "<dependencies>\n" +
                                           "  <dependency>\n" +
                                           "    <groupId>test</groupId>\n" +
                                           "    <artifactId>m3</artifactId>\n" +
                                           "    <version>1</version>\n" +
                                           "    <scope>\n" + scope + "</scope>\n" +
                                           "  </dependency>\n" +
                                           "</dependencies>\n");
    // The default setupInWriteAction only creates directories up to m4.
    // Create directories for m5 and m6 which we will use for this test.
    WriteCommandAction.writeCommandAction(myProject).run(() -> createProjectSubDirs("m5/src/main/java",
                                                                                    "m5/src/test/java",

                                                                                    "m6/src/main/java",
                                                                                    "m6/src/test/java"));
    VirtualFile m5 = createModulePom("m5", """
      <groupId>test</groupId>
      <artifactId>m5</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m6</artifactId>
          <version>1</version>
          <scope>compile</scope>
        </dependency>
      </dependencies>
      """);
    VirtualFile m6 = createModulePom("m6", """
      <groupId>test</groupId>
      <artifactId>m6</artifactId>
      <version>1</version>
      """);
    importProjects(m1, m2, m3, m4, m5, m6);
    assertModules("m1", "m2", "m3", "m4", "m5", "m6");
    createOutputDirectories();

    return Arrays.asList(getModule("m1"), getModule("m2"), getModule("m3"), getModule("m4"),
                         getModule("m5"), getModule("m6"));
  }

  // Checks that the DirectoryIndex#getOrderEntries() returns the expected values
  // for the dependency graph set up by setupDirIndexTestModulesWithScope().
  // The result is the same for "compile" and "runtime" scopes.
  private void checkDirIndexTestModulesWithCompileOrRuntimeScope(List<Module> modules) {
    assertEquals(6, modules.size());
    ProjectFileIndex index = ProjectFileIndex.getInstance(myProject);
    VirtualFile m3JavaDir = VfsUtil.findFileByIoFile(new File(getProjectPath(), "m3/src/main/java"), true);
    assertNotNull(m3JavaDir);
    // Should be: m1 -> m3, m2 -> m3, m3 -> source, and m4 -> m3
    List<OrderEntry> orderEntries = index.getOrderEntriesForFile(m3JavaDir);
    assertEquals(4, orderEntries.size());
    List<Module> ownerModules = orderEntriesToOwnerModules(orderEntries);
    List<Module> depModules = orderEntriesToDepModules(orderEntries);
    assertOrderedElementsAreEqual(ownerModules,
                                  Arrays.asList(modules.get(0), modules.get(1), modules.get(2), modules.get(3)));
    assertOrderedElementsAreEqual(depModules,
                                  Arrays.asList(modules.get(2), modules.get(2), null, modules.get(2)));
    // m3 -> source
    OrderEntry m3E2 = orderEntries.get(2);
    assertInstanceOf(m3E2, ModuleSourceOrderEntry.class);

    VirtualFile m6javaDir = VfsUtil.findFileByIoFile(new File(getProjectPath(), "m6/src/main/java"), true);
    assertNotNull(m6javaDir);
    // Should be m1 -> m6, m2 -> m6, m5 -> m6, m6 -> source
    List<OrderEntry> m6OrderEntries = index.getOrderEntriesForFile(m6javaDir);
    assertEquals(4, m6OrderEntries.size());
    List<Module> m6OwnerModules = orderEntriesToOwnerModules(m6OrderEntries);
    List<Module> m6DepModules = orderEntriesToDepModules(m6OrderEntries);
    assertOrderedElementsAreEqual(m6OwnerModules,
                                  Arrays.asList(modules.get(0), modules.get(1), modules.get(4), modules.get(5)));
    assertOrderedElementsAreEqual(m6DepModules,
                                  Arrays.asList(modules.get(5), modules.get(5), modules.get(5), null));
    // m6 -> source
    OrderEntry m6E3 = m6OrderEntries.get(3);
    assertInstanceOf(m6E3, ModuleSourceOrderEntry.class);

    VirtualFile jmockDir = VfsUtil.findFileByIoFile(new File(getRepositoryPath(), "jmock/jmock/1.0/jmock-1.0.jar"), true);
    assertNotNull(jmockDir);
    VirtualFile jmockJar = JarFileSystem.getInstance().getJarRootForLocalFile(jmockDir);
    assertNotNull(jmockJar);
    // m2 -> jmock, m3 -> jmock
    List<OrderEntry> jmockOrderEntries = index.getOrderEntriesForFile(jmockJar);
    assertEquals(2, jmockOrderEntries.size());
    OrderEntry jmockE0 = jmockOrderEntries.get(0);
    assertEquals(modules.get(1), jmockE0.getOwnerModule());
    assertInstanceOf(jmockE0, LibraryOrderEntry.class);
    OrderEntry jmockE1 = jmockOrderEntries.get(1);
    assertEquals(modules.get(2), jmockE1.getOwnerModule());
    assertInstanceOf(jmockE1, LibraryOrderEntry.class);
  }

  @Test
  public void testDirIndexOrderEntriesTransitiveTestScope() throws IOException {
    // This test is a bit different from the above tests of compile or runtime scope,
    // because test scope does not propagate transitive dependencies.
    List<Module> modules = setupDirIndexTestModulesWithScope("test");
    assertEquals(6, modules.size());
    ProjectFileIndex index = ProjectFileIndex.getInstance(myProject);
    VirtualFile m3JavaDir = VfsUtil.findFileByIoFile(new File(getProjectPath(), "m3/src/main/java"), true);
    assertNotNull(m3JavaDir);
    // Should be no transitive deps: m2 -> m3, m3 -> source, and m4 -> m3
    List<OrderEntry> orderEntries = index.getOrderEntriesForFile(m3JavaDir);
    assertEquals(3, orderEntries.size());
    List<Module> ownerModules = orderEntriesToOwnerModules(orderEntries);
    List<Module> depModules = orderEntriesToDepModules(orderEntries);
    assertOrderedElementsAreEqual(ownerModules,
                                  Arrays.asList(modules.get(1), modules.get(2), modules.get(3)));
    assertOrderedElementsAreEqual(depModules,
                                  Arrays.asList(modules.get(2), null, modules.get(2)));
    // m3 -> source
    OrderEntry m3E1 = orderEntries.get(1);
    assertInstanceOf(m3E1, ModuleSourceOrderEntry.class);

    VirtualFile m6javaDir = VfsUtil.findFileByIoFile(new File(getProjectPath(), "m6/src/main/java"), true);
    assertNotNull(m6javaDir);
    // Still has some transitive deps because m5 -> m6 is hardcoded to be compile scope
    // m2 -> m6, m5 -> m6, m6 -> source
    List<OrderEntry> m6OrderEntries = index.getOrderEntriesForFile(m6javaDir);
    assertEquals(3, m6OrderEntries.size());
    List<Module> m6OwnerModules = orderEntriesToOwnerModules(m6OrderEntries);
    List<Module> m6DepModules = orderEntriesToDepModules(m6OrderEntries);
    assertOrderedElementsAreEqual(m6OwnerModules,
                                  Arrays.asList(modules.get(1), modules.get(4), modules.get(5)));
    assertOrderedElementsAreEqual(m6DepModules,
                                  Arrays.asList(modules.get(5), modules.get(5), null));
    // m6 -> source
    OrderEntry m6E2 = m6OrderEntries.get(2);
    assertInstanceOf(m6E2, ModuleSourceOrderEntry.class);

    VirtualFile jmockDir = VfsUtil.findFileByIoFile(new File(getRepositoryPath(), "jmock/jmock/1.0/jmock-1.0.jar"), true);
    assertNotNull(jmockDir);
    VirtualFile jmockJar = JarFileSystem.getInstance().getJarRootForLocalFile(jmockDir);
    assertNotNull(jmockJar);
    // m2 -> jmock, m3 -> jmock
    List<OrderEntry> jmockOrderEntries = index.getOrderEntriesForFile(jmockJar);
    assertEquals(2, jmockOrderEntries.size());
    OrderEntry jmockE0 = jmockOrderEntries.get(0);
    assertEquals(modules.get(1), jmockE0.getOwnerModule());
    assertInstanceOf(jmockE0, LibraryOrderEntry.class);
    OrderEntry jmockE1 = jmockOrderEntries.get(1);
    assertEquals(modules.get(2), jmockE1.getOwnerModule());
    assertInstanceOf(jmockE1, LibraryOrderEntry.class);
  }

  @Test
  public void testDirIndexOrderEntriesStartingFromRegularModule() throws IOException {
    final List<Module> modules = setupDirIndexTestModulesWithScope("compile");
    assertEquals(6, modules.size());
    final Module nonMavenM1 = createModule("nonMavenM1");
    final Module nonMavenM2 = createModule("nonMavenM2");

    WriteCommandAction.writeCommandAction(myProject).run(() -> {
      ModuleRootModificationUtil.addDependency(nonMavenM1, nonMavenM2, DependencyScope.COMPILE, true);
      ModuleRootModificationUtil.addDependency(nonMavenM2, modules.get(0), DependencyScope.COMPILE, true);
      createProjectSubDirs("nonMavenM1/src/main/java", "nonMavenM1/src/test/java",
                           "nonMavenM2/src/main/java", "nonMavenM2/src/test/java");
      VirtualFile nonMavenM1JavaDir = VfsUtil.findFileByIoFile(new File(getProjectPath(), "nonMavenM1/src/main/java"), true);
      assertNotNull(nonMavenM1JavaDir);
      PsiTestUtil.addSourceContentToRoots(nonMavenM1, nonMavenM1JavaDir);
      VirtualFile nonMavenM2JavaDir = VfsUtil.findFileByIoFile(new File(getProjectPath(), "nonMavenM2/src/main/java"), true);
      assertNotNull(nonMavenM2JavaDir);
      PsiTestUtil.addSourceContentToRoots(nonMavenM2, nonMavenM2JavaDir);
    });

    assertModuleModuleDeps("nonMavenM1", "nonMavenM2");
    assertModuleModuleDeps("nonMavenM2", "m1");
    assertModuleModuleDeps("m1", "m2", "m3", "m5", "m6");

    ProjectFileIndex index = ProjectFileIndex.getInstance(myProject);
    VirtualFile m3JavaDir = VfsUtil.findFileByIoFile(new File(getProjectPath(), "m3/src/main/java"), true);
    assertNotNull(m3JavaDir);
    // Should be: m1 -> m3, m2 -> m3, m3 -> source, and m4 -> m3
    // It doesn't trace back to nonMavenM1 and nonMavenM2.
    List<OrderEntry> orderEntries = index.getOrderEntriesForFile(m3JavaDir);
    List<Module> ownerModules = orderEntriesToOwnerModules(orderEntries);
    List<Module> depModules = orderEntriesToDepModules(orderEntries);
    assertOrderedElementsAreEqual(ownerModules,
                                  Arrays.asList(modules.get(0), modules.get(1), modules.get(2), modules.get(3)));
    assertOrderedElementsAreEqual(depModules,
                                  Arrays.asList(modules.get(2), modules.get(2), null, modules.get(2)));

    VirtualFile m6javaDir = VfsUtil.findFileByIoFile(new File(getProjectPath(), "m6/src/main/java"), true);
    assertNotNull(m6javaDir);
    // Should be m1 -> m6, m2 -> m6, m5 -> m6, m6 -> source
    List<OrderEntry> m6OrderEntries = index.getOrderEntriesForFile(m6javaDir);
    List<Module> m6OwnerModules = orderEntriesToOwnerModules(m6OrderEntries);
    List<Module> m6DepModules = orderEntriesToDepModules(m6OrderEntries);
    assertOrderedElementsAreEqual(m6OwnerModules,
                                  Arrays.asList(modules.get(0), modules.get(1), modules.get(4), modules.get(5)));
    assertOrderedElementsAreEqual(m6DepModules,
                                  Arrays.asList(modules.get(5), modules.get(5), modules.get(5), null));

    VirtualFile nonMavenM2JavaDir = VfsUtil.findFileByIoFile(new File(getProjectPath(), "nonMavenM2/src/main/java"), true);
    assertNotNull(nonMavenM2JavaDir);
    // Should be nonMavenM1 -> nonMavenM2, nonMavenM2 -> source
    List<OrderEntry> nonMavenM2JavaOrderEntries = index.getOrderEntriesForFile(nonMavenM2JavaDir);
    List<Module> nonMavenM2OwnerModules = orderEntriesToOwnerModules(nonMavenM2JavaOrderEntries);
    List<Module> nonMavenM2DepModules = orderEntriesToDepModules(nonMavenM2JavaOrderEntries);
    assertOrderedElementsAreEqual(nonMavenM2OwnerModules, Arrays.asList(nonMavenM1, nonMavenM2));
    assertOrderedElementsAreEqual(nonMavenM2DepModules, Arrays.asList(nonMavenM2, null));
  }

  private static List<Module> orderEntriesToOwnerModules(List<OrderEntry> orderEntries) {
    return ContainerUtil.map(orderEntries, orderEntry -> orderEntry.getOwnerModule());
  }

  private static List<Module> orderEntriesToDepModules(List<OrderEntry> orderEntries) {
    return ContainerUtil.map(orderEntries,
                             orderEntry -> (orderEntry instanceof ModuleOrderEntry) ? ((ModuleOrderEntry)orderEntry).getModule() : null);
  }

  private void assertAllProductionClasspath(String moduleName, String... paths) throws Exception {
    assertCompileProductionClasspath(moduleName, paths);
    assertRuntimeProductionClasspath(moduleName, paths);
  }

  private void assertAllTestsClasspath(String moduleName, String... paths) throws Exception {
    assertCompileTestsClasspath(moduleName, paths);
    assertRuntimeTestsClasspath(moduleName, paths);
  }

  private void assertCompileProductionClasspath(String moduleName, String... paths) throws Exception {
    assertClasspath(moduleName, Scope.COMPILE, Type.PRODUCTION, paths);
  }

  private void assertCompileTestsClasspath(String moduleName, String... paths) throws Exception {
    assertClasspath(moduleName, Scope.COMPILE, Type.TESTS, paths);
  }

  private void assertRuntimeProductionClasspath(String moduleName, String... paths) throws Exception {
    assertClasspath(moduleName, Scope.RUNTIME, Type.PRODUCTION, paths);
  }

  private void assertRuntimeTestsClasspath(String moduleName, String... paths) throws Exception {
    assertClasspath(moduleName, Scope.RUNTIME, Type.TESTS, paths);
  }

  private void assertClasspath(String moduleName, Scope scope, Type type, String... expectedPaths) throws Exception {
    createOutputDirectories();

    PathsList actualPathsList;
    Module module = getModule(moduleName);

    if (scope == Scope.RUNTIME) {
      JavaParameters params = new JavaParameters();
      params.configureByModule(module, type == Type.TESTS ? JavaParameters.CLASSES_AND_TESTS : JavaParameters.CLASSES_ONLY);
      actualPathsList = params.getClassPath();
    }
    else {
      OrderEnumerator en = OrderEnumerator.orderEntries(module).recursively().withoutSdk().compileOnly();
      if (type == Type.PRODUCTION) en.productionOnly();
      actualPathsList = en.classes().getPathsList();
    }

    assertPaths(expectedPaths, actualPathsList.getPathList());
  }

  private void assertModuleScopes(String... modules) {
    for (String each : modules) {
      assertModuleSearchScope(each,
                              getProjectPath() + "/" + each + "/src/main/java",
                              getProjectPath() + "/" + each + "/src/test/java");
    }
  }

  private void assertModuleSearchScope(String moduleName, String... paths) {
    assertSearchScope(moduleName, Scope.MODULE, null, paths);
  }

  private void assertAllProductionSearchScope(String moduleName, String... paths) {
    assertCompileProductionSearchScope(moduleName, paths);
    assertRuntimeProductionSearchScope(moduleName, paths);
  }

  private void assertAllTestsSearchScope(String moduleName, String... paths) {
    assertCompileTestsSearchScope(moduleName, paths);
    assertRuntimeTestsSearchScope(moduleName, paths);
  }

  private void assertCompileProductionSearchScope(String moduleName, String... paths) {
    assertSearchScope(moduleName, Scope.COMPILE, Type.PRODUCTION, paths);
  }

  private void assertCompileTestsSearchScope(String moduleName, String... paths) {
    assertSearchScope(moduleName, Scope.COMPILE, Type.TESTS, paths);
  }

  private void assertRuntimeProductionSearchScope(String moduleName, String... paths) {
    assertSearchScope(moduleName, Scope.RUNTIME, Type.PRODUCTION, paths);
  }

  private void assertRuntimeTestsSearchScope(String moduleName, String... paths) {
    assertSearchScope(moduleName, Scope.RUNTIME, Type.TESTS, paths);
  }

  private void assertSearchScope(String moduleName, Scope scope, Type type, String... expectedPaths) {
    createOutputDirectories();
    Module module = getModule(moduleName);

    GlobalSearchScope searchScope = switch (scope) {
      case MODULE -> module.getModuleScope();
      case COMPILE -> module.getModuleWithDependenciesAndLibrariesScope(type == Type.TESTS);
      case RUNTIME -> module.getModuleRuntimeScope(type == Type.TESTS);
    };

    assertSearchScope(searchScope, expectedPaths);
  }

  private void assertSearchScope(GlobalSearchScope searchScope, String... expectedPaths) {
    Collection<VirtualFile> roots;
    if (searchScope instanceof DelegatingGlobalSearchScope) {
      searchScope = ReflectionUtil.getField(DelegatingGlobalSearchScope.class, searchScope, GlobalSearchScope.class, "myBaseScope");
    }
    if (searchScope instanceof ModuleWithDependenciesScope) {
      roots = ((ModuleWithDependenciesScope)searchScope).getRoots();
    }
    else {
      roots = ((LibraryRuntimeClasspathScope)searchScope).getRoots();
    }
    final List<VirtualFile> entries = new ArrayList<>(roots);
    entries.removeAll(Arrays.asList(ProjectRootManager.getInstance(myProject).orderEntries().sdkOnly().classes().getRoots()));

    List<String> actualPaths = new ArrayList<>();
    for (VirtualFile each : entries) {
      actualPaths.add(each.getPresentableUrl());
    }

    assertPaths(expectedPaths, actualPaths);
  }

  private static void assertPaths(String[] expectedPaths, List<String> actualPaths) {
    List<String> normalizedActualPaths = new ArrayList<>();
    List<String> normalizedExpectedPaths = new ArrayList<>();

    for (String each : actualPaths) {
      normalizedActualPaths.add(FileUtil.toSystemDependentName(each));
    }
    for (String each : expectedPaths) {
      normalizedExpectedPaths.add(FileUtil.toSystemDependentName(each));
    }

    assertOrderedElementsAreEqual(normalizedActualPaths, normalizedExpectedPaths);
  }

  private void createRepositoryFile(String filePath) throws IOException {
    File f = new File(getProjectPath(), "repo/" + filePath);
    f.getParentFile().mkdirs();

    ArtifactsDownloadingTestCase.createEmptyJar(f.getParent(), f.getName());
    setRepositoryPath(createProjectSubDir("repo").getPath());
  }

  private void createOutputDirectories() {
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      CompilerModuleExtension extension = CompilerModuleExtension.getInstance(module);
      if (extension != null) {
        createDirectoryIfDoesntExist(extension.getCompilerOutputUrl());
        createDirectoryIfDoesntExist(extension.getCompilerOutputUrlForTests());
      }
    }
  }

  private static void createDirectoryIfDoesntExist(@Nullable String url) {
    if (StringUtil.isEmpty(url)) return;

    File file = new File(FileUtil.toSystemDependentName(VfsUtil.urlToPath(url)));
    if (file.exists()) return;

    if (!file.mkdirs()) {
      fail("Cannot create directory " + file);
    }
    VirtualFileManager.getInstance().refreshAndFindFileByUrl(url);
  }
}
