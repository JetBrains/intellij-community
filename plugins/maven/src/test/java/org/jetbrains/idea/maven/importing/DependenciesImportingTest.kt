// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertExportedDeps
import com.intellij.maven.testFramework.fixtures.assertModuleLibDep
import com.intellij.maven.testFramework.fixtures.assertModuleLibDepScope
import com.intellij.maven.testFramework.fixtures.assertModuleLibDeps
import com.intellij.maven.testFramework.fixtures.assertModuleModuleDepScope
import com.intellij.maven.testFramework.fixtures.assertModuleModuleDeps
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.assertOrderedElementsAreEqual
import com.intellij.maven.testFramework.fixtures.assertProjectLibraries
import com.intellij.maven.testFramework.fixtures.assertProjectLibraryCoordinates
import com.intellij.maven.testFramework.fixtures.assumeMaven3
import com.intellij.maven.testFramework.fixtures.assumeModel_4_0_0
import com.intellij.maven.testFramework.fixtures.assumeVersionMoreThan
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.doImportProjectsAsync
import com.intellij.maven.testFramework.fixtures.envVar
import com.intellij.maven.testFramework.fixtures.getModule
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.importProjectWithProfiles
import com.intellij.maven.testFramework.fixtures.importProjectsAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.mn
import com.intellij.maven.testFramework.fixtures.moduleTag
import com.intellij.maven.testFramework.fixtures.modulesTag
import com.intellij.maven.testFramework.fixtures.projectPath
import com.intellij.maven.testFramework.fixtures.projectsTree
import com.intellij.maven.testFramework.fixtures.removeFromLocalRepository
import com.intellij.maven.testFramework.fixtures.repositoryPathCanonical
import com.intellij.maven.testFramework.fixtures.setIgnoredFilesPathForNextImport
import com.intellij.maven.testFramework.fixtures.updateAllProjects
import com.intellij.maven.testFramework.fixtures.updateAllProjectsFullSync
import com.intellij.maven.testFramework.fixtures.updateModulePom
import com.intellij.maven.testFramework.fixtures.updateProjectPom
import com.intellij.maven.testFramework.fixtures.updateSettingsXmlFully
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.JavadocOrderRootType
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ModuleSourceOrderEntry
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.findOrCreateFile
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.io.createDirectories
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.MavenCustomNioRepositoryHelper
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Arrays
import kotlin.io.path.exists

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class DependenciesImportingTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion,
    skipPluginResolution = false,
  )
  

  @BeforeEach
  fun setUp() {
    maven.projectsManager.initForTests()
  }

  @Test
  fun testLibraryDependency() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>4.0</version>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    maven.assertModules("project")
    maven.assertModuleLibDep("project", "Maven: junit:junit:4.0",
                       "jar://${maven.repositoryPathCanonical}/junit/junit/4.0/junit-4.0.jar!/",
                       "jar://${maven.repositoryPathCanonical}/junit/junit/4.0/junit-4.0-sources.jar!/",
                       "jar://${maven.repositoryPathCanonical}/junit/junit/4.0/junit-4.0-javadoc.jar!/")
    maven.assertProjectLibraryCoordinates("Maven: junit:junit:4.0", "junit", "junit", "4.0")
  }

  @Test
  fun testSystemDependency() = runBlocking {
    maven.importProjectAsync("""
                  <groupId>test</groupId>
                  <artifactId>project</artifactId>
                  <version>1</version>
                  <dependencies>
                    <dependency>
                      <groupId>junit</groupId>
                      <artifactId>junit</artifactId>
                      <version>4.0</version>
                      <scope>system</scope>
                      <systemPath>
                  ${maven.repositoryPath}/junit/junit/4.0/junit-4.0.jar</systemPath>
                    </dependency>
                  </dependencies>
                  """.trimIndent())

    maven.assertModules("project")
    maven.assertModuleLibDep("project", "Maven: junit:junit:4.0",
                       listOf("jar://" + maven.repositoryPathCanonical + "/junit/junit/4.0/junit-4.0.jar!/"),
                       emptyList(), emptyList())
  }

  @Test
  fun testTestJarDependencies() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>4.0</version>
                        <type>test-jar</type>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    maven.assertModules("project")
    maven.assertModuleLibDep("project", "Maven: junit:junit:test-jar:tests:4.0",
                       "jar://" + maven.repositoryPathCanonical + "/junit/junit/4.0/junit-4.0-tests.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/junit/junit/4.0/junit-4.0-test-sources.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/junit/junit/4.0/junit-4.0-test-javadoc.jar!/")
    maven.assertProjectLibraryCoordinates("Maven: junit:junit:test-jar:tests:4.0", "junit", "junit", "tests", "jar", "4.0")
  }

  @Test
  fun testDependencyWithClassifier() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>4.0</version>
                        <classifier>bar</classifier>
                      </dependency>
                    </dependencies>
                    """.trimIndent())
    maven.assertModules("project")
    maven.assertModuleLibDep("project", "Maven: junit:junit:bar:4.0",
                       "jar://" + maven.repositoryPathCanonical + "/junit/junit/4.0/junit-4.0-bar.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/junit/junit/4.0/junit-4.0-sources.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/junit/junit/4.0/junit-4.0-javadoc.jar!/")
    maven.assertProjectLibraryCoordinates("Maven: junit:junit:bar:4.0", "junit", "junit", "bar", "jar", "4.0")
  }


  @Test
  fun testPreservingDependenciesOrder() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>B</groupId>
                        <artifactId>B</artifactId>
                        <version>2</version>
                      </dependency>
                      <dependency>
                        <groupId>A</groupId>
                        <artifactId>A</artifactId>
                        <version>1</version>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    maven.assertModules("project")
    maven.assertModuleLibDeps("project", "Maven: B:B:2", "Maven: A:A:1")
  }

  @Test
  fun testPreservingDependenciesOrderWithTestDependencies() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>a</groupId>
                        <artifactId>compile</artifactId>
                        <version>1</version>
                      </dependency>
                      <dependency>
                        <groupId>a</groupId>
                        <artifactId>test</artifactId>
                        <version>1</version>
                        <scope>test</scope>
                      </dependency>
                      <dependency>
                        <groupId>a</groupId>
                        <artifactId>runtime</artifactId>
                        <version>1</version>
                        <scope>runtime</scope>
                      </dependency>
                      <dependency>
                        <groupId>a</groupId>
                        <artifactId>compile-2</artifactId>
                        <version>1</version>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    maven.assertModules("project")
    maven.assertModuleLibDeps("project",
                        "Maven: a:compile:1", "Maven: a:test:1", "Maven: a:runtime:1", "Maven: a:compile-2:1")
  }

  @Test
  fun testDoNotResetDependenciesIfProjectIsInvalid() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>group</groupId>
                           <artifactId>lib</artifactId>
                           <version>1</version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.importProjectAsync()
    maven.assertModuleLibDeps("project", "Maven: group:lib:1")

    // incomplete tag
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version<dependencies>
                         <dependency>
                           <groupId>group</groupId>
                           <artifactId>lib</artifactId>
                           <version>1</version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.doImportProjectsAsync(listOf(maven.projectPom), false)
    maven.assertModuleLibDeps("project", "Maven: group:lib:1")
  }

  @Test
  fun testInterModuleDependencies() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1", """
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

    maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())

    maven.importProjectAsync()
    maven.assertModules("project", "m1", "m2")

    maven.assertModuleModuleDeps("m1", "m2")
  }

  @Test
  fun testInterModuleDependenciesWithClassifier() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m2</artifactId>
          <version>1</version>
          <classifier>client</classifier>
        </dependency>
      </dependencies>
      """.trimIndent())

    maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())

    maven.importProjectAsync()

    maven.assertModuleModuleDeps("m1", "m2")
    maven.assertModuleLibDep("m1", "Maven: test:m2:client:1",
                       "jar://" + maven.repositoryPathCanonical + "/test/m2/1/m2-1-client.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/test/m2/1/m2-1-sources.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/test/m2/1/m2-1-javadoc.jar!/")
  }

  @Test
  fun testDoNotAddInterModuleDependenciesFoUnsupportedDependencyType() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m2</artifactId>
          <version>1</version>
          <type>xxx</type>
        </dependency>
      </dependencies>
      """.trimIndent())

    maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())

    maven.importProjectAsync()
    maven.assertModules("project", "m1", "m2")

    maven.assertModuleModuleDeps("m1")
  }

  @Test
  fun testInterModuleDependenciesWithoutModuleVersions() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1", """
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

    maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
      """.trimIndent())

    maven.importProjectAsync()
    maven.assertModules("project", "m1", maven.mn("project", "m2"))

    maven.assertModuleModuleDeps("m1", maven.mn("project", "m2"))
  }

  @Test
  fun testInterModuleDependenciesWithVersionRanges() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m2</artifactId>
          <version>[1, 2]</version>
        </dependency>
      </dependencies>
      """.trimIndent())

    maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())

    maven.importProjectAsync()
    maven.assertModules("project", "m1", "m2")

    maven.assertModuleModuleDeps("m1", "m2")
  }

  @Test
  fun testInterModuleDependenciesWithoutModuleGroup() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1", """
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

    maven.createModulePom("m2", """
      <artifactId>m2</artifactId>
      <version>1</version>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
      """.trimIndent())

    maven.importProjectAsync()
    maven.assertModules("project", "m1", maven.mn("project", "m2"))

    maven.assertModuleModuleDeps("m1", maven.mn("project", "m2"))
  }


  @Test
  fun testInterModuleDependenciesIfThereArePropertiesInArtifactHeader() = runBlocking {
    maven.assumeModel_4_0_0("4.1.0 model does not allow such case: - [FATAL] 'artifactId' contains an expression but should be a constant")
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <properties>
                         <module2Name>m2</module2Name>
                       </properties>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1", """
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

    maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>${'$'}{module2Name}</artifactId>
      <version>${'$'}{project.parent.version}</version>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
      """.trimIndent())

    maven.importProjectAsync()
    maven.assertModules("project", "m1", maven.mn("project", "m2"))

    maven.assertModuleModuleDeps("m1", maven.mn("project", "m2"))
  }

  @Test
  fun testInterModuleDependenciesIfThereArePropertiesInArtifactHeaderDefinedInParent() = runBlocking {
    maven.assumeModel_4_0_0("4.1.0 model does not allow such case: - [FATAL] 'groupId' contains an expression but should be a constant")
    maven.createProjectPom("""
                       <groupId>${'$'}{groupProp}</groupId>
                       <artifactId>parent</artifactId>
                       <version>${'$'}{versionProp}</version>
                       <packaging>pom</packaging>
                       <properties>
                         <groupProp>test</groupProp>
                         <versionProp>1</versionProp>
                       </properties>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1",
                    """
                      <parent>
                        <groupId>${'$'}{groupProp}</groupId>
                        <artifactId>parent</artifactId>
                        <version>${'$'}{versionProp}</version>
                      </parent>
                      <artifactId>m1</artifactId>
                      <dependencies>
                        <dependency>
                          <groupId>${'$'}{groupProp}</groupId>
                          <artifactId>m2</artifactId>
                          <version>${'$'}{versionProp}</version>
                        </dependency>
                      </dependencies>
                      """.trimIndent())

    maven.createModulePom("m2",
                    """
                      <parent>
                        <groupId>${'$'}{groupProp}</groupId>
                        <artifactId>parent</artifactId>
                        <version>${'$'}{versionProp}</version>
                      </parent>
                      <artifactId>m2</artifactId>
                      """.trimIndent())

    maven.importProjectAsync()
    maven.assertModules("parent", "m1", "m2")

    maven.assertModuleModuleDeps("m1", "m2")
  }

  @Test
  fun testDependenciesInPerSourceTypeModule() = runBlocking {
    maven.createModulePom("m1",
                    """
                      <groupId>test</groupId>
                      <artifactId>m1</artifactId>
                      <version>1</version>
                      <parent>
                        <groupId>test</groupId>
                        <artifactId>project</artifactId>
                        <version>1</version>
                      </parent>
                      """.trimIndent())

    maven.createModulePom("m2",
                    """
                      <groupId>test</groupId>
                      <artifactId>m2</artifactId>
                      <version>1</version>
                      <parent>
                        <groupId>test</groupId>
                        <artifactId>project</artifactId>
                        <version>1</version>
                      </parent>
                      <dependencies>
                        <dependency>
                          <groupId>test</groupId>
                          <artifactId>m1</artifactId>
                          <version>1</version>
                        </dependency>
                      </dependencies>
                      """.trimIndent())

    maven.importProjectAsync("""
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
                      <module>m2</module>
                    </modules>
                    """.trimIndent())

    maven.assertModules("project",
                  maven.mn("project", "m1"),
                  maven.mn("project", "m2"),
                  maven.mn("project", "m1.main"),
                  maven.mn("project", "m1.test"),
                  maven.mn("project", "m2.main"),
                  maven.mn("project", "m2.test"))
    maven.assertModuleModuleDeps(maven.mn("project", "m1.test"), maven.mn("project", "m1.main"))
    maven.assertModuleModuleDeps(maven.mn("project", "m2.test"), maven.mn("project", "m2.main"), maven.mn("project", "m1.main"))
    maven.assertModuleModuleDeps(maven.mn("project", "m2.main"), maven.mn("project", "m1.main"))
  }

  @Test
  fun testTestDependencyOnPerSourceTypeModule() = runBlocking {
    maven.createModulePom("m1",
                    """
                      <groupId>test</groupId>
                      <artifactId>m1</artifactId>
                      <version>1</version>
                      <properties>
                        <maven.compiler.source>8</maven.compiler.source>
                        <maven.compiler.target>8</maven.compiler.target>
                        <maven.compiler.testSource>11</maven.compiler.testSource>
                        <maven.compiler.testTarget>11</maven.compiler.testTarget>
                      </properties>
                      <parent>
                        <groupId>test</groupId>
                        <artifactId>project</artifactId>
                        <version>1</version>
                      </parent>
                      """.trimIndent())

    maven.createModulePom("m2",
                    """
                      <groupId>test</groupId>
                      <artifactId>m2</artifactId>
                      <version>1</version>
                      <parent>
                        <groupId>test</groupId>
                        <artifactId>project</artifactId>
                        <version>1</version>
                      </parent>
                      <dependencies>
                        <dependency>
                          <groupId>test</groupId>
                          <artifactId>m1</artifactId>
                          <version>1</version>
                          <type>test-jar</type>
                          <scope>test</scope>
                        </dependency>
                      </dependencies>
                      """.trimIndent())

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>m1</module>
                      <module>m2</module>
                    </modules>
                    """.trimIndent())

    maven.assertModules("project",
                  maven.mn("project", "m1"),
                  maven.mn("project", "m2"),
                  maven.mn("project", "m1.main"),
                  maven.mn("project", "m1.test"))
    maven.assertModuleModuleDeps(maven.mn("project", "m2"), maven.mn("project", "m1.test"))
    maven.assertModuleModuleDeps(maven.mn("project", "m1.test"), maven.mn("project", "m1.main"))
  }

  @Test
  fun testDependencyOnSelf() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>test</groupId>
                        <artifactId>project</artifactId>
                        <version>1</version>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    maven.assertModuleModuleDeps("project")
  }

  @Test
  fun testDependencyOnSelfWithPomPackaging() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <dependencies>
                      <dependency>
                        <groupId>test</groupId>
                        <artifactId>project</artifactId>
                        <version>1</version>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    maven.assertModuleModuleDeps("project")
  }

  @Test
  fun testIntermoduleDependencyOnTheSameModuleWithDifferentTypes() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1", """
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

    maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())

    maven.importProjectAsync()
    maven.assertModules("project", "m1", "m2")

    maven.assertModuleModuleDeps("m1", "m2", "m2")
  }

  @Test
  fun testDependencyScopes() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>test</groupId>
                        <artifactId>foo1</artifactId>
                        <version>1</version>
                      </dependency>
                      <dependency>
                        <groupId>test</groupId>
                        <artifactId>foo2</artifactId>
                        <version>1</version>
                        <scope>runtime</scope>
                      </dependency>
                      <dependency>
                        <groupId>test</groupId>
                        <artifactId>foo3</artifactId>
                        <version>1</version>
                        <scope>test</scope>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    maven.assertModuleLibDepScope("project", "Maven: test:foo1:1", DependencyScope.COMPILE)
    maven.assertModuleLibDepScope("project", "Maven: test:foo2:1", DependencyScope.RUNTIME)
    maven.assertModuleLibDepScope("project", "Maven: test:foo3:1", DependencyScope.TEST)
  }

  @Test
  fun testModuleDependencyScopes() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                         <module>m3</module>
                         <module>m4</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1", """
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
          <scope>runtime</scope>
        </dependency>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m4</artifactId>
          <version>1</version>
          <scope>test</scope>
        </dependency>
      </dependencies>
      """.trimIndent())

    maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())
    maven.createModulePom("m3", """
      <groupId>test</groupId>
      <artifactId>m3</artifactId>
      <version>1</version>
      """.trimIndent())
    maven.createModulePom("m4", """
      <groupId>test</groupId>
      <artifactId>m4</artifactId>
      <version>1</version>
      """.trimIndent())

    maven.importProjectAsync()
    maven.assertModules("project", "m1", "m2", "m3", "m4")

    maven.assertModuleModuleDepScope("m1", "m2", DependencyScope.COMPILE)
    maven.assertModuleModuleDepScope("m1", "m3", DependencyScope.RUNTIME)
    maven.assertModuleModuleDepScope("m1", "m4", DependencyScope.TEST)
  }

  @Test
  fun testDependenciesAreNotExported() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1", """
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
          <groupId>lib</groupId>
          <artifactId>lib</artifactId>
          <version>1</version>
        </dependency>
      </dependencies>
      """.trimIndent())

    maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())

    maven.importProjectAsync()
    maven.assertExportedDeps("m1")
  }

  @Test
  fun testTransitiveDependencies() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1", """
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

    maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>group</groupId>
          <artifactId>id</artifactId>
          <version>1</version>
        </dependency>
      </dependencies>
      """.trimIndent())

    maven.importProjectAsync()
    maven.assertModules("project", "m1", "m2")

    maven.assertModuleLibDeps("m2", "Maven: group:id:1")
    maven.assertModuleLibDeps("m1", "Maven: group:id:1")
  }

  @Test
  fun testTransitiveLibraryDependencyVersionResolution() = runBlocking {
    // this test hanles the case when the particular dependency list cause embedder set
    // the versionRange for the xml-apis:xml-apis:1.0.b2 artifact to null.
    // see http://jira.codehaus.org/browse/MNG-3386

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>dom4j</groupId>
                        <artifactId>dom4j</artifactId>
                        <version>1.6.1</version>
                        <scope>runtime</scope>
                      </dependency>
                      <dependency>
                         <groupId>org.apache.ws.commons.util</groupId>
                         <artifactId>ws-commons-util</artifactId>
                         <version>1.0.2</version>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    maven.assertModules("project")
    maven.assertModuleLibDep("project", "Maven: xml-apis:xml-apis:1.0.b2")
  }

  @Test
  fun testIncrementalSyncTransitiveLibraryDependencyManagement() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencyManagement>
                      <dependencies>
                        <dependency>
                          <groupId>asm</groupId>
                          <artifactId>asm</artifactId>
                          <version>3.3.0</version>
                        </dependency>       
                      </dependencies>
                    </dependencyManagement>                    
                    <dependencies>
                      <dependency>
                        <groupId>asm</groupId>
                        <artifactId>asm-attrs</artifactId>
                        <version>2.2.1</version>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    maven.assertModules("project")
    maven.assertModuleLibDeps("project", "Maven: asm:asm-attrs:2.2.1", "Maven: asm:asm:3.3.0")

    maven.updateProjectPom("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencyManagement>
                      <dependencies>
                        <dependency>
                          <groupId>asm</groupId>
                          <artifactId>asm</artifactId>
                          <version>3.3.1</version>
                        </dependency>       
                      </dependencies>
                    </dependencyManagement>                    
                    <dependencies>
                      <dependency>
                        <groupId>asm</groupId>
                        <artifactId>asm-attrs</artifactId>
                        <version>2.2.1</version>
                      </dependency>
                    </dependencies>
                    """.trimIndent())
    maven.updateAllProjects()

    maven.assertModuleLibDeps("project", "Maven: asm:asm-attrs:2.2.1", "Maven: asm:asm:3.3.1")
  }

  @Test
  fun testExclusionOfTransitiveDependencies() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m2</artifactId>
          <version>1</version>
            <exclusions>
              <exclusion>
                <groupId>group</groupId>
                <artifactId>id</artifactId>
              </exclusion>
            </exclusions>
        </dependency>
      </dependencies>
      """.trimIndent())

    maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>group</groupId>
          <artifactId>id</artifactId>
          <version>1</version>
        </dependency>
      </dependencies>
      """.trimIndent())
    maven.importProjectAsync()

    maven.assertModuleLibDeps("m2", "Maven: group:id:1")

    maven.assertModuleModuleDeps("m1", "m2")
    maven.assertModuleLibDeps("m1")
  }

  @Test
  fun testDependencyWithEnvironmentProperty() = runBlocking {
    val javaHome = FileUtil.toSystemIndependentName(System.getProperty("java.home"))

    val javaHomePath = Paths.get(javaHome)
    val firstJar = Files.walk(javaHomePath)
      .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith("jar") }
      .findFirst()
      .orElse(null)!!
      .toCanonicalPath()
      .substring(javaHome.length + 1)

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>direct-system-dependency</groupId>
                           <artifactId>direct-system-dependency</artifactId>
                           <version>1.0</version>
                           <scope>system</scope>
                           <systemPath>${'$'}{java.home}/$firstJar</systemPath>
                         </dependency>
                       </dependencies>
                       """.trimIndent())
    maven.doImportProjectsAsync(listOf(maven.projectPom), false)

    maven.assertModules("project")
    maven.assertModuleLibDep("project",
                       "Maven: direct-system-dependency:direct-system-dependency:1.0",
                       "jar://$javaHome/$firstJar!/")
  }

  private fun createFileByRelativePath(dir: Path, relativePath: String): VirtualFile {
    val f = dir.resolve(relativePath)
    f.parent.createDirectories()
    f.findOrCreateFile()
    return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(f)!!
  }

  @Test
  fun testDependencyWithEnvironmentENVProperty() = runBlocking {
    var envDir = FileUtil.toSystemIndependentName(System.getenv(maven.envVar))
    envDir = StringUtil.trimEnd(envDir, "/")

    val envPath = Paths.get(envDir)
    val relativePath = "testDependencyWithEnvironmentENVProperty/foo.jar"
    createFileByRelativePath(envPath, relativePath)

    maven.createProjectPom("""<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
