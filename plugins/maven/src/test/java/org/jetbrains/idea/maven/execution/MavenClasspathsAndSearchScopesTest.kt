// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution

import com.intellij.execution.configurations.JavaParameters
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModuleLibDeps
import com.intellij.maven.testFramework.fixtures.assertModuleModuleDeps
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.assertOrderedElementsAreEqual
import com.intellij.maven.testFramework.fixtures.createModule
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectSubDir
import com.intellij.maven.testFramework.fixtures.createProjectSubDirs
import com.intellij.maven.testFramework.fixtures.getModule
import com.intellij.maven.testFramework.fixtures.importProjectsAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.projectPath
import com.intellij.maven.testFramework.fixtures.repositoryPathCanonical
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager.Companion.getInstance
import com.intellij.openapi.module.impl.scopes.LibraryRuntimeClasspathScope
import com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ModuleSourceOrderEntry
import com.intellij.openapi.roots.OrderEntry
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.impl.LibraryScopeCache
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.search.DelegatingGlobalSearchScope
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ArrayUtil
import com.intellij.util.ArrayUtilRt
import com.intellij.util.CommonProcessors.CollectProcessor
import com.intellij.util.PathsList
import com.intellij.util.ReflectionUtil
import com.intellij.util.io.DigestUtil.sha1
import com.intellij.util.io.createDirectories
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.fixtures.setupJdkForModules
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Paths

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenClasspathsAndSearchScopesTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion,
  )
  
  private enum class Type {
    PRODUCTION, TESTS
  }

  private enum class Scope {
    COMPILE, RUNTIME, MODULE
  }

  @BeforeEach
  fun setUp() {
    maven.createProjectSubDirs("m1/src/main/java",
                         "m1/src/test/java",

                         "m2/src/main/java",
                         "m2/src/test/java",

                         "m3/src/main/java",
                         "m3/src/test/java",

                         "m4/src/main/java",
                         "m4/src/test/java")
  }

  @Test
  fun testConfiguringModuleDependencies() = runBlocking {
    val m1 = maven.createModulePom("m1", """
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
      
      """.trimIndent())

    val m2 = maven.createModulePom("m2", """
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
      
      """.trimIndent())

    val m3 = maven.createModulePom("m3", """
      <groupId>test</groupId>
      <artifactId>m3</artifactId>
      <version>1</version>
      
      """.trimIndent())

    val m4 = maven.createModulePom("m4", """
      <groupId>test</groupId>
      <artifactId>m4</artifactId>
      <version>1</version>
      
      """.trimIndent())

    maven.importProjectsAsync(m1, m2, m3, m4)
    maven.assertModules("m1", "m2", "m3", "m4")

    maven.assertModuleModuleDeps("m1", "m2", "m3")
    maven.assertModuleModuleDeps("m2", "m3", "m4")

    maven.setupJdkForModules("m1", "m2", "m3", "m4")

    assertModuleScopes("m1", "m2", "m3", "m4")

    assertAllProductionSearchScope("m1",
                                   "${maven.projectPath}/m1/src/main/java",
                                   "${maven.projectPath}/m2/src/main/java",
                                   "${maven.projectPath}/m3/src/main/java")
    assertAllTestsSearchScope("m1",
                              "${maven.projectPath}/m1/src/main/java",
                              "${maven.projectPath}/m1/src/test/java",
                              "${maven.projectPath}/m2/src/main/java",
                              "${maven.projectPath}/m3/src/main/java")

    assertAllProductionClasspath("m1",
                                 "${maven.projectPath}/m1/target/classes",
                                 "${maven.projectPath}/m2/target/classes",
                                 "${maven.projectPath}/m3/target/classes")
    assertAllTestsClasspath("m1",
                            "${maven.projectPath}/m1/target/test-classes",
                            "${maven.projectPath}/m1/target/classes",
                            "${maven.projectPath}/m2/target/classes",
                            "${maven.projectPath}/m3/target/classes")

    assertAllProductionSearchScope("m2",
                                   "${maven.projectPath}/m2/src/main/java",
                                   "${maven.projectPath}/m3/src/main/java",
                                   "${maven.projectPath}/m4/src/main/java")
    assertAllTestsSearchScope("m2",
                              "${maven.projectPath}/m2/src/main/java",
                              "${maven.projectPath}/m2/src/test/java",
                              "${maven.projectPath}/m3/src/main/java",
                              "${maven.projectPath}/m4/src/main/java")

    assertAllProductionClasspath("m2",
                                 "${maven.projectPath}/m2/target/classes",
                                 "${maven.projectPath}/m3/target/classes",
                                 "${maven.projectPath}/m4/target/classes")
    assertAllTestsClasspath("m2",
                            "${maven.projectPath}/m2/target/test-classes",
                            "${maven.projectPath}/m2/target/classes",
                            "${maven.projectPath}/m3/target/classes",
                            "${maven.projectPath}/m4/target/classes")
  }

  @Test
  fun testDoNotIncludeTargetDirectoriesOfModuleDependenciesToLibraryClassesRoots() = runBlocking {
    val m = maven.createModulePom("m", """
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
      
      """.trimIndent())

    val dep = maven.createModulePom("dep", """
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
      
      """.trimIndent())

    maven.importProjectsAsync(m, dep)
    maven.assertModules("m", "dep")

    maven.assertModuleModuleDeps("m", "dep")

    maven.setupJdkForModules("m", "dep")

    createOutputDirectories()
    val module = maven.getModule("m")
    val jdkRoots = ModuleRootManager.getInstance(module).sdk!!.rootProvider.getFiles(OrderRootType.CLASSES)
    val junitRoots = LibraryTablesRegistrar.getInstance().getLibraryTable(maven.project).getLibraryByName("Maven: junit:junit:4.0")!!.getFiles(OrderRootType.CLASSES)
    UsefulTestCase.assertOrderedEquals(OrderEnumerator.orderEntries(module).allLibrariesAndSdkClassesRoots,
                                       *ArrayUtil.mergeArrays(jdkRoots, junitRoots))
  }

  @Test
  fun testDoNotIncludeTestClassesWhenConfiguringModuleDependenciesForProductionCode() = runBlocking {
    val m1 = maven.createModulePom("m1", """
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
      
      """.trimIndent())

    val m2 = maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      
      """.trimIndent())

    maven.importProjectsAsync(m1, m2)
    maven.assertModules("m1", "m2")
    maven.assertModuleModuleDeps("m1", "m2")

    maven.setupJdkForModules("m1", "m2")

    assertModuleScopes("m1", "m2")

    assertAllProductionSearchScope("m1",
                                   "${maven.projectPath}/m1/src/main/java",
                                   "${maven.projectPath}/m2/src/main/java")
    assertAllProductionClasspath("m1",
                                 "${maven.projectPath}/m1/target/classes",
                                 "${maven.projectPath}/m2/target/classes")

    assertAllProductionSearchScope("m2",
                                   "${maven.projectPath}/m2/src/main/java")
    assertAllProductionClasspath("m2",
                                 "${maven.projectPath}/m2/target/classes")
  }

  @Test
  fun testConfiguringModuleDependenciesOnTestJar() = runBlocking {
    val m1 = maven.createModulePom("m1", """
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
      
      """.trimIndent())

    val m2 = maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      
      """.trimIndent())
    val m3 = maven.createModulePom("m3", """
      <groupId>test</groupId>
      <artifactId>m3</artifactId>
      <version>1</version>
      
      """.trimIndent())

    maven.importProjectsAsync(m1, m2, m3)
    maven.assertModules("m1", "m2", "m3")

    maven.setupJdkForModules("m1", "m2", "m3")

    assertModuleScopes("m1", "m2", "m3")

    assertAllProductionSearchScope("m1",
                                   "${maven.projectPath}/m1/src/main/java",
                                   "${maven.projectPath}/m2/src/test/java",
                                   "${maven.projectPath}/m3/src/test/java")
    assertAllTestsSearchScope("m1",
                              "${maven.projectPath}/m1/src/main/java",
                              "${maven.projectPath}/m1/src/test/java",
                              "${maven.projectPath}/m2/src/test/java",
                              "${maven.projectPath}/m3/src/test/java")

    assertAllProductionClasspath("m1",
                                 "${maven.projectPath}/m1/target/classes",
                                 "${maven.projectPath}/m2/target/test-classes",
                                 "${maven.projectPath}/m3/target/test-classes")
    assertAllTestsClasspath("m1",
                            "${maven.projectPath}/m1/target/test-classes",
                            "${maven.projectPath}/m1/target/classes",
                            "${maven.projectPath}/m2/target/test-classes",
                            "${maven.projectPath}/m3/target/test-classes")
  }

  @Test
  fun testConfiguringModuleDependenciesOnTestJarWithTestScope() = runBlocking {
    val m1 = maven.createModulePom("m1", """
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
      
      """.trimIndent())

    val m2 = maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      
      """.trimIndent())
    val m3 = maven.createModulePom("m3", """
      <groupId>test</groupId>
      <artifactId>m3</artifactId>
      <version>1</version>
      
      """.trimIndent())

    maven.importProjectsAsync(m1, m2, m3)
    maven.assertModules("m1", "m2", "m3")

    maven.setupJdkForModules("m1", "m2", "m3")

    assertModuleScopes("m1", "m2", "m3")

    assertAllProductionSearchScope("m1",
                                   "${maven.projectPath}/m1/src/main/java")
    assertAllTestsSearchScope("m1",
                              "${maven.projectPath}/m1/src/main/java",
                              "${maven.projectPath}/m1/src/test/java",
                              "${maven.projectPath}/m2/src/test/java",
                              "${maven.projectPath}/m3/src/test/java")

    assertAllProductionClasspath("m1",
                                 "${maven.projectPath}/m1/target/classes")
    assertAllTestsClasspath("m1",
                            "${maven.projectPath}/m1/target/test-classes",
                            "${maven.projectPath}/m1/target/classes",
                            "${maven.projectPath}/m2/target/test-classes",
                            "${maven.projectPath}/m3/target/test-classes")
  }

  @Test
  fun testConfiguringModuleDependenciesOnBothNormalAndTestJar() = runBlocking {
    val m1 = maven.createModulePom("m1", """
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
      
      """.trimIndent())

    val m2 = maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      
      """.trimIndent())

    maven.importProjectsAsync(m1, m2)
    maven.assertModules("m1", "m2")

    maven.setupJdkForModules("m1", "m2")

    assertModuleScopes("m1", "m2")

    assertAllProductionSearchScope("m1",
                                   "${maven.projectPath}/m1/src/main/java",
                                   "${maven.projectPath}/m2/src/main/java",
                                   "${maven.projectPath}/m2/src/test/java")
    assertAllTestsSearchScope("m1",
                              "${maven.projectPath}/m1/src/main/java",
                              "${maven.projectPath}/m1/src/test/java",
                              "${maven.projectPath}/m2/src/main/java",
                              "${maven.projectPath}/m2/src/test/java")


    assertAllProductionClasspath("m1",
                                 "${maven.projectPath}/m1/target/classes",
                                 "${maven.projectPath}/m2/target/classes",
                                 "${maven.projectPath}/m2/target/test-classes")

    assertAllTestsClasspath("m1",
                            "${maven.projectPath}/m1/target/test-classes",
                            "${maven.projectPath}/m1/target/classes",
                            "${maven.projectPath}/m2/target/classes",
                            "${maven.projectPath}/m2/target/test-classes")
  }

  @Test
  fun testConfiguringModuleDependenciesOnNormalAndTestJarWithTestScope() = runBlocking {
    val m1 = maven.createModulePom("m1", """
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
      
      """.trimIndent())

    val m2 = maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      
      """.trimIndent())

    maven.importProjectsAsync(m1, m2)
    maven.assertModules("m1", "m2")

    maven.setupJdkForModules("m1", "m2")

    assertModuleScopes("m1", "m2")

    assertAllProductionSearchScope("m1",
                                   "${maven.projectPath}/m1/src/main/java",
                                   "${maven.projectPath}/m2/src/main/java")
    assertAllTestsSearchScope("m1",
                              "${maven.projectPath}/m1/src/main/java",
                              "${maven.projectPath}/m1/src/test/java",
                              "${maven.projectPath}/m2/src/main/java",
                              "${maven.projectPath}/m2/src/test/java")

    assertAllProductionClasspath("m1",
                                 "${maven.projectPath}/m1/target/classes",
                                 "${maven.projectPath}/m2/target/classes")

    assertAllTestsClasspath("m1",
                            "${maven.projectPath}/m1/target/test-classes",
                            "${maven.projectPath}/m1/target/classes",
                            "${maven.projectPath}/m2/target/classes",
                            "${maven.projectPath}/m2/target/test-classes")
  }

  @Test
  fun testOptionalLibraryDependencies() = runBlocking {
    createRepositoryFile("jmock/jmock/1.0/jmock-1.0.jar")
    val m1 = maven.createModulePom("m1", """
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
      
      """.trimIndent())

    val m2 = maven.createModulePom("m2", """
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
      
      """.trimIndent())

    maven.importProjectsAsync(m1, m2)
    maven.assertModules("m1", "m2")

    assertModuleScopes("m1", "m2")

    maven.assertModuleModuleDeps("m1", "m2")
    maven.assertModuleLibDeps("m1", "Maven: jmock:jmock:1.0")
    maven.assertModuleLibDeps("m2", "Maven: jmock:jmock:1.0", "Maven: junit:junit:4.0")

    maven.setupJdkForModules("m1", "m2")

    assertAllProductionSearchScope("m1",
                                   "${maven.projectPath}/m1/src/main/java",
                                   "${maven.projectPath}/m2/src/main/java",
                                   "${maven.repositoryPathCanonical}/jmock/jmock/1.0/jmock-1.0.jar")
    assertAllTestsSearchScope("m1",
                              "${maven.projectPath}/m1/src/main/java",
                              "${maven.projectPath}/m1/src/test/java",
                              "${maven.projectPath}/m2/src/main/java",
                              "${maven.repositoryPathCanonical}/jmock/jmock/1.0/jmock-1.0.jar")

    assertAllProductionClasspath("m1",
                                 "${maven.projectPath}/m1/target/classes",
                                 "${maven.projectPath}/m2/target/classes",
                                 "${maven.repositoryPathCanonical}/jmock/jmock/1.0/jmock-1.0.jar")
    assertAllTestsClasspath("m1",
                            "${maven.projectPath}/m1/target/test-classes",
                            "${maven.projectPath}/m1/target/classes",
                            "${maven.projectPath}/m2/target/classes",
                            "${maven.repositoryPathCanonical}/jmock/jmock/1.0/jmock-1.0.jar")

    assertAllProductionSearchScope("m2",
                                   "${maven.projectPath}/m2/src/main/java",
                                   "${maven.repositoryPathCanonical}/jmock/jmock/1.0/jmock-1.0.jar",
                                   "${maven.repositoryPathCanonical}/junit/junit/4.0/junit-4.0.jar")
    assertAllTestsSearchScope("m2",
                              "${maven.projectPath}/m2/src/main/java",
                              "${maven.projectPath}/m2/src/test/java",
                              "${maven.repositoryPathCanonical}/jmock/jmock/1.0/jmock-1.0.jar",
                              "${maven.repositoryPathCanonical}/junit/junit/4.0/junit-4.0.jar")

    assertAllProductionClasspath("m2",
                                 "${maven.projectPath}/m2/target/classes",
                                 "${maven.repositoryPathCanonical}/jmock/jmock/1.0/jmock-1.0.jar",
                                 "${maven.repositoryPathCanonical}/junit/junit/4.0/junit-4.0.jar")
    assertAllTestsClasspath("m2",
                            "${maven.projectPath}/m2/target/test-classes",
                            "${maven.projectPath}/m2/target/classes",
                            "${maven.repositoryPathCanonical}/jmock/jmock/1.0/jmock-1.0.jar",
                            "${maven.repositoryPathCanonical}/junit/junit/4.0/junit-4.0.jar")
  }

  @Test
  fun testProvidedAndTestDependencies() = runBlocking {
    createRepositoryFile("jmock/jmock/4.0/jmock-4.0.jar")
    val m1 = maven.createModulePom("m1", """
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
      
      """.trimIndent())

    val m2 = maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      
      """.trimIndent())

    val m3 = maven.createModulePom("m3", """
      <groupId>test</groupId>
      <artifactId>m3</artifactId>
      <version>1</version>
      
      """.trimIndent())

    maven.importProjectsAsync(m1, m2, m3)
    maven.assertModules("m1", "m2", "m3")

    maven.setupJdkForModules("m1", "m2", "m3")

    assertModuleScopes("m1", "m2", "m3")

    assertCompileProductionSearchScope("m1",
                                       "${maven.projectPath}/m1/src/main/java",
                                       "${maven.projectPath}/m2/src/main/java",
                                       "${maven.repositoryPathCanonical}/junit/junit/4.0/junit-4.0.jar")
    assertRuntimeProductionSearchScope("m1",
                                       "${maven.projectPath}/m1/src/main/java",
                                       "${maven.projectPath}/m2/src/main/java",
                                       "${maven.repositoryPathCanonical}/junit/junit/4.0/junit-4.0.jar")
    assertAllTestsSearchScope("m1",
                              "${maven.projectPath}/m1/src/main/java",
                              "${maven.projectPath}/m1/src/test/java",
                              "${maven.projectPath}/m2/src/main/java",
                              "${maven.repositoryPathCanonical}/junit/junit/4.0/junit-4.0.jar",
                              "${maven.projectPath}/m3/src/main/java",
                              "${maven.repositoryPathCanonical}/jmock/jmock/4.0/jmock-4.0.jar")

    assertCompileProductionClasspath("m1",
                                     "${maven.projectPath}/m1/target/classes",
                                     "${maven.projectPath}/m2/target/classes",
                                     "${maven.repositoryPathCanonical}/junit/junit/4.0/junit-4.0.jar")
    assertRuntimeProductionClasspath("m1",
                                     "${maven.projectPath}/m1/target/classes")
    assertAllTestsClasspath("m1",
                            "${maven.projectPath}/m1/target/test-classes",
                            "${maven.projectPath}/m1/target/classes",
                            "${maven.projectPath}/m2/target/classes",
                            "${maven.repositoryPathCanonical}/junit/junit/4.0/junit-4.0.jar",
                            "${maven.projectPath}/m3/target/classes",
                            "${maven.repositoryPathCanonical}/jmock/jmock/4.0/jmock-4.0.jar")
  }

  @Test
  fun testRuntimeDependency() = runBlocking {
    createRepositoryFile("jmock/jmock/4.0/jmock-4.0.jar")
    val m1 = maven.createModulePom("m1", """
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
      
      """.trimIndent())

    val m2 = maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      
      """.trimIndent())

    maven.importProjectsAsync(m1, m2)
    maven.assertModules("m1", "m2")

    maven.setupJdkForModules("m1", "m2")

    assertModuleScopes("m1", "m2")

    assertCompileProductionSearchScope("m1",
                                       "${maven.projectPath}/m1/src/main/java")
    assertRuntimeProductionSearchScope("m1",
                                       "${maven.projectPath}/m1/src/main/java",
                                       "${maven.projectPath}/m2/src/main/java",
                                       "${maven.repositoryPathCanonical}/junit/junit/4.0/junit-4.0.jar")
    assertAllTestsSearchScope("m1",
                              "${maven.projectPath}/m1/src/main/java",
                              "${maven.projectPath}/m1/src/test/java",
                              "${maven.projectPath}/m2/src/main/java",
                              "${maven.repositoryPathCanonical}/junit/junit/4.0/junit-4.0.jar")

    assertCompileProductionClasspath("m1",
                                     "${maven.projectPath}/m1/target/classes")

    assertRuntimeProductionClasspath("m1",
                                     "${maven.projectPath}/m1/target/classes",
                                     "${maven.projectPath}/m2/target/classes",
                                     "${maven.repositoryPathCanonical}/junit/junit/4.0/junit-4.0.jar")

    assertAllTestsClasspath("m1",
                            "${maven.projectPath}/m1/target/test-classes",
                            "${maven.projectPath}/m1/target/classes",
                            "${maven.projectPath}/m2/target/classes",
                            "${maven.repositoryPathCanonical}/junit/junit/4.0/junit-4.0.jar")
  }

  @Test
  fun testDoNotIncludeProvidedAndTestTransitiveDependencies() = runBlocking {
    createRepositoryFile("jmock/jmock/1.0/jmock-1.0.jar")
    val m1 = maven.createModulePom("m1", """
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
      
      """.trimIndent())

    val m2 = maven.createModulePom("m2", """
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
      
      """.trimIndent())

    maven.importProjectsAsync(m1, m2)
    maven.assertModules("m1", "m2")

    maven.assertModuleModuleDeps("m1", "m2")
    maven.assertModuleLibDeps("m1")
    maven.assertModuleLibDeps("m2", "Maven: jmock:jmock:1.0", "Maven: junit:junit:4.0")

    maven.setupJdkForModules("m1", "m2")

    assertModuleScopes("m1", "m2")

    assertAllProductionSearchScope("m1",
                                   "${maven.projectPath}/m1/src/main/java",
                                   "${maven.projectPath}/m2/src/main/java")
    assertAllTestsSearchScope("m1",
                              "${maven.projectPath}/m1/src/main/java",
                              "${maven.projectPath}/m1/src/test/java",
                              "${maven.projectPath}/m2/src/main/java")

    assertAllProductionClasspath("m1",
                                 "${maven.projectPath}/m1/target/classes",
                                 "${maven.projectPath}/m2/target/classes")
    assertAllTestsClasspath("m1",
                            "${maven.projectPath}/m1/target/test-classes",
                            "${maven.projectPath}/m1/target/classes",
                            "${maven.projectPath}/m2/target/classes")


    assertCompileProductionSearchScope("m2",
                                       "${maven.projectPath}/m2/src/main/java",
                                       "${maven.repositoryPathCanonical}/jmock/jmock/1.0/jmock-1.0.jar")
    assertRuntimeProductionSearchScope("m2",
                                       "${maven.projectPath}/m2/src/main/java",
                                       "${maven.repositoryPathCanonical}/jmock/jmock/1.0/jmock-1.0.jar")
    assertAllTestsSearchScope("m2",
                              "${maven.projectPath}/m2/src/main/java",
                              "${maven.projectPath}/m2/src/test/java",
                              "${maven.repositoryPathCanonical}/jmock/jmock/1.0/jmock-1.0.jar",
                              "${maven.repositoryPathCanonical}/junit/junit/4.0/junit-4.0.jar")

    assertCompileProductionClasspath("m2",
                                     "${maven.projectPath}/m2/target/classes",
                                     "${maven.repositoryPathCanonical}/jmock/jmock/1.0/jmock-1.0.jar")
    assertRuntimeProductionClasspath("m2",
                                     "${maven.projectPath}/m2/target/classes")
    assertAllTestsClasspath("m2",
                            "${maven.projectPath}/m2/target/test-classes",
                            "${maven.projectPath}/m2/target/classes",
                            "${maven.repositoryPathCanonical}/jmock/jmock/1.0/jmock-1.0.jar",
                            "${maven.repositoryPathCanonical}/junit/junit/4.0/junit-4.0.jar")
  }

  @Test
  fun testLibraryScopeForTwoDependentModules() = runBlocking {
    val m1 = maven.createModulePom("m1", """
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
      
      """.trimIndent())

    val m2 = maven.createModulePom("m2", """
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
      
      """.trimIndent())
    maven.importProjectsAsync(m1, m2)
    maven.assertModules("m1", "m2")

    val m1m = getInstance(maven.project).findModuleByName("m1")
    val modules1: List<OrderEntry> = ArrayList()
    ModuleRootManager.getInstance(m1m!!).orderEntries().withoutSdk().withoutModuleSourceEntries().forEach(
      CollectProcessor(modules1))
    val scope1 = LibraryScopeCache.getInstance(maven.project).getLibraryScope(modules1)
    assertSearchScope(scope1,
                      "${maven.projectPath}/m1/src/main/java",
                      "${maven.projectPath}/m1/src/test/java",
                      "${maven.projectPath}/m2/src/main/java",
                      "${maven.projectPath}/m2/src/test/java"
    )

    val libraryPath = "${maven.repositoryPathCanonical}/junit/junit/4.0/junit-4.0.jar"
    val librarySrcPath = "${maven.repositoryPathCanonical}/junit/junit/4.0/junit-4.0-sources.jar"
    val m2m = getInstance(maven.project).findModuleByName("m2")
    val modules2: List<OrderEntry> = ArrayList()
    ModuleRootManager.getInstance(m2m!!).orderEntries().withoutSdk().withoutModuleSourceEntries().forEach(
      CollectProcessor(modules2))
    val scope2 = LibraryScopeCache.getInstance(maven.project).getLibraryScope(modules2)

    val expectedPaths: MutableList<String> =
      ArrayList(listOf("${maven.projectPath}/m2/src/main/java", "${maven.projectPath}/m2/src/test/java", libraryPath))
    if (File(librarySrcPath).exists()) {
      expectedPaths.add(librarySrcPath)
    }
    assertSearchScope(scope2, *ArrayUtilRt.toStringArray(expectedPaths))
  }

  @Test
  fun testDoNotIncludeConflictingTransitiveDependenciesInTheClasspath() = runBlocking {
    val m1 = maven.createModulePom("m1", """
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
      
      """.trimIndent())

    val m2 = maven.createModulePom("m2", """
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
      
      """.trimIndent())

    val m3 = maven.createModulePom("m3", """
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
      
      """.trimIndent())

    maven.importProjectsAsync(m1, m2, m3)
    maven.assertModules("m1", "m2", "m3")

    maven.assertModuleModuleDeps("m1", "m2", "m3")
    maven.assertModuleLibDeps("m1", "Maven: junit:junit:4.0")

    maven.setupJdkForModules("m1", "m2", "m3")

    assertModuleScopes("m1", "m2", "m3")

    assertAllTestsSearchScope("m1",
                              "${maven.projectPath}/m1/src/main/java",
                              "${maven.projectPath}/m1/src/test/java",
                              "${maven.projectPath}/m2/src/main/java",
                              "${maven.repositoryPathCanonical}/junit/junit/4.0/junit-4.0.jar",
                              "${maven.projectPath}/m3/src/main/java")

    assertAllTestsClasspath("m1",
                            "${maven.projectPath}/m1/target/test-classes",
                            "${maven.projectPath}/m1/target/classes",
                            "${maven.projectPath}/m2/target/classes",
                            "${maven.repositoryPathCanonical}/junit/junit/4.0/junit-4.0.jar",
                            "${maven.projectPath}/m3/target/classes")
  }

  @Test
  fun testDoNotChangeClasspathForRegularModules() = runBlocking {
    val m1 = maven.createModulePom("m1", """
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
      
      """.trimIndent())

    val m2 = maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      
      """.trimIndent())

    maven.importProjectsAsync(m1, m2)
    maven.assertModules("m1", "m2")

    val user = maven.createModule("user")

    WriteCommandAction.writeCommandAction(maven.project).run<IOException> {
      ModuleRootModificationUtil.addDependency(user, maven.getModule("m1"))
      val out = user.moduleFile!!.parent.createChildDirectory(this, "output")
      val testOut = user.moduleFile!!.parent.createChildDirectory(this, "test-output")
      PsiTestUtil.setCompilerOutputPath(user, out.url, false)
      PsiTestUtil.setCompilerOutputPath(user, testOut.url, true)
    }


    maven.assertModuleModuleDeps("m1", "m2")
    maven.assertModuleLibDeps("m1", "Maven: junit:junit:4.0")

    maven.assertModuleModuleDeps("user", "m1")
    maven.assertModuleLibDeps("user")

    maven.setupJdkForModules("m1", "m2", "user")

    // todo check search scopes
    assertModuleScopes("m1", "m2")

    assertCompileProductionClasspath("user",
                                     "${maven.projectPath}/user/output",
                                     "${maven.projectPath}/m1/target/classes",
                                     "${maven.repositoryPathCanonical}/junit/junit/4.0/junit-4.0.jar")

    assertRuntimeProductionClasspath("user",
                                     "${maven.projectPath}/user/output",
                                     "${maven.projectPath}/m1/target/classes",
                                     "${maven.projectPath}/m2/target/classes")

    assertCompileTestsClasspath("user",
                                "${maven.projectPath}/user/test-output",
                                "${maven.projectPath}/user/output",
                                "${maven.projectPath}/m1/target/test-classes",
                                "${maven.projectPath}/m1/target/classes",
                                "${maven.repositoryPathCanonical}/junit/junit/4.0/junit-4.0.jar")

    assertRuntimeTestsClasspath("user",
                                "${maven.projectPath}/user/test-output",
                                "${maven.projectPath}/user/output",
                                "${maven.projectPath}/m1/target/test-classes",
                                "${maven.projectPath}/m1/target/classes",
                                "${maven.projectPath}/m2/target/test-classes",
                                "${maven.projectPath}/m2/target/classes",
                                "${maven.repositoryPathCanonical}/junit/junit/4.0/junit-4.0.jar")

    assertCompileProductionClasspath("m1",
                                     "${maven.projectPath}/m1/target/classes",
                                     "${maven.repositoryPathCanonical}/junit/junit/4.0/junit-4.0.jar")
    assertRuntimeProductionClasspath("m1",
                                     "${maven.projectPath}/m1/target/classes",
                                     "${maven.projectPath}/m2/target/classes")
    assertAllTestsClasspath("m1",
                            "${maven.projectPath}/m1/target/test-classes",
                            "${maven.projectPath}/m1/target/classes",
                            "${maven.projectPath}/m2/target/classes",
                            "${maven.repositoryPathCanonical}/junit/junit/4.0/junit-4.0.jar")
  }

  @Test
  fun testDirIndexOrderEntriesTransitiveCompileScope() = runBlocking {
    val modules = setupDirIndexTestModulesWithScope("compile")
    checkDirIndexTestModulesWithCompileOrRuntimeScope(modules)
  }

  @Test
  fun testDirIndexOrderEntriesTransitiveRuntimeScope() = runBlocking {
    val modules = setupDirIndexTestModulesWithScope("runtime")
    checkDirIndexTestModulesWithCompileOrRuntimeScope(modules)
  }

  // Creates a Maven dependency graph for testing DirectoryIndex#getOrderEntries.
  private suspend fun setupDirIndexTestModulesWithScope(scope: String): List<Module> {
    createRepositoryFile("jmock/jmock/1.0/jmock-1.0.jar")
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
    val m1 = maven.createModulePom("m1", """<groupId>test</groupId>
<artifactId>m1</artifactId>
<version>1</version>
<dependencies>
  <dependency>
    <groupId>test</groupId>
    <artifactId>m2</artifactId>
    <version>1</version>
    <scope>
$scope</scope>
  </dependency>
</dependencies>
""")
    val m2 = maven.createModulePom("m2", """<groupId>test</groupId>
<artifactId>m2</artifactId>
<version>1</version>
<dependencies>
  <dependency>
    <groupId>test</groupId>
    <artifactId>m3</artifactId>
    <version>1</version>
    <scope>
$scope</scope>
  </dependency>
  <dependency>
    <groupId>jmock</groupId>
    <artifactId>jmock</artifactId>
    <version>1.0</version>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>test</groupId>
    <artifactId>m5</artifactId>
    <version>1</version>
    <scope>
$scope</scope>
  </dependency>
</dependencies>
""")
    val m3 = maven.createModulePom("m3", """
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
      
      """.trimIndent())
    val m4 = maven.createModulePom("m4", """<groupId>test</groupId>
<artifactId>m4</artifactId>
<version>1</version>
<dependencies>
  <dependency>
    <groupId>test</groupId>
    <artifactId>m3</artifactId>
    <version>1</version>
    <scope>
$scope</scope>
  </dependency>
</dependencies>
""")
    // The default setupInWriteAction only creates directories up to m4.
    // Create directories for m5 and m6 which we will use for this test.
    WriteCommandAction.writeCommandAction(maven.project).run<RuntimeException> {
      maven.createProjectSubDirs("m5/src/main/java",
                           "m5/src/test/java",

                           "m6/src/main/java",
                           "m6/src/test/java")
    }
    val m5 = maven.createModulePom("m5", """
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
      
      """.trimIndent())
    val m6 = maven.createModulePom("m6", """
      <groupId>test</groupId>
      <artifactId>m6</artifactId>
      <version>1</version>
      
      """.trimIndent())
    maven.importProjectsAsync(m1, m2, m3, m4, m5, m6)
    maven.assertModules("m1", "m2", "m3", "m4", "m5", "m6")
    createOutputDirectories()

    return listOf(maven.getModule("m1"), maven.getModule("m2"), maven.getModule("m3"), maven.getModule("m4"),
                  maven.getModule("m5"), maven.getModule("m6"))
  }

  // Checks that the DirectoryIndex#getOrderEntries() returns the expected values
  // for the dependency graph set up by setupDirIndexTestModulesWithScope().
  // The result is the same for "compile" and "runtime" scopes.
  private suspend fun checkDirIndexTestModulesWithCompileOrRuntimeScope(modules: List<Module>) {
    assertEquals(6, modules.size)
    val index = ProjectFileIndex.getInstance(maven.project)
    val m3JavaDir = VfsUtil.findFile(Paths.get(maven.projectPath.toString(), "m3/src/main/java"), true)
    assertNotNull(m3JavaDir)
    // Should be: m1 -> m3, m2 -> m3, m3 -> source, and m4 -> m3
    val orderEntries = readAction { index.getOrderEntriesForFile(m3JavaDir!!) }
    assertEquals(4, orderEntries.size)
    val ownerModules = orderEntriesToOwnerModules(orderEntries)
    val depModules = orderEntriesToDepModules(orderEntries)
    assertOrderedElementsAreEqual(ownerModules, listOf(modules[0], modules[1], modules[2], modules[3]))
    assertOrderedElementsAreEqual(depModules, listOf(modules[2], modules[2], null, modules[2]))
    // m3 -> source
    val m3E2 = orderEntries[2]
    UsefulTestCase.assertInstanceOf(m3E2, ModuleSourceOrderEntry::class.java)

    val m6javaDir = VfsUtil.findFile(Paths.get(maven.projectPath.toString(), "m6/src/main/java"), true)
    assertNotNull(m6javaDir)
    // Should be m1 -> m6, m2 -> m6, m5 -> m6, m6 -> source
    val m6OrderEntries = readAction { index.getOrderEntriesForFile(m6javaDir!!) }
    assertEquals(4, m6OrderEntries.size)
    val m6OwnerModules = orderEntriesToOwnerModules(m6OrderEntries)
    val m6DepModules = orderEntriesToDepModules(m6OrderEntries)
    assertOrderedElementsAreEqual(m6OwnerModules, listOf(modules[0], modules[1], modules[4], modules[5]))
    assertOrderedElementsAreEqual(m6DepModules, listOf(modules[5], modules[5], modules[5], null))
    // m6 -> source
    val m6E3 = m6OrderEntries[3]
    UsefulTestCase.assertInstanceOf(m6E3, ModuleSourceOrderEntry::class.java)

    val jmockDir = VfsUtil.findFile(maven.repositoryPath.resolve("jmock/jmock/1.0/jmock-1.0.jar"), true)
    assertNotNull(jmockDir)
    val jmockJar = JarFileSystem.getInstance().getJarRootForLocalFile(jmockDir!!)
    assertNotNull(jmockJar)
    // m2 -> jmock, m3 -> jmock
    val jmockOrderEntries = readAction { index.getOrderEntriesForFile(jmockJar!!) }
    assertEquals(2, jmockOrderEntries.size)
    val jmockE0 = jmockOrderEntries[0]
    assertEquals(modules[1], jmockE0.ownerModule)
    UsefulTestCase.assertInstanceOf(jmockE0, LibraryOrderEntry::class.java)
    val jmockE1 = jmockOrderEntries[1]
    assertEquals(modules[2], jmockE1.ownerModule)
    UsefulTestCase.assertInstanceOf(jmockE1, LibraryOrderEntry::class.java)
  }

  @Test
  fun testDirIndexOrderEntriesTransitiveTestScope() = runBlocking {
    // This test is a bit different from the above tests of compile or runtime scope,
    // because test scope does not propagate transitive dependencies.
    val modules = setupDirIndexTestModulesWithScope("test")
    assertEquals(6, modules.size)
    val index = ProjectFileIndex.getInstance(maven.project)
    val m3JavaDir = VfsUtil.findFile(Paths.get(maven.projectPath.toString(), "m3/src/main/java"), true)
    assertNotNull(m3JavaDir)
    // Should be no transitive deps: m2 -> m3, m3 -> source, and m4 -> m3
    val orderEntries = readAction { index.getOrderEntriesForFile(m3JavaDir!!) }
    assertEquals(3, orderEntries.size)
    val ownerModules = orderEntriesToOwnerModules(orderEntries)
    val depModules = orderEntriesToDepModules(orderEntries)
    assertOrderedElementsAreEqual(ownerModules, listOf(modules[1], modules[2], modules[3]))
    assertOrderedElementsAreEqual(depModules, listOf(modules[2], null, modules[2]))
    // m3 -> source
    val m3E1 = orderEntries[1]
    UsefulTestCase.assertInstanceOf(m3E1, ModuleSourceOrderEntry::class.java)

    val m6javaDir = VfsUtil.findFile(Paths.get(maven.projectPath.toString(), "m6/src/main/java"), true)
    assertNotNull(m6javaDir)
    // Still has some transitive deps because m5 -> m6 is hardcoded to be compile scope
    // m2 -> m6, m5 -> m6, m6 -> source
    val m6OrderEntries = readAction { index.getOrderEntriesForFile(m6javaDir!!) }
    assertEquals(3, m6OrderEntries.size)
    val m6OwnerModules = orderEntriesToOwnerModules(m6OrderEntries)
    val m6DepModules = orderEntriesToDepModules(m6OrderEntries)
    assertOrderedElementsAreEqual(m6OwnerModules, listOf(modules[1], modules[4], modules[5]))
    assertOrderedElementsAreEqual(m6DepModules, listOf(modules[5], modules[5], null))
    // m6 -> source
    val m6E2 = m6OrderEntries[2]
    UsefulTestCase.assertInstanceOf(m6E2, ModuleSourceOrderEntry::class.java)

    val jmockDir = VfsUtil.findFile(maven.repositoryPath.resolve("jmock/jmock/1.0/jmock-1.0.jar"), true)
    assertNotNull(jmockDir)
    val jmockJar = JarFileSystem.getInstance().getJarRootForLocalFile(jmockDir!!)
    assertNotNull(jmockJar)
    // m2 -> jmock, m3 -> jmock
    val jmockOrderEntries = readAction { index.getOrderEntriesForFile(jmockJar!!) }
    assertEquals(2, jmockOrderEntries.size)
    val jmockE0 = jmockOrderEntries[0]
    assertEquals(modules[1], jmockE0.ownerModule)
    UsefulTestCase.assertInstanceOf(jmockE0, LibraryOrderEntry::class.java)
    val jmockE1 = jmockOrderEntries[1]
    assertEquals(modules[2], jmockE1.ownerModule)
    UsefulTestCase.assertInstanceOf(jmockE1, LibraryOrderEntry::class.java)

    Unit
  }

  @Test
  fun testDirIndexOrderEntriesStartingFromRegularModule() = runBlocking {
    val modules = setupDirIndexTestModulesWithScope("compile")
    assertEquals(6, modules.size)
    val nonMavenM1 = maven.createModule("nonMavenM1")
    val nonMavenM2 = maven.createModule("nonMavenM2")

    edtWriteAction {
      ModuleRootModificationUtil.addDependency(nonMavenM1, nonMavenM2, DependencyScope.COMPILE, true)
      ModuleRootModificationUtil.addDependency(nonMavenM2, modules[0], DependencyScope.COMPILE, true)
      maven.createProjectSubDirs("nonMavenM1/src/main/java", "nonMavenM1/src/test/java",
                           "nonMavenM2/src/main/java", "nonMavenM2/src/test/java")
      val nonMavenM1JavaDir = VfsUtil.findFile(Paths.get(maven.projectPath.toString(), "nonMavenM1/src/main/java"), true)
      assertNotNull(nonMavenM1JavaDir)
      PsiTestUtil.addSourceContentToRoots(nonMavenM1, nonMavenM1JavaDir!!)
      val nonMavenM2JavaDir = VfsUtil.findFile(Paths.get(maven.projectPath.toString(), "nonMavenM2/src/main/java"), true)
      assertNotNull(nonMavenM2JavaDir)
      PsiTestUtil.addSourceContentToRoots(nonMavenM2, nonMavenM2JavaDir!!)
    }

    maven.assertModuleModuleDeps("nonMavenM1", "nonMavenM2")
    maven.assertModuleModuleDeps("nonMavenM2", "m1")
    maven.assertModuleModuleDeps("m1", "m2", "m3", "m5", "m6")

    val index = ProjectFileIndex.getInstance(maven.project)
    val m3JavaDir = VfsUtil.findFile(Paths.get(maven.projectPath.toString(), "m3/src/main/java"), true)
    assertNotNull(m3JavaDir)
    // Should be: m1 -> m3, m2 -> m3, m3 -> source, and m4 -> m3
    // It doesn't trace back to nonMavenM1 and nonMavenM2.
    val orderEntries = readAction { index.getOrderEntriesForFile(m3JavaDir!!) }
    val ownerModules = orderEntriesToOwnerModules(orderEntries)
    val depModules = orderEntriesToDepModules(orderEntries)
    assertOrderedElementsAreEqual(ownerModules, listOf(modules[0], modules[1], modules[2], modules[3]))
    assertOrderedElementsAreEqual(depModules, listOf(modules[2], modules[2], null, modules[2]))

    val m6javaDir = VfsUtil.findFile(Paths.get(maven.projectPath.toString(), "m6/src/main/java"), true)
    assertNotNull(m6javaDir)
    // Should be m1 -> m6, m2 -> m6, m5 -> m6, m6 -> source
    val m6OrderEntries = readAction { index.getOrderEntriesForFile(m6javaDir!!) }
    val m6OwnerModules = orderEntriesToOwnerModules(m6OrderEntries)
    val m6DepModules = orderEntriesToDepModules(m6OrderEntries)
    assertOrderedElementsAreEqual(m6OwnerModules, listOf(modules[0], modules[1], modules[4], modules[5]))
    assertOrderedElementsAreEqual(m6DepModules, listOf(modules[5], modules[5], modules[5], null))

    val nonMavenM2JavaDir = VfsUtil.findFile(Paths.get(maven.projectPath.toString(), "nonMavenM2/src/main/java"), true)
    assertNotNull(nonMavenM2JavaDir)
    // Should be nonMavenM1 -> nonMavenM2, nonMavenM2 -> source
    val nonMavenM2JavaOrderEntries = readAction { index.getOrderEntriesForFile(nonMavenM2JavaDir!!) }
    val nonMavenM2OwnerModules = orderEntriesToOwnerModules(nonMavenM2JavaOrderEntries)
    val nonMavenM2DepModules = orderEntriesToDepModules(nonMavenM2JavaOrderEntries)
    assertOrderedElementsAreEqual(nonMavenM2OwnerModules, listOf(nonMavenM1, nonMavenM2))
    assertOrderedElementsAreEqual(nonMavenM2DepModules, listOf(nonMavenM2, null))
  }

  private suspend fun assertAllProductionClasspath(moduleName: String, vararg paths: String) {
    assertCompileProductionClasspath(moduleName, *paths)
    assertRuntimeProductionClasspath(moduleName, *paths)
  }

  private suspend fun assertAllTestsClasspath(moduleName: String, vararg paths: String) {
    assertCompileTestsClasspath(moduleName, *paths)
    assertRuntimeTestsClasspath(moduleName, *paths)
  }

  private suspend fun assertCompileProductionClasspath(moduleName: String, vararg paths: String) {
    assertClasspath(moduleName, Scope.COMPILE, Type.PRODUCTION, *paths)
  }

  private suspend fun assertCompileTestsClasspath(moduleName: String, vararg paths: String) {
    assertClasspath(moduleName, Scope.COMPILE, Type.TESTS, *paths)
  }

  private suspend fun assertRuntimeProductionClasspath(moduleName: String, vararg paths: String) {
    assertClasspath(moduleName, Scope.RUNTIME, Type.PRODUCTION, *paths)
  }

  private suspend fun assertRuntimeTestsClasspath(moduleName: String, vararg paths: String) {
    assertClasspath(moduleName, Scope.RUNTIME, Type.TESTS, *paths)
  }

  private suspend fun assertClasspath(moduleName: String, scope: Scope, type: Type, vararg expectedPaths: String) {
    createOutputDirectories()

    val actualPathsList: PathsList
    val module = maven.getModule(moduleName)

    actualPathsList = readAction {
      if (scope == Scope.RUNTIME) {
        val params = JavaParameters()
        params.configureByModule(module, if (type == Type.TESTS) JavaParameters.CLASSES_AND_TESTS else JavaParameters.CLASSES_ONLY)
        params.classPath
      }
      else {
        val en = OrderEnumerator.orderEntries(module).recursively().withoutSdk().compileOnly()
        if (type == Type.PRODUCTION) en.productionOnly()
        en.classes().pathsList
      }
    }

    assertPaths(expectedPaths, actualPathsList.pathList)
  }

  private fun assertModuleScopes(vararg modules: String) {
    for (each in modules) {
      assertModuleSearchScope(each,
                              "${maven.projectPath}/$each/src/main/java",
                              "${maven.projectPath}/$each/src/test/java")
    }
  }

  private fun assertModuleSearchScope(moduleName: String, vararg paths: String) {
    assertSearchScope(moduleName, Scope.MODULE, null, *paths)
  }

  private fun assertAllProductionSearchScope(moduleName: String, vararg paths: String) {
    assertCompileProductionSearchScope(moduleName, *paths)
    assertRuntimeProductionSearchScope(moduleName, *paths)
  }

  private fun assertAllTestsSearchScope(moduleName: String, vararg paths: String) {
    assertCompileTestsSearchScope(moduleName, *paths)
    assertRuntimeTestsSearchScope(moduleName, *paths)
  }

  private fun assertCompileProductionSearchScope(moduleName: String, vararg paths: String) {
    assertSearchScope(moduleName, Scope.COMPILE, Type.PRODUCTION, *paths)
  }

  private fun assertCompileTestsSearchScope(moduleName: String, vararg paths: String) {
    assertSearchScope(moduleName, Scope.COMPILE, Type.TESTS, *paths)
  }

  private fun assertRuntimeProductionSearchScope(moduleName: String, vararg paths: String) {
    assertSearchScope(moduleName, Scope.RUNTIME, Type.PRODUCTION, *paths)
  }

  private fun assertRuntimeTestsSearchScope(moduleName: String, vararg paths: String) {
    assertSearchScope(moduleName, Scope.RUNTIME, Type.TESTS, *paths)
  }

  private fun assertSearchScope(moduleName: String, scope: Scope, type: Type?, vararg expectedPaths: String) {
    createOutputDirectories()
    val module = maven.getModule(moduleName)

    val searchScope = when (scope) {
      Scope.MODULE -> module.moduleScope
      Scope.COMPILE -> module.getModuleWithDependenciesAndLibrariesScope(type == Type.TESTS)
      Scope.RUNTIME -> module.getModuleRuntimeScope(type == Type.TESTS)
    }

    assertSearchScope(searchScope, *expectedPaths)
  }

  private fun assertSearchScope(aSearchScope: GlobalSearchScope, vararg expectedPaths: String) {
    var searchScope = aSearchScope
    if (searchScope is DelegatingGlobalSearchScope) {
      searchScope = ReflectionUtil.getField(DelegatingGlobalSearchScope::class.java, searchScope, GlobalSearchScope::class.java,
                                            "myBaseScope")
    }
    val roots = if (searchScope is ModuleWithDependenciesScope) {
      searchScope.roots
    }
    else {
      (searchScope as LibraryRuntimeClasspathScope).roots
    }
    val entries: MutableList<VirtualFile> = ArrayList(roots)
    entries.removeAll(listOf(*ProjectRootManager.getInstance(maven.project).orderEntries().sdkOnly().classes().roots))

    val actualPaths: MutableList<String> = ArrayList()
    for (each in entries) {
      actualPaths.add(each.presentableUrl)
    }

    assertPaths(expectedPaths, actualPaths)
  }

  private fun assertPaths(expectedPaths: Array<out String>, actualPaths: List<String>) {
    val normalizedActualPaths: MutableList<String> = ArrayList()
    val normalizedExpectedPaths: MutableList<String> = ArrayList()

    for (each in actualPaths) {
      normalizedActualPaths.add(FileUtil.toSystemDependentName(each))
    }
    for (each in expectedPaths) {
      normalizedExpectedPaths.add(FileUtil.toSystemDependentName(each))
    }

    assertOrderedElementsAreEqual(normalizedActualPaths, normalizedExpectedPaths)
  }

  private fun createRepositoryFile(filePath: String) {
    val f = Paths.get(maven.projectPath.toString(), "repo/$filePath")
    f.parent.createDirectories()

    createEmptyJar(f.parent.toString(), f.fileName.toString())
    maven.repositoryPath = maven.createProjectSubDir("repo").toNioPath()
  }

  private fun createOutputDirectories() {
    for (module in getInstance(maven.project).modules) {
      val extension = CompilerModuleExtension.getInstance(module)
      if (extension != null) {
        createDirectoryIfDoesntExist(extension.compilerOutputUrl)
        createDirectoryIfDoesntExist(extension.compilerOutputUrlForTests)
      }
    }
  }

  private fun orderEntriesToOwnerModules(orderEntries: List<OrderEntry>): List<Module> {
    return orderEntries.map { orderEntry: OrderEntry -> orderEntry.ownerModule }
  }

  private fun orderEntriesToDepModules(orderEntries: List<OrderEntry>): List<Module?> {
    return orderEntries.map { orderEntry: OrderEntry? -> if ((orderEntry is ModuleOrderEntry)) orderEntry.module else null }
  }

  private fun createDirectoryIfDoesntExist(url: String?) {
    if (StringUtil.isEmpty(url)) return

    val file = File(FileUtil.toSystemDependentName(VfsUtil.urlToPath(url)))
    if (file.exists()) return

    if (!file.mkdirs()) {
      Assertions.fail<Any>("Cannot create directory $file")
    }
    VirtualFileManager.getInstance().refreshAndFindFileByUrl(url!!)
  }

  companion object {
    @JvmStatic
    fun createEmptyJar(dir: String, name: String) {
      val jar = File(dir, name)
      FileUtil.ensureExists(jar.getParentFile())
      IoTestUtil.createTestJar(jar)

      val digest = sha1()
      digest.update(FileUtil.loadFileBytes(jar))
      val sha1 = digest.digest()

      PrintWriter(File(dir, "$name.sha1"), StandardCharsets.UTF_8).use { out ->
        for (b in sha1) out.printf("%02x", b)
        out.println("  $name")
      }
    }
  }
}