<dependencies>
  <dependency>
    <groupId>direct-system-dependency</groupId>
    <artifactId>direct-system-dependency</artifactId>
    <version>1.0</version>
    <scope>system</scope>
    <systemPath>${"$"}{env.${maven.envVar}}/$relativePath</systemPath>
  </dependency>
</dependencies>
""")
    maven.doImportProjectsAsync(listOf(maven.projectPom), false)

    maven.assertModules("project")
    maven.assertModuleLibDep("project",
                       "Maven: direct-system-dependency:direct-system-dependency:1.0",
                       "jar://$envDir/$relativePath!/")
  }

  @Test
  fun testDependencyWithVersionRangeOnModule() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m2</artifactId>
          <version>[1, 3]</version>
        </dependency>
      </dependencies>
      """.trimIndent())

    maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>2</version>
      """.trimIndent())

    maven.importProjectAsync()

    maven.assertModules("project", "m1", "m2")

    maven.assertModuleModuleDeps("m1", "m2")
    maven.assertModuleLibDeps("m1")
  }

  @Test
  fun testPropertiesInInheritedDependencies() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <dependencies>
                         <dependency>
                           <groupId>group</groupId>
                           <artifactId>lib</artifactId>
                           <version>${'$'}{project.version}</version>
                         </dependency>
                       </dependencies>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>2</version>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
      """.trimIndent())

    maven.importProjectAsync()

    maven.assertModuleLibDep(maven.mn("project", "m"), "Maven: group:lib:2")
  }

  @Test
  fun testPropertyInTheModuleDependency() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <properties>
                         <dep-version>1.2.3</dep-version>
                       </properties>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent()
    )

    maven.createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
      <dependencies>
        <dependency>
          <groupId>group</groupId>
          <artifactId>id</artifactId>
          <version>${'$'}{dep-version}</version>
        </dependency>
      </dependencies>
      """.trimIndent())

    maven.importProjectAsync()

    maven.assertModules("project", maven.mn("project", "m"))
    maven.assertModuleLibDeps(maven.mn("project", "m"), "Maven: group:id:1.2.3")
  }

  @Test
  fun testManagedModuleDependency() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <dependencyManagement>
                         <dependencies>
                           <dependency>
                             <groupId>group</groupId>
                             <artifactId>id</artifactId>
                             <version>1</version>
                           </dependency>
                         </dependencies>
                       </dependencyManagement>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
      <dependencies>
        <dependency>
          <groupId>group</groupId>
          <artifactId>id</artifactId>
        </dependency>
      </dependencies>
      """.trimIndent())

    maven.importProjectAsync()
    maven.assertModuleLibDeps(maven.mn("project", "m"), "Maven: group:id:1")
  }

  @Test
  fun testPropertyInTheManagedModuleDependencyVersion() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <properties>
                         <dep-version>1</dep-version>
                       </properties>
                       <dependencyManagement>
                         <dependencies>
                           <dependency>
                             <groupId>group</groupId>
                             <artifactId>id</artifactId>
                             <version>${'$'}{dep-version}</version>
                           </dependency>
                         </dependencies>
                       </dependencyManagement>
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
      <dependencies>
        <dependency>
          <groupId>group</groupId>
          <artifactId>id</artifactId>
        </dependency>
      </dependencies>
      """.trimIndent())

    maven.importProjectAsync()

    maven.assertModules("project", maven.mn("project", "m"))
    maven.assertModuleLibDeps(maven.mn("project", "m"), "Maven: group:id:1")
  }

  @Test
  fun testPomTypeDependency() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <version>4.0</version>
                           <type>pom</type>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.importProjectAsync() // shouldn't throw any exception
  }

  @Test
  fun testPropertyInTheManagedModuleDependencyVersionOfPomType() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <properties>
                         <version>1</version>
                       </properties>
                       <dependencyManagement>
                         <dependencies>
                           <dependency>
                             <groupId>xxx</groupId>
                             <artifactId>yyy</artifactId>
                             <version>${'$'}{version}</version>
                             <type>pom</type>
                           </dependency>
                         </dependencies>
                       </dependencyManagement>
       
                       <${maven.modulesTag}>
                         <${maven.moduleTag}>m</${maven.moduleTag}>
                       </${maven.modulesTag}>
                       """.trimIndent())

    maven.createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
      <dependencies>
        <dependency>
          <groupId>xxx</groupId>
          <artifactId>yyy</artifactId>
          <type>pom</type>
        </dependency>
      </dependencies>
      """.trimIndent())

    maven.doImportProjectsAsync(listOf(maven.projectPom), false)

    maven.assertModules("project", maven.mn("project", "m"))
    maven.assertModuleLibDeps(maven.mn("project", "m"))


    val root = maven.projectsTree.rootProjects[0]
    val modules = maven.projectsTree.getModules(root)

    assertOrderedElementsAreEqual(root.problems)
    assertTrue(modules[0].problems[0].description!!.contains("Unresolved dependency: 'xxx:yyy:pom:1'"))
  }

  @Test
  fun testResolvingFromRepositoriesIfSeveral() = runBlocking {
    val fixture = MavenCustomNioRepositoryHelper(maven.dir, "local1")
    maven.repositoryPath = fixture.getTestData("local1")
    maven.removeFromLocalRepository("junit")

    val file = fixture.getTestData("local1/junit/junit/4.0/junit-4.0.pom")
    assertFalse(file.exists())

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                      <repositories>
                        <repository>
                          <id>foo</id>
                          <url>http://foo.bar</url>
                        </repository>
                      </repositories>
                    <dependencies>
                      <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>4.0</version>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    assertTrue(file.exists())
  }

  @Test
  fun testUsingMirrors() = runBlocking {
    maven.repositoryPath = maven.dir.resolve("repo")
    val mirrorPath = org.jetbrains.idea.maven.server.RemotePathTransformerFactory.createForProject(maven.project).toRemotePath(maven.dir.resolve("mirror").toCanonicalPath())

    maven.updateSettingsXmlFully("""<settings>
  <mirrors>
    <mirror>
      <id>foo</id>
      <url>file://$mirrorPath</url>
      <mirrorOf>*</mirrorOf>
    </mirror>
  </mirrors>
</settings>
""")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>4.0</version>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    assertTrue(maven.projectsTree.findProject(maven.projectPom)!!.hasUnresolvedArtifacts())
  }

  @Test
  fun testCanResolveDependenciesWhenExtensionPluginNotFound() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <version>4.0</version>
                         </dependency>
                       </dependencies>
                       <build>
                        <plugins>
                          <plugin>
                            <groupId>xxx</groupId>
                            <artifactId>yyy</artifactId>
                            <version>1</version>
                            <extensions>true</extensions>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())
    maven.doImportProjectsAsync(listOf(maven.projectPom), false)

    maven.assertModuleLibDep("project", "Maven: junit:junit:4.0")
  }

  @Test
  fun testDoNotRemoveLibrariesOnImportIfProjectWasNotChanged() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>4.0</version>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    maven.assertProjectLibraries("Maven: junit:junit:4.0")
    maven.assertModuleLibDeps("project", "Maven: junit:junit:4.0")

    maven.updateAllProjects()

    maven.assertProjectLibraries("Maven: junit:junit:4.0")
    maven.assertModuleLibDeps("project", "Maven: junit:junit:4.0")
  }

  @Test
  fun testDoNotCreateSameLibraryTwice() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>4.0</version>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    maven.importProjectAsync()

    maven.assertProjectLibraries("Maven: junit:junit:4.0")
    maven.assertModuleLibDeps("project", "Maven: junit:junit:4.0")
  }

  @Test
  fun testCreateSeparateLibraryForDifferentArtifactTypeAndClassifier() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>4.0</version>
                      </dependency>
                      <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>4.0</version>
                        <type>test-jar</type>
                      </dependency>
                      <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>4.0</version>
                        <classifier>jdk5</classifier>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    maven.assertProjectLibraries("Maven: junit:junit:4.0",
                           "Maven: junit:junit:test-jar:tests:4.0",
                           "Maven: junit:junit:jdk5:4.0")
    maven.assertModuleLibDeps("project",
                        "Maven: junit:junit:4.0",
                        "Maven: junit:junit:test-jar:tests:4.0",
                        "Maven: junit:junit:jdk5:4.0")
  }

  @Test
  fun testRemoveUnnecessaryMavenizedModuleDepsOnRepomport() = runBlocking {
    val m1 = maven.createModulePom("m1",
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
                                       """.trimIndent())
    val m2 = maven.createModulePom("m2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m2</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    maven.importProjectsAsync(m1, m2)
    maven.assertModuleModuleDeps("m1", "m2")

    maven.updateModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      """.trimIndent())

    maven.updateAllProjects()
    maven.assertModuleModuleDeps("m1")
  }

  @Test
  fun testDifferentSystemDependenciesWithSameId() = runBlocking {
    maven.createProjectSubFile("m1/foo.jar")
    maven.createProjectSubFile("m2/foo.jar")
    val pp = maven.projectPath.toCanonicalPath()

    maven.createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>xxx</groupId>
          <artifactId>yyy</artifactId>
          <version>1</version>
          <scope>system</scope>
          <systemPath>${pp}/m1/foo.jar</systemPath>
        </dependency>
      </dependencies>
      """)
    maven.createModulePom("m2", """<groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>xxx</groupId>
          <artifactId>yyy</artifactId>
          <version>1</version>
          <scope>system</scope>
          <systemPath>${pp}/m2/foo.jar</systemPath>
        </dependency>
      </dependencies>
      """.trimIndent())

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())
    maven.doImportProjectsAsync(listOf(maven.projectPom), false)

    maven.assertModuleLibDep("m1", "Maven: xxx:yyy:1", "jar://$pp/m1/foo.jar!/")
    maven.assertModuleLibDep("m2", "Maven: xxx:yyy:1", "jar://$pp/m2/foo.jar!/")
  }

  @Test
  fun testDoNotPopulateSameRootEntriesOnEveryImport() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>4.0</version>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    maven.assertModuleLibDep("project", "Maven: junit:junit:4.0",
                       Arrays.asList("jar://" + maven.repositoryPathCanonical + "/junit/junit/4.0/junit-4.0.jar!/"),
                       Arrays.asList("jar://" + maven.repositoryPathCanonical + "/junit/junit/4.0/junit-4.0-sources.jar!/"),
                       Arrays.asList("jar://" + maven.repositoryPathCanonical + "/junit/junit/4.0/junit-4.0-javadoc.jar!/"))

    // update twice
    maven.updateAllProjects()
    maven.updateAllProjects()

    maven.assertModuleLibDep("project", "Maven: junit:junit:4.0",
                       Arrays.asList("jar://" + maven.repositoryPathCanonical + "/junit/junit/4.0/junit-4.0.jar!/"),
                       Arrays.asList("jar://" + maven.repositoryPathCanonical + "/junit/junit/4.0/junit-4.0-sources.jar!/"),
                       Arrays.asList("jar://" + maven.repositoryPathCanonical + "/junit/junit/4.0/junit-4.0-javadoc.jar!/"))
  }

  @Test
  fun testDoNotPopulateSameRootEntriesOnEveryImportForSystemLibraries() = runBlocking {
    maven.createProjectSubFile("foo/bar.jar")
    val pp = maven.projectPath.toCanonicalPath()
    val path = "jar://$pp/foo/bar.jar!/"
    runBlocking {
      maven.createProjectPom("""
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
        <dependencies>
          <dependency>
            <groupId>xxx</groupId>
            <artifactId>yyy</artifactId>
            <version>1</version>
            <scope>system</scope>
            <systemPath>$pp/foo/bar.jar</systemPath>
          </dependency>
        </dependencies>
        """.trimIndent())
      maven.doImportProjectsAsync(listOf(maven.projectPom), false)

      maven.assertModuleLibDep("project", "Maven: xxx:yyy:1", listOf(path), emptyList(), emptyList())

      // update twice
      maven.updateAllProjects()
      maven.updateAllProjects()

      maven.assertModuleLibDep("project", "Maven: xxx:yyy:1", listOf(path), emptyList(), emptyList())
    }
  }

  @Test
  fun testRemovingPreviousSystemPathForForSystemLibraries() = runBlocking {
    maven.createProjectSubFile("foo/bar.jar")
    maven.createProjectSubFile("foo/xxx.jar")

    maven.createProjectPom("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>xxx</groupId>
          <artifactId>yyy</artifactId>
          <version>1</version>
          <scope>system</scope>
          <systemPath>${maven.projectPath}/foo/bar.jar</systemPath>
        </dependency>
      </dependencies>
      """.trimIndent())
    maven.doImportProjectsAsync(listOf(maven.projectPom), false)

    maven.assertModuleLibDep("project", "Maven: xxx:yyy:1",
                       listOf("jar://${maven.projectPath}/foo/bar.jar!/"),
                       emptyList(),
                       emptyList())

    maven.updateProjectPom("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>xxx</groupId>
          <artifactId>yyy</artifactId>
          <version>1</version>
          <scope>system</scope>
          <systemPath>${maven.projectPath}/foo/xxx.jar</systemPath>
        </dependency>
      </dependencies>
      """.trimIndent())

    maven.updateAllProjects()

    maven.assertModuleLibDep("project", "Maven: xxx:yyy:1",
                       listOf("jar://${maven.projectPath}/foo/xxx.jar!/"),
                       emptyList(),
                       emptyList())
  }

  @Test
  fun testRemovingUnusedLibraries() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>group</groupId>
          <artifactId>lib1</artifactId>
          <version>1</version>
        </dependency>
        <dependency>
          <groupId>group</groupId>
          <artifactId>lib2</artifactId>
          <version>1</version>
        </dependency>
      </dependencies>
      """.trimIndent())

    maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>group</groupId>
          <artifactId>lib2</artifactId>
          <version>1</version>
        </dependency>
        <dependency>
          <groupId>group</groupId>
          <artifactId>lib3</artifactId>
          <version>1</version>
        </dependency>
        <dependency>
          <groupId>group</groupId>
          <artifactId>lib4</artifactId>
          <version>1</version>
        </dependency>
      </dependencies>
      """.trimIndent())

    maven.importProjectAsync()
    maven.assertProjectLibraries("Maven: group:lib1:1",
                           "Maven: group:lib2:1",
                           "Maven: group:lib3:1",
                           "Maven: group:lib4:1")

    maven.updateModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>group</groupId>
          <artifactId>lib2</artifactId>
          <version>1</version>
        </dependency>
      </dependencies>
      """.trimIndent())

    maven.updateModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>group</groupId>
          <artifactId>lib3</artifactId>
          <version>1</version>
        </dependency>
      </dependencies>
      """.trimIndent())

    maven.updateAllProjects()
    maven.assertProjectLibraries("Maven: group:lib2:1",
                           "Maven: group:lib3:1")
  }

  @Test
  fun testDoNoRemoveUnusedLibraryIfItWasChanged() = runBlocking {

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>group</groupId>
                        <artifactId>lib1</artifactId>
                        <version>1</version>
                      </dependency>
                      <dependency>
                        <groupId>group</groupId>
                        <artifactId>lib2</artifactId>
                        <version>1</version>
                      </dependency>
                      <dependency>
                        <groupId>group</groupId>
                        <artifactId>lib3</artifactId>
                        <version>1</version>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    maven.assertProjectLibraries("Maven: group:lib1:1",
                           "Maven: group:lib2:1",
                           "Maven: group:lib3:1")

    addLibraryRoot("Maven: group:lib1:1", JavadocOrderRootType.getInstance(), "file://foo.bar")
    clearLibraryRoots("Maven: group:lib2:1", JavadocOrderRootType.getInstance())
    addLibraryRoot("Maven: group:lib2:1", JavadocOrderRootType.getInstance(), "file://foo.baz")

    maven.updateProjectPom("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    maven.updateAllProjects()

    maven.assertProjectLibraries()
  }

  @Test
  fun testDoNoRemoveUserProjectLibraries() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    createAndAddProjectLibrary("project", "lib")

    maven.assertProjectLibraries("lib")
    addLibraryRoot("lib", OrderRootType.CLASSES, "file://" + maven.repositoryPathCanonical + "/foo/bar.jar!/")

    maven.updateProjectPom("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    maven.updateAllProjects()

    maven.assertProjectLibraries("lib")

    maven.assertModuleLibDeps("project")
  }

  @Test
  fun testDoNoRemoveUnusedUserProjectLibraries() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    createProjectLibrary("lib")
    addLibraryRoot("lib", OrderRootType.CLASSES, "file://" + maven.repositoryPathCanonical + "/foo/bar.jar!/")

    maven.assertProjectLibraries("lib")

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    maven.assertProjectLibraries("lib")
  }

  @Test
  fun testRemovingUnusedLibrariesIfProjectRemoved() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>group</groupId>
          <artifactId>lib1</artifactId>
          <version>1</version>
        </dependency>
      </dependencies>
      """.trimIndent())

    maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>group</groupId>
          <artifactId>lib2</artifactId>
          <version>1</version>
        </dependency>
      </dependencies>
      """.trimIndent())

    maven.importProjectAsync()
    maven.assertProjectLibraries("Maven: group:lib1:1",
                           "Maven: group:lib2:1")

    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                       </modules>
                       """.trimIndent())

    maven.updateAllProjects()
    maven.assertProjectLibraries("Maven: group:lib1:1")
  }

  @Test
  fun testRemovingUnusedLibraryWithClassifier() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>group</groupId>
                        <artifactId>lib1</artifactId>
                        <version>1</version>
                        <classifier>tests</classifier>
                      </dependency>
                      <dependency>
                        <groupId>group</groupId>
                        <artifactId>lib2</artifactId>
                        <version>1</version>
                        <type>test-jar</type>
                        <classifier>tests</classifier>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    maven.assertProjectLibraries("Maven: group:lib1:tests:1",
                           "Maven: group:lib2:test-jar:tests:1")

    maven.updateProjectPom("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    maven.updateAllProjects()

    maven.assertProjectLibraries()
  }

  private fun createProjectLibrary(libraryName: String): Library {
    val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(maven.project)
    return WriteAction.computeAndWait<Library, RuntimeException> { libraryTable.createLibrary(libraryName) }
  }

  private fun createAndAddProjectLibrary(moduleName: String, libraryName: String) {
    WriteAction.runAndWait<RuntimeException> {
      val lib = createProjectLibrary(libraryName)
      ModuleRootModificationUtil.addDependency(maven.getModule(moduleName), lib)
    }
  }

  private fun clearLibraryRoots(libraryName: String, vararg types: OrderRootType) {
    val lib = LibraryTablesRegistrar.getInstance().getLibraryTable(maven.project).getLibraryByName(libraryName)
    WriteAction.runAndWait<RuntimeException> {
      val model = lib!!.getModifiableModel()
      for (eachType in types) {
        for (each in model.getUrls(eachType)) {
          model.removeRoot(each!!, eachType)
        }
      }
      model.commit()
    }
  }

  private fun addLibraryRoot(libraryName: String, type: OrderRootType, path: String) {
    val lib = LibraryTablesRegistrar.getInstance().getLibraryTable(maven.project).getLibraryByName(libraryName)
    WriteAction.runAndWait<RuntimeException> {
      val model = lib!!.getModifiableModel()
      model.addRoot(path, type)
      model.commit()
    }
  }

  @Test
  fun testEjbDependenciesInJarProject() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>foo</groupId>
                        <artifactId>foo</artifactId>
                        <version>1</version>
                        <type>ejb</type>
                      </dependency>
                      <dependency>
                        <groupId>foo</groupId>
                        <artifactId>bar</artifactId>
                        <version>1</version>
                        <type>ejb-client</type>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    maven.assertModules("project")
    maven.assertModuleLibDeps("project", "Maven: foo:foo:ejb:1", "Maven: foo:bar:ejb-client:client:1")
    maven.assertProjectLibraryCoordinates("Maven: foo:foo:ejb:1", "foo", "foo", null, "ejb", "1")
    maven.assertProjectLibraryCoordinates("Maven: foo:bar:ejb-client:client:1", "foo", "bar", "client", "ejb", "1")
  }

  @Test
  fun testDoNotFailOnAbsentAppLibrary() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    ApplicationManager.getApplication().invokeAndWait {
      ApplicationManager.getApplication().runWriteAction {
        val appTable = LibraryTablesRegistrar.getInstance().getLibraryTable()
        val lib = appTable.createLibrary("foo")
        ModuleRootModificationUtil.addDependency(maven.getModule("project"), lib)
        appTable.removeLibrary(lib)
      }
    }

    maven.importProjectAsync() // should not fail;
  }

  @Test
  fun testDoNotFailToConfigureUnresolvedVersionRangeDependencies() = runBlocking {
    // should not throw NPE when accessing CustomArtifact.getPath();
    val helper = MavenCustomNioRepositoryHelper(maven.dir, "local1")
    val repoPath = helper.getTestData("local1")
    maven.repositoryPath = repoPath

    maven.importProjectAsync("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>junit</groupId>
          <artifactId>junit</artifactId>
          <version>[3.8.1,3.8.2]</version>
        </dependency>
        <dependency>
          <groupId>org.apache.maven.errortest</groupId>
          <artifactId>dep</artifactId>
          <version>1</version>
          <type>pom</type>
        </dependency>
      </dependencies>
      <repositories>
        <repository>
          <id>central</id>
          <url>file://localhost/$repoPath</url>
        </repository>
      </repositories>
      """.trimIndent())

    maven.assertModuleLibDeps("project", "Maven: junit:junit:3.8.2")
    maven.assertModuleLibDep("project", "Maven: junit:junit:3.8.2",
                       "jar://$repoPath/junit/junit/3.8.2/junit-3.8.2.jar!/")
  }

  @Test
  fun testVersionRangeInDependencyManagementDoesntBreakIndirectDependency() = runBlocking {
    val helper = MavenCustomNioRepositoryHelper(maven.dir, "local1")
    val repoPath = helper.getTestData("local1")
    maven.repositoryPath = repoPath

    maven.createProjectPom("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      <modules>
        <module>m</module>
      </modules>
      <dependencyManagement>
        <dependencies>
          <dependency>
            <artifactId>asm</artifactId>
            <groupId>asm</groupId>
            <version>[2.2.1]</version>
            <scope>runtime</scope>
          </dependency>
          <dependency>
            <artifactId>asm-attrs</artifactId>
            <groupId>asm</groupId>
            <version>[2.2.1]</version>
            <scope>runtime</scope>
          </dependency>
        </dependencies>
      </dependencyManagement>""".trimIndent())

    maven.createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>1</version>    
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
      <dependencies>
        <dependency>
          <artifactId>asm-attrs</artifactId>
          <groupId>asm</groupId>
          <scope>test</scope>
        </dependency>
      </dependencies>""".trimIndent())

    maven.importProjectAsync()

    maven.assertModuleLibDeps(maven.mn("project", "m"), "Maven: asm:asm-attrs:2.2.1", "Maven: asm:asm:2.2.1")
  }

  @Test
  fun testDependencyToIgnoredProject() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m2</artifactId>
          <version>2</version>
        </dependency>
      </dependencies>
      """.trimIndent())

    val m2 = maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>2</version>
      """.trimIndent())

    maven.importProjectAsync()

    maven.assertModules("project", "m1", "m2")
    maven.assertModuleModuleDeps("m1", "m2")

    maven.setIgnoredFilesPathForNextImport(listOf(m2.getPath()))

    maven.updateAllProjects()

    maven.assertModules("project", "m1")
    maven.assertModuleLibDeps("m1", "Maven: test:m2:2")

    maven.assertModuleLibDep("m1", "Maven: test:m2:2",
                       "jar://" + maven.repositoryPathCanonical + "/test/m2/2/m2-2.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/test/m2/2/m2-2-sources.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/test/m2/2/m2-2-javadoc.jar!/")
  }

  @Test
  fun testSaveJdkPosition() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1", """
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
          <groupId>junit</groupId>
          <artifactId>junit</artifactId>
          <version>4.0</version>
        </dependency>
      </dependencies>
      """.trimIndent())

    maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())

    maven.importProjectAsync()

    WriteAction.runAndWait<RuntimeException> {
      val rootModel = ModuleRootManager.getInstance(maven.getModule("m1")).getModifiableModel()
      val orderEntries = rootModel.getOrderEntries().clone()
      assert(orderEntries.size == 4)
      assert(orderEntries[0] is JdkOrderEntry)
      assert(orderEntries[1] is ModuleSourceOrderEntry)
      assert((orderEntries[2] as ModuleOrderEntry).getModuleName() == "m2")
      assert("Maven: junit:junit:4.0" == (orderEntries[3] as LibraryOrderEntry).getLibraryName())
      rootModel.rearrangeOrderEntries(arrayOf(orderEntries[2], orderEntries[3], orderEntries[0], orderEntries[1]))
      rootModel.commit()
    }

    // JDK position was saved
    val orderEntries = ModuleRootManager.getInstance(maven.getModule("m1")).getOrderEntries()
    assert(orderEntries.size == 4)
    assert((orderEntries[0] as ModuleOrderEntry).getModuleName() == "m2")
    assert("Maven: junit:junit:4.0" == (orderEntries[1] as LibraryOrderEntry).getLibraryName())
    assert(orderEntries[2] is JdkOrderEntry)
    assert(orderEntries[3] is ModuleSourceOrderEntry)
  }

  @Test
  fun testSaveJdkPositionSystemDependency() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>m1</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>test</groupId>
                           <artifactId>systemDep</artifactId>
                           <version>1</version>
                           <scope>system</scope>
                           <systemPath>${'$'}{java.home}/lib/rt.jar</systemPath>
                         </dependency>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <version>4.0</version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())
    maven.doImportProjectsAsync(listOf(maven.projectPom), false)

    WriteAction.runAndWait<RuntimeException> {
      val rootModel = ModuleRootManager.getInstance(maven.getModule("m1")).getModifiableModel()
      val orderEntries = rootModel.getOrderEntries().clone()
      assert(orderEntries.size == 4)
      assert(orderEntries[0] is JdkOrderEntry)
      assert(orderEntries[1] is ModuleSourceOrderEntry)
      assert("Maven: test:systemDep:1" == (orderEntries[2] as LibraryOrderEntry).getLibraryName())
      assert("Maven: junit:junit:4.0" == (orderEntries[3] as LibraryOrderEntry).getLibraryName())
      rootModel.rearrangeOrderEntries(arrayOf(orderEntries[2], orderEntries[3], orderEntries[0], orderEntries[1]))
      rootModel.commit()
    }

    // JDK position was saved
    val orderEntries = ModuleRootManager.getInstance(maven.getModule("m1")).getOrderEntries()
    assert(orderEntries.size == 4)
    assert("Maven: test:systemDep:1" == (orderEntries[0] as LibraryOrderEntry).getLibraryName())
    assert("Maven: junit:junit:4.0" == (orderEntries[1] as LibraryOrderEntry).getLibraryName())
    assert(orderEntries[2] is JdkOrderEntry)
    assert(orderEntries[3] is ModuleSourceOrderEntry)
  }

  @Test
  fun testBundleDependencyType() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>com.google.guava</groupId>
                        <artifactId>guava</artifactId>
                        <version>15.0</version>
                        <type>bundle</type>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    maven.assertProjectLibraries("Maven: com.google.guava:guava:15.0")
    maven.assertModuleLibDep("project", "Maven: com.google.guava:guava:15.0",
                       "jar://" + maven.repositoryPathCanonical + "/com/google/guava/guava/15.0/guava-15.0.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/com/google/guava/guava/15.0/guava-15.0-sources.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/com/google/guava/guava/15.0/guava-15.0-javadoc.jar!/")
  }

  @Test
  fun testReimportingInheritedLibrary() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                       </modules>
                       <dependencies>
                         <dependency>
                           <groupId>junit1</groupId><artifactId>junit1</artifactId><version>3.3</version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.createModulePom("m1", """
      <parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          
          <version>1</version>
        </parent>
      <artifactId>m1</artifactId>
      """.trimIndent())


    maven.importProjectAsync()

    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                       </modules>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId><artifactId>junit</artifactId><version>4.0</version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.updateAllProjects()

    maven.assertModuleLibDep("project", "Maven: junit:junit:4.0",
                       "jar://" + maven.repositoryPathCanonical + "/junit/junit/4.0/junit-4.0.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/junit/junit/4.0/junit-4.0-sources.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/junit/junit/4.0/junit-4.0-javadoc.jar!/")

    maven.assertModuleLibDep(maven.mn("project", "m1"), "Maven: junit:junit:4.0",
                       "jar://" + maven.repositoryPathCanonical + "/junit/junit/4.0/junit-4.0.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/junit/junit/4.0/junit-4.0-sources.jar!/",
                       "jar://" + maven.repositoryPathCanonical + "/junit/junit/4.0/junit-4.0-javadoc.jar!/")
  }

  @Test
  fun testRemoveInvalidOrderEntry() = runBlocking {
    val value = Registry.get("maven.always.remove.bad.entries")
    try {
      value.setValue(true)
      maven.createProjectPom("""
                         <groupId>test</groupId>
                         <artifactId>project</artifactId>
                         <packaging>pom</packaging>
                         <version>1</version>
                         <dependencies>
                           <dependency>
                             <groupId>junit</groupId>
                             <artifactId>junit</artifactId>
                             <version>4.0</version>
                           </dependency>
                         </dependencies>
                         """.trimIndent())
      maven.importProjectAsync()

      edtWriteAction {
        val modifiableModel = ModuleRootManager.getInstance(maven.getModule("project")).getModifiableModel()
        modifiableModel.addInvalidLibrary("SomeLibrary", LibraryTablesRegistrar.PROJECT_LEVEL)
        modifiableModel.addInvalidLibrary("Maven: AnotherLibrary", LibraryTablesRegistrar.PROJECT_LEVEL)
        modifiableModel.commit()
      }

      // incremental sync doesn't update module if effective pom dependencies haven't changed
      maven.updateAllProjectsFullSync()

      maven.assertModuleLibDeps("project", "Maven: junit:junit:4.0")
    }
    finally {
      value.resetToDefault()
    }
  }

  @Test
  fun testTransitiveProfileDependency() = runBlocking {
    maven.assumeVersionMoreThan("3.1.0")
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <profiles>
        <profile>
          <id>test</id>
          <dependencies>
            <dependency>
              <groupId>junit</groupId>
              <artifactId>junit</artifactId>
              <version>4.0</version>
            </dependency>
          </dependencies>
        </profile>
      </profiles>
      """.trimIndent())

    maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m1</artifactId>
          <version>1</version>
        </dependency>
      </dependencies>
      """.trimIndent())

    maven.importProjectWithProfiles("test")
    maven.assertModuleLibDeps("m2", "Maven: junit:junit:4.0")
  }

  @Test
  fun testAttachedJarDependency() = runBlocking {
    // IDEA-86815 Recognize attached jar as library dependency

    maven.createModulePom("m1", """
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

    val file = maven.createProjectSubFile("m1/src/main/java/Foo.java",
                                    """
                                      class Foo {
                                        void foo() {
                                          junit.framework.TestCase a = null;
                                          junit.framework.<error>TestCase123</error> b = null;
                                        }
                                      }
                                      """.trimIndent())

    val jarPath = PlatformTestUtil.getCommunityPath() + "/plugins/maven/src/test/data/local1/junit/junit/3.8.1/junit-3.8.1.jar"

    maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
        <build>
          <plugins>
            <plugin>
              <groupId>org.codehaus.mojo</groupId>
              <artifactId>build-helper-maven-plugin</artifactId>
              <executions>
                <execution>
                  <phase>compile</phase>
                  <goals>
                    <goal>attach-artifact</goal>
                  </goals>
                  <configuration>
                    <artifacts>
                      <artifact>
                        <file>
      $jarPath</file>
                        <type>jar</type>
                      </artifact>
                    </artifacts>
                  </configuration>
                </execution>
              </executions>
            </plugin>
          </plugins>
        </build>
      """.trimIndent())

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>m1</module>
                      <module>m2</module>
                    </modules>
                    """.trimIndent())

    maven.assertModuleModuleDeps("m1", "m2")
    maven.assertModuleLibDeps("m1", "Maven: ATTACHED-JAR: test:m2:1")
    maven.assertModuleLibDep("m1", "Maven: ATTACHED-JAR: test:m2:1", "jar://" + FileUtil.toSystemIndependentName(jarPath) + "!/")
    maven.assertModuleLibDeps("m2")
  }

  @Test
  fun testTwoLinkedProjectsFromDifferentBasedirsShouldBeResolvedInDifferentEmbedders() = runBlocking {
    val project1 = maven.createModulePom("project1",
                                   """
                    <groupId>org.example</groupId>
                    <artifactId>project1</artifactId>
                    <version>1.0</version>
                
                    <properties>
                        <maven.compiler.source>17</maven.compiler.source>
                        <maven.compiler.target>17</maven.compiler.target>
                        <pom.myversion>${'$'}{myversion}</pom.myversion>
                    </properties>
                
                    <dependencies>
                        <dependency>
                            <groupId>test</groupId>
                            <artifactId>test</artifactId>
                            <version>${'$'}{pom.myversion}</version>
                        </dependency>
                    </dependencies>
""")

    maven.createProjectSubFile("project1/.mvn/jvm.config", "-Dmyversion=1")

    val project2 = maven.createModulePom("project2",
                                   """
                    <groupId>org.example</groupId>
                    <artifactId>project2</artifactId>
                    <version>1.0</version>
                
                    <properties>
                        <maven.compiler.source>17</maven.compiler.source>
                        <maven.compiler.target>17</maven.compiler.target>
                        <pom.myversion>${'$'}{myversion}</pom.myversion>
                    </properties>
                
                    <dependencies>
                        <dependency>
                            <groupId>test</groupId>
                            <artifactId>test</artifactId>
                            <version>${'$'}{pom.myversion}</version>
                        </dependency>
                    </dependencies>
""")

    maven.createProjectSubFile("project2/.mvn/jvm.config", "-Dmyversion=2")

    maven.importProjectsAsync(project1, project2)
    maven.assertModules("project1", "project2")
    maven.assertModuleLibDeps("project1", "Maven: test:test:1")
    maven.assertModuleLibDeps("project2", "Maven: test:test:2")
  }

  @Test
  fun testInterpolatePomVersion() = runBlocking {
    maven.assumeMaven3()

    maven.createModulePom("m1", """
      <artifactId>m1</artifactId>
      <version>1</version>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>m2</artifactId>
          <version>2</version>
        </dependency>
      </dependencies>
      """.trimIndent())

    maven.createModulePom("m2", """
      <artifactId>m2</artifactId>
      <version>${'$'}{ver}</version>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
      """.trimIndent())

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>m1</module>
                      <module>m2</module>
                    </modules>
                    <properties>
                      <ver>2</ver>
                    </properties>
                      """.trimIndent())

    val module = maven.projectsManager.findProject(maven.getModule(maven.mn("project", "m1")))
    assertNotNull(module)
    maven.assertModuleModuleDeps("m1", "m2")
    assertEmpty(module!!.problems)
  }
}
