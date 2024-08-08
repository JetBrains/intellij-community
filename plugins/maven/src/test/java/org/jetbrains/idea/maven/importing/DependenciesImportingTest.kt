// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.InstantImportCompatible
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper
import org.junit.Test
import java.util.*

class DependenciesImportingTest : MavenMultiVersionImportingTestCase() {

  override fun setUp() {
    super.setUp()
    projectsManager.initForTests()
  }

  @Test
  @InstantImportCompatible
  fun testLibraryDependency() = runBlocking {
    importProjectAsync("""
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

    assertModules("project")
    assertModuleLibDep("project", "Maven: junit:junit:4.0",
                       "jar://$repositoryPath/junit/junit/4.0/junit-4.0.jar!/",
                       "jar://$repositoryPath/junit/junit/4.0/junit-4.0-sources.jar!/",
                       "jar://$repositoryPath/junit/junit/4.0/junit-4.0-javadoc.jar!/")
    assertProjectLibraryCoordinates("Maven: junit:junit:4.0", "junit", "junit", "4.0")
  }

  @Test
  fun testSystemDependency() = runBlocking {
    importProjectAsync("""
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
                  ${repositoryPath}/junit/junit/4.0/junit-4.0.jar</systemPath>
                    </dependency>
                  </dependencies>
                  """.trimIndent())

    assertModules("project")
    assertModuleLibDep("project", "Maven: junit:junit:4.0",
                       listOf("jar://" + repositoryPath + "/junit/junit/4.0/junit-4.0.jar!/"),
                       emptyList(), emptyList())
  }

  @Test
  fun testTestJarDependencies() = runBlocking {
    importProjectAsync("""
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

    assertModules("project")
    assertModuleLibDep("project", "Maven: junit:junit:test-jar:tests:4.0",
                       "jar://" + repositoryPath + "/junit/junit/4.0/junit-4.0-tests.jar!/",
                       "jar://" + repositoryPath + "/junit/junit/4.0/junit-4.0-test-sources.jar!/",
                       "jar://" + repositoryPath + "/junit/junit/4.0/junit-4.0-test-javadoc.jar!/")
    assertProjectLibraryCoordinates("Maven: junit:junit:test-jar:tests:4.0", "junit", "junit", "tests", "jar", "4.0")
  }

  @Test
  fun testDependencyWithClassifier() = runBlocking {
    importProjectAsync("""
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
    assertModules("project")
    assertModuleLibDep("project", "Maven: junit:junit:bar:4.0",
                       "jar://" + repositoryPath + "/junit/junit/4.0/junit-4.0-bar.jar!/",
                       "jar://" + repositoryPath + "/junit/junit/4.0/junit-4.0-sources.jar!/",
                       "jar://" + repositoryPath + "/junit/junit/4.0/junit-4.0-javadoc.jar!/")
    assertProjectLibraryCoordinates("Maven: junit:junit:bar:4.0", "junit", "junit", "bar", "jar", "4.0")
  }


  @Test
  @InstantImportCompatible
  fun testPreservingDependenciesOrder() = runBlocking {
    importProjectAsync("""
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

    assertModules("project")
    assertModuleLibDeps("project", "Maven: B:B:2", "Maven: A:A:1")
  }

  @Test
  @InstantImportCompatible
  fun testPreservingDependenciesOrderWithTestDependencies() = runBlocking {
    importProjectAsync("""
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

    assertModules("project")
    assertModuleLibDeps("project",
                        "Maven: a:compile:1", "Maven: a:test:1", "Maven: a:runtime:1", "Maven: a:compile-2:1")
  }

  @Test
  fun testDoNotResetDependenciesIfProjectIsInvalid() = runBlocking {
    createProjectPom("""
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

    importProjectAsync()
    assertModuleLibDeps("project", "Maven: group:lib:1")

    // incomplete tag
    createProjectPom("""
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

    doImportProjectsAsync(listOf(projectPom), false)
    assertModuleLibDeps("project", "Maven: group:lib:1")
  }

  @Test
  @InstantImportCompatible
  fun testInterModuleDependencies() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    createModulePom("m1", """
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

    createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())

    importProjectAsync()
    assertModules("project", "m1", "m2")

    assertModuleModuleDeps("m1", "m2")
  }

  @Test
  fun testInterModuleDependenciesWithClassifier() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    createModulePom("m1", """
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

    createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())

    importProjectAsync()

    assertModuleModuleDeps("m1", "m2")
    assertModuleLibDep("m1", "Maven: test:m2:client:1",
                       "jar://" + repositoryPath + "/test/m2/1/m2-1-client.jar!/",
                       "jar://" + repositoryPath + "/test/m2/1/m2-1-sources.jar!/",
                       "jar://" + repositoryPath + "/test/m2/1/m2-1-javadoc.jar!/")
  }

  @Test
  fun testDoNotAddInterModuleDependenciesFoUnsupportedDependencyType() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    createModulePom("m1", """
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

    createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())

    importProjectAsync()
    assertModules("project", "m1", "m2")

    assertModuleModuleDeps("m1")
  }

  @Test
  @InstantImportCompatible
  fun testInterModuleDependenciesWithoutModuleVersions() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    createModulePom("m1", """
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

    createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
      """.trimIndent())

    importProjectAsync()
    assertModules("project", "m1", mn("project", "m2"))

    assertModuleModuleDeps("m1", mn("project", "m2"))
  }

  @Test
  fun testInterModuleDependenciesWithVersionRanges() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    createModulePom("m1", """
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

    createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())

    importProjectAsync()
    assertModules("project", "m1", "m2")

    assertModuleModuleDeps("m1", "m2")
  }

  @Test
  @InstantImportCompatible
  fun testInterModuleDependenciesWithoutModuleGroup() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    createModulePom("m1", """
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

    createModulePom("m2", """
      <artifactId>m2</artifactId>
      <version>1</version>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
      """.trimIndent())

    importProjectAsync()
    assertModules("project", "m1", mn("project", "m2"))

    assertModuleModuleDeps("m1", mn("project", "m2"))
  }


  @Test
  fun testInterModuleDependenciesIfThereArePropertiesInArtifactHeader() = runBlocking {
    createProjectPom("""
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

    createModulePom("m1", """
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

    createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>${'$'}{module2Name}</artifactId>
      <version>${'$'}{project.parent.version}</version>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
      """.trimIndent())

    importProjectAsync()
    assertModules("project", "m1", mn("project", "m2"))

    assertModuleModuleDeps("m1", mn("project", "m2"))
  }

  @Test
  fun testInterModuleDependenciesIfThereArePropertiesInArtifactHeaderDefinedInParent() = runBlocking {
    createProjectPom("""
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

    createModulePom("m1",
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

    createModulePom("m2",
                    """
                      <parent>
                        <groupId>${'$'}{groupProp}</groupId>
                        <artifactId>parent</artifactId>
                        <version>${'$'}{versionProp}</version>
                      </parent>
                      <artifactId>m2</artifactId>
                      """.trimIndent())

    importProjectAsync()
    assertModules("parent", "m1", "m2")

    assertModuleModuleDeps("m1", "m2")
  }

  @Test
  fun testDependenciesInPerSourceTypeModule() = runBlocking {
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
                      """.trimIndent())

    createModulePom("m2",
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
                      <module>m2</module>
                    </modules>
                    """.trimIndent())

    assertModules("project",
                  mn("project", "m1"),
                  mn("project", "m2"),
                  mn("project", "m1.main"),
                  mn("project", "m1.test"),
                  mn("project", "m2.main"),
                  mn("project", "m2.test"))
    assertModuleModuleDeps(mn("project", "m1.test"), mn("project", "m1.main"))
    assertModuleModuleDeps(mn("project", "m2.test"), mn("project", "m2.main"), mn("project", "m1.main"))
    assertModuleModuleDeps(mn("project", "m2.main"), mn("project", "m1.main"))
  }

  @Test
  fun testTestDependencyOnPerSourceTypeModule() = runBlocking {
    createModulePom("m1",
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

    createModulePom("m2",
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

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>m1</module>
                      <module>m2</module>
                    </modules>
                    """.trimIndent())

    assertModules("project",
                  mn("project", "m1"),
                  mn("project", "m2"),
                  mn("project", "m1.main"),
                  mn("project", "m1.test"))
    assertModuleModuleDeps(mn("project", "m2"), mn("project", "m1.test"))
    assertModuleModuleDeps(mn("project", "m1.test"), mn("project", "m1.main"))
  }

  @Test
  @InstantImportCompatible
  fun testDependencyOnSelf() = runBlocking {
    importProjectAsync("""
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

    assertModuleModuleDeps("project")
  }

  @Test
  fun testDependencyOnSelfWithPomPackaging() = runBlocking {
    importProjectAsync("""
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

    assertModuleModuleDeps("project")
  }

  @Test
  fun testIntermoduleDependencyOnTheSameModuleWithDifferentTypes() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    createModulePom("m1", """
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

    createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())

    importProjectAsync()
    assertModules("project", "m1", "m2")

    assertModuleModuleDeps("m1", "m2", "m2")
  }

  @Test
  fun testDependencyScopes() = runBlocking {
    importProjectAsync("""
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

    assertModuleLibDepScope("project", "Maven: test:foo1:1", DependencyScope.COMPILE)
    assertModuleLibDepScope("project", "Maven: test:foo2:1", DependencyScope.RUNTIME)
    assertModuleLibDepScope("project", "Maven: test:foo3:1", DependencyScope.TEST)
  }

  @Test
  fun testModuleDependencyScopes() = runBlocking {
    createProjectPom("""
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

    createModulePom("m1", """
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

    createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())
    createModulePom("m3", """
      <groupId>test</groupId>
      <artifactId>m3</artifactId>
      <version>1</version>
      """.trimIndent())
    createModulePom("m4", """
      <groupId>test</groupId>
      <artifactId>m4</artifactId>
      <version>1</version>
      """.trimIndent())

    importProjectAsync()
    assertModules("project", "m1", "m2", "m3", "m4")

    assertModuleModuleDepScope("m1", "m2", DependencyScope.COMPILE)
    assertModuleModuleDepScope("m1", "m3", DependencyScope.RUNTIME)
    assertModuleModuleDepScope("m1", "m4", DependencyScope.TEST)
  }

  @Test
  @InstantImportCompatible
  fun testDependenciesAreNotExported() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    createModulePom("m1", """
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

    createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())

    importProjectAsync()
    assertExportedDeps("m1")
  }

  @Test
  fun testTransitiveDependencies() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    createModulePom("m1", """
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

    createModulePom("m2", """
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

    importProjectAsync()
    assertModules("project", "m1", "m2")

    assertModuleLibDeps("m2", "Maven: group:id:1")
    assertModuleLibDeps("m1", "Maven: group:id:1")
  }

  @Test
  fun testTransitiveLibraryDependencyVersionResolution() = runBlocking {
    // this test hanles the case when the particular dependency list cause embedder set
    // the versionRange for the xml-apis:xml-apis:1.0.b2 artifact to null.
    // see http://jira.codehaus.org/browse/MNG-3386

    importProjectAsync("""
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

    assertModules("project")
    assertModuleLibDep("project", "Maven: xml-apis:xml-apis:1.0.b2")
  }

  @Test
  fun testIncrementalSyncTransitiveLibraryDependencyManagement() = runBlocking {
    importProjectAsync("""
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

    assertModules("project")
    assertModuleLibDeps("project", "Maven: asm:asm-attrs:2.2.1", "Maven: asm:asm:3.3.0")

    updateProjectPom("""
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
    updateAllProjects()

    assertModuleLibDeps("project", "Maven: asm:asm-attrs:2.2.1", "Maven: asm:asm:3.3.1")
  }

  @Test
  fun testExclusionOfTransitiveDependencies() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    createModulePom("m1", """
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

    createModulePom("m2", """
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
    importProjectAsync()

    assertModuleLibDeps("m2", "Maven: group:id:1")

    assertModuleModuleDeps("m1", "m2")
    assertModuleLibDeps("m1")
  }

  @Test
  fun testDependencyWithEnvironmentProperty() = runBlocking {
    needFixForMaven4()
    val javaHome = FileUtil.toSystemIndependentName(System.getProperty("java.home"))

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>direct-system-dependency</groupId>
                           <artifactId>direct-system-dependency</artifactId>
                           <version>1.0</version>
                           <scope>system</scope>
                           <systemPath>${'$'}{java.home}/lib/tools.jar</systemPath>
                         </dependency>
                       </dependencies>
                       """.trimIndent())
    doImportProjectsAsync(listOf(projectPom), false)

    assertModules("project")
    assertModuleLibDep("project",
                       "Maven: direct-system-dependency:direct-system-dependency:1.0",
                       "jar://$javaHome/lib/tools.jar!/")
  }

  @Test
  fun testDependencyWithEnvironmentENVProperty() = runBlocking {
    needFixForMaven4()
    var envDir = FileUtil.toSystemIndependentName(System.getenv(envVar))
    envDir = StringUtil.trimEnd(envDir, "/")

    createProjectPom("""<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
<dependencies>
  <dependency>
    <groupId>direct-system-dependency</groupId>
    <artifactId>direct-system-dependency</artifactId>
    <version>1.0</version>
    <scope>system</scope>
    <systemPath>${"$"}{env.${envVar}}/lib/tools.jar</systemPath>
  </dependency>
</dependencies>
""")
    doImportProjectsAsync(listOf(projectPom), false)

    assertModules("project")
    assertModuleLibDep("project",
                       "Maven: direct-system-dependency:direct-system-dependency:1.0",
                       "jar://$envDir/lib/tools.jar!/")
  }

  @Test
  fun testDependencyWithVersionRangeOnModule() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    createModulePom("m1", """
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

    createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>2</version>
      """.trimIndent())

    importProjectAsync()

    assertModules("project", "m1", "m2")

    assertModuleModuleDeps("m1", "m2")
    assertModuleLibDeps("m1")
  }

  @Test
  fun testPropertiesInInheritedDependencies() = runBlocking {
    createProjectPom("""
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

    createModulePom("m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>2</version>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
      """.trimIndent())

    importProjectAsync()

    assertModuleLibDep(mn("project", "m"), "Maven: group:lib:2")
  }

  @Test
  @InstantImportCompatible
  fun testPropertyInTheModuleDependency() = runBlocking {
    createProjectPom("""
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

    createModulePom("m", """
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

    importProjectAsync()

    assertModules("project", mn("project", "m"))
    assertModuleLibDeps(mn("project", "m"), "Maven: group:id:1.2.3")
  }

  @Test
  fun testManagedModuleDependency() = runBlocking {
    createProjectPom("""
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

    createModulePom("m", """
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

    importProjectAsync()
    assertModuleLibDeps(mn("project", "m"), "Maven: group:id:1")
  }

  @Test
  fun testPropertyInTheManagedModuleDependencyVersion() = runBlocking {
    createProjectPom("""
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

    createModulePom("m", """
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

    importProjectAsync()

    assertModules("project", mn("project", "m"))
    assertModuleLibDeps(mn("project", "m"), "Maven: group:id:1")
  }

  @Test
  fun testPomTypeDependency() = runBlocking {
    createProjectPom("""
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

    importProjectAsync() // shouldn't throw any exception
  }

  @Test
  fun testPropertyInTheManagedModuleDependencyVersionOfPomType() = runBlocking {
    createProjectPom("""
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
       
                       <modules>
                         <module>m</module>
                       </modules>
                       """.trimIndent())

    createModulePom("m", """
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

    doImportProjectsAsync(listOf(projectPom), false)

    assertModules("project", mn("project", "m"))
    assertModuleLibDeps(mn("project", "m"))


    val root = projectsTree.rootProjects[0]
    val modules = projectsTree.getModules(root)

    assertOrderedElementsAreEqual(root.getProblems())
    assertTrue(modules[0].getProblems()[0].description!!.contains("Unresolved dependency: 'xxx:yyy:pom:1'"))
  }

  @Test
  fun testResolvingFromRepositoriesIfSeveral() = runBlocking {
    val fixture = MavenCustomRepositoryHelper(dir, "local1")
    repositoryPath = fixture.getTestDataPath("local1")
    removeFromLocalRepository("junit")

    val file = fixture.getTestData("local1/junit/junit/4.0/junit-4.0.pom")
    assertFalse(file.exists())

    importProjectAsync("""
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
    needFixForMaven4()
    repositoryPath = dir.path + "/repo"
    val mirrorPath = pathTransformer.toRemotePath(FileUtil.toSystemIndependentName(dir.path + "/mirror"))

    updateSettingsXmlFully("""<settings>
  <mirrors>
    <mirror>
      <id>foo</id>
      <url>file://$mirrorPath</url>
      <mirrorOf>*</mirrorOf>
    </mirror>
  </mirrors>
</settings>
""")

    importProjectAsync("""
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

    assertTrue(projectsTree.findProject(projectPom)!!.hasUnresolvedArtifacts())
  }

  @Test
  fun testCanResolveDependenciesWhenExtensionPluginNotFound() = runBlocking {
    createProjectPom("""
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
    doImportProjectsAsync(listOf(projectPom), false)

    assertModuleLibDep("project", "Maven: junit:junit:4.0")
  }

  @Test
  fun testDoNotRemoveLibrariesOnImportIfProjectWasNotChanged() = runBlocking {
    importProjectAsync("""
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

    assertProjectLibraries("Maven: junit:junit:4.0")
    assertModuleLibDeps("project", "Maven: junit:junit:4.0")

    updateAllProjects()

    assertProjectLibraries("Maven: junit:junit:4.0")
    assertModuleLibDeps("project", "Maven: junit:junit:4.0")
  }

  @Test
  @InstantImportCompatible
  fun testDoNotCreateSameLibraryTwice() = runBlocking {
    importProjectAsync("""
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

    importProjectAsync()

    assertProjectLibraries("Maven: junit:junit:4.0")
    assertModuleLibDeps("project", "Maven: junit:junit:4.0")
  }

  @Test
  fun testCreateSeparateLibraryForDifferentArtifactTypeAndClassifier() = runBlocking {
    importProjectAsync("""
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

    assertProjectLibraries("Maven: junit:junit:4.0",
                           "Maven: junit:junit:test-jar:tests:4.0",
                           "Maven: junit:junit:jdk5:4.0")
    assertModuleLibDeps("project",
                        "Maven: junit:junit:4.0",
                        "Maven: junit:junit:test-jar:tests:4.0",
                        "Maven: junit:junit:jdk5:4.0")
  }

  @Test
  fun testRemoveUnnecessaryMavenizedModuleDepsOnRepomport() = runBlocking {
    val m1 = createModulePom("m1",
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
    val m2 = createModulePom("m2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m2</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    importProjects(m1, m2)
    assertModuleModuleDeps("m1", "m2")

    updateModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      """.trimIndent())

    updateAllProjects()
    assertModuleModuleDeps("m1")
  }

  @Test
  fun testDifferentSystemDependenciesWithSameId() = runBlocking {
    needFixForMaven4()
    createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>xxx</groupId>
          <artifactId>yyy</artifactId>
          <version>1</version>
          <scope>system</scope>
          <systemPath>
      ${root}/m1/foo.jar</systemPath>
        </dependency>
      </dependencies>
      """)
    createModulePom("m2", """<groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>xxx</groupId>
          <artifactId>yyy</artifactId>
          <version>1</version>
          <scope>system</scope>
          <systemPath>
      ${root}/m2/foo.jar</systemPath>
        </dependency>
      </dependencies>
      """.trimIndent())

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
    doImportProjectsAsync(listOf(projectPom), false)

    //    assertProjectLibraries("Maven: xxx:yyy:1");
    assertModuleLibDep("m1", "Maven: xxx:yyy:1", "jar://" + root + "/m1/foo.jar!/")
    assertModuleLibDep("m2", "Maven: xxx:yyy:1", "jar://" + root + "/m2/foo.jar!/")
  }

  @Test
  @InstantImportCompatible
  fun testDoNotPopulateSameRootEntriesOnEveryImport() = runBlocking {
    importProjectAsync("""
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

    assertModuleLibDep("project", "Maven: junit:junit:4.0",
                       Arrays.asList("jar://" + repositoryPath + "/junit/junit/4.0/junit-4.0.jar!/"),
                       Arrays.asList("jar://" + repositoryPath + "/junit/junit/4.0/junit-4.0-sources.jar!/"),
                       Arrays.asList("jar://" + repositoryPath + "/junit/junit/4.0/junit-4.0-javadoc.jar!/"))

    // update twice
    updateAllProjects()
    updateAllProjects()

    assertModuleLibDep("project", "Maven: junit:junit:4.0",
                       Arrays.asList("jar://" + repositoryPath + "/junit/junit/4.0/junit-4.0.jar!/"),
                       Arrays.asList("jar://" + repositoryPath + "/junit/junit/4.0/junit-4.0-sources.jar!/"),
                       Arrays.asList("jar://" + repositoryPath + "/junit/junit/4.0/junit-4.0-javadoc.jar!/"))
  }

  @Test
  fun testDoNotPopulateSameRootEntriesOnEveryImportForSystemLibraries() = runBlocking {
    needFixForMaven4()
    val root = root
    val path = "jar://$root/foo/bar.jar!/"
    runBlocking {
      createProjectPom("""
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
        <dependencies>
          <dependency>
            <groupId>xxx</groupId>
            <artifactId>yyy</artifactId>
            <version>1</version>
            <scope>system</scope>
            <systemPath>$root/foo/bar.jar</systemPath>
          </dependency>
        </dependencies>
        """.trimIndent())
      doImportProjectsAsync(listOf(projectPom), false)

      assertModuleLibDep("project", "Maven: xxx:yyy:1", listOf(path), emptyList(), emptyList())

      // update twice
      updateAllProjects()
      updateAllProjects()

      assertModuleLibDep("project", "Maven: xxx:yyy:1", listOf(path), emptyList(), emptyList())
    }
  }

  @Test
  fun testRemovingPreviousSystemPathForForSystemLibraries() = runBlocking {
    createProjectSubFile("foo/bar.jar")
    createProjectSubFile("foo/xxx.jar")

    createProjectPom("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>xxx</groupId>
          <artifactId>yyy</artifactId>
          <version>1</version>
          <scope>system</scope>
          <systemPath>
      $projectPath/foo/bar.jar</systemPath>
        </dependency>
      </dependencies>
      """.trimIndent())
    doImportProjectsAsync(listOf(projectPom), false)

    assertModuleLibDep("project", "Maven: xxx:yyy:1",
                       listOf("jar://$projectPath/foo/bar.jar!/"),
                       emptyList(),
                       emptyList())

    updateProjectPom("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>xxx</groupId>
          <artifactId>yyy</artifactId>
          <version>1</version>
          <scope>system</scope>
          <systemPath>
      $projectPath/foo/xxx.jar</systemPath>
        </dependency>
      </dependencies>
      """.trimIndent())

    updateAllProjects()

    assertModuleLibDep("project", "Maven: xxx:yyy:1",
                       listOf("jar://$projectPath/foo/xxx.jar!/"),
                       emptyList(),
                       emptyList())
  }

  @Test
  fun testRemovingUnusedLibraries() = runBlocking {
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

    createModulePom("m1", """
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

    createModulePom("m2", """
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

    importProjectAsync()
    assertProjectLibraries("Maven: group:lib1:1",
                           "Maven: group:lib2:1",
                           "Maven: group:lib3:1",
                           "Maven: group:lib4:1")

    updateModulePom("m1", """
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

    updateModulePom("m2", """
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

    updateAllProjects()
    assertProjectLibraries("Maven: group:lib2:1",
                           "Maven: group:lib3:1")
  }

  @Test
  @InstantImportCompatible
  fun testDoNoRemoveUnusedLibraryIfItWasChanged() = runBlocking {
    importProjectAsync("""
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

    assertProjectLibraries("Maven: group:lib1:1",
                           "Maven: group:lib2:1",
                           "Maven: group:lib3:1")

    addLibraryRoot("Maven: group:lib1:1", JavadocOrderRootType.getInstance(), "file://foo.bar")
    clearLibraryRoots("Maven: group:lib2:1", JavadocOrderRootType.getInstance())
    addLibraryRoot("Maven: group:lib2:1", JavadocOrderRootType.getInstance(), "file://foo.baz")

    updateProjectPom("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    updateAllProjects()

    assertProjectLibraries()
  }

  @Test
  fun testDoNoRemoveUserProjectLibraries() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    createAndAddProjectLibrary("project", "lib")

    assertProjectLibraries("lib")
    addLibraryRoot("lib", OrderRootType.CLASSES, "file://" + repositoryPath + "/foo/bar.jar!/")

    updateProjectPom("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    updateAllProjects()

    assertProjectLibraries("lib")

    assertModuleLibDeps("project")
  }

  @Test
  @InstantImportCompatible
  fun testDoNoRemoveUnusedUserProjectLibraries() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    createProjectLibrary("lib")
    addLibraryRoot("lib", OrderRootType.CLASSES, "file://" + repositoryPath + "/foo/bar.jar!/")

    assertProjectLibraries("lib")

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    assertProjectLibraries("lib")
  }

  @Test
  fun testRemovingUnusedLibrariesIfProjectRemoved() = runBlocking {
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

    createModulePom("m1", """
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

    createModulePom("m2", """
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

    importProjectAsync()
    assertProjectLibraries("Maven: group:lib1:1",
                           "Maven: group:lib2:1")

    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>m1</module>
                       </modules>
                       """.trimIndent())

    updateAllProjects()
    assertProjectLibraries("Maven: group:lib1:1")
  }

  @Test
  fun testRemovingUnusedLibraryWithClassifier() = runBlocking {
    importProjectAsync("""
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

    assertProjectLibraries("Maven: group:lib1:tests:1",
                           "Maven: group:lib2:test-jar:tests:1")

    updateProjectPom("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
    updateAllProjects()

    assertProjectLibraries()
  }

  private fun createProjectLibrary(libraryName: String): Library {
    val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
    return WriteAction.computeAndWait<Library, RuntimeException> { libraryTable.createLibrary(libraryName) }
  }

  private fun createAndAddProjectLibrary(moduleName: String, libraryName: String) {
    WriteAction.runAndWait<RuntimeException> {
      val lib = createProjectLibrary(libraryName)
      ModuleRootModificationUtil.addDependency(getModule(moduleName), lib)
    }
  }

  private fun clearLibraryRoots(libraryName: String, vararg types: OrderRootType) {
    val lib = LibraryTablesRegistrar.getInstance().getLibraryTable(project).getLibraryByName(libraryName)
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
    val lib = LibraryTablesRegistrar.getInstance().getLibraryTable(project).getLibraryByName(libraryName)
    WriteAction.runAndWait<RuntimeException> {
      val model = lib!!.getModifiableModel()
      model.addRoot(path, type)
      model.commit()
    }
  }

  @Test
  fun testEjbDependenciesInJarProject() = runBlocking {
    importProjectAsync("""
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

    assertModules("project")
    assertModuleLibDeps("project", "Maven: foo:foo:ejb:1", "Maven: foo:bar:ejb-client:client:1")
    assertProjectLibraryCoordinates("Maven: foo:foo:ejb:1", "foo", "foo", null, "ejb", "1")
    assertProjectLibraryCoordinates("Maven: foo:bar:ejb-client:client:1", "foo", "bar", "client", "ejb", "1")
  }

  @Test
  fun testDoNotFailOnAbsentAppLibrary() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    ApplicationManager.getApplication().invokeAndWait {
      ApplicationManager.getApplication().runWriteAction {
        val appTable = LibraryTablesRegistrar.getInstance().getLibraryTable()
        val lib = appTable.createLibrary("foo")
        ModuleRootModificationUtil.addDependency(getModule("project"), lib)
        appTable.removeLibrary(lib)
      }
    }

    importProjectAsync() // should not fail;
  }

  @Test
  fun testDoNotFailToConfigureUnresolvedVersionRangeDependencies() = runBlocking {
    // should not throw NPE when accessing CustomArtifact.getPath();
    val helper = MavenCustomRepositoryHelper(dir, "local1")
    val repoPath = helper.getTestDataPath("local1")
    repositoryPath = repoPath

    importProjectAsync("""
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

    assertModuleLibDeps("project", "Maven: junit:junit:3.8.2")
    assertModuleLibDep("project", "Maven: junit:junit:3.8.2",
                       "jar://$repoPath/junit/junit/3.8.2/junit-3.8.2.jar!/")
  }

  @Test
  fun testVersionRangeInDependencyManagementDoesntBreakIndirectDependency() = runBlocking {
    val helper = MavenCustomRepositoryHelper(dir, "local1")
    val repoPath = helper.getTestDataPath("local1")
    repositoryPath = repoPath

    createProjectPom("""
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

    createModulePom("m", """
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

    importProjectAsync()

    assertModuleLibDeps(mn("project", "m"), "Maven: asm:asm-attrs:2.2.1", "Maven: asm:asm:2.2.1")
  }

  @Test
  fun testDependencyToIgnoredProject() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    createModulePom("m1", """
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

    val m2 = createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>2</version>
      """.trimIndent())

    importProjectAsync()

    assertModules("project", "m1", "m2")
    assertModuleModuleDeps("m1", "m2")

    setIgnoredFilesPathForNextImport(listOf(m2.getPath()))

    updateAllProjects()

    assertModules("project", "m1")
    assertModuleLibDeps("m1", "Maven: test:m2:2")

    assertModuleLibDep("m1", "Maven: test:m2:2",
                       "jar://" + repositoryPath + "/test/m2/2/m2-2.jar!/",
                       "jar://" + repositoryPath + "/test/m2/2/m2-2-sources.jar!/",
                       "jar://" + repositoryPath + "/test/m2/2/m2-2-javadoc.jar!/")
  }

  @Test
  fun testSaveJdkPosition() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    createModulePom("m1", """
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

    createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())

    importProjectAsync()

    WriteAction.runAndWait<RuntimeException> {
      val rootModel = ModuleRootManager.getInstance(getModule("m1")).getModifiableModel()
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
    val orderEntries = ModuleRootManager.getInstance(getModule("m1")).getOrderEntries()
    assert(orderEntries.size == 4)
    assert((orderEntries[0] as ModuleOrderEntry).getModuleName() == "m2")
    assert("Maven: junit:junit:4.0" == (orderEntries[1] as LibraryOrderEntry).getLibraryName())
    assert(orderEntries[2] is JdkOrderEntry)
    assert(orderEntries[3] is ModuleSourceOrderEntry)
  }

  @Test
  fun testSaveJdkPositionSystemDependency() = runBlocking {
    createProjectPom("""
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
    doImportProjectsAsync(listOf(projectPom), false)

    WriteAction.runAndWait<RuntimeException> {
      val rootModel = ModuleRootManager.getInstance(getModule("m1")).getModifiableModel()
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
    val orderEntries = ModuleRootManager.getInstance(getModule("m1")).getOrderEntries()
    assert(orderEntries.size == 4)
    assert("Maven: test:systemDep:1" == (orderEntries[0] as LibraryOrderEntry).getLibraryName())
    assert("Maven: junit:junit:4.0" == (orderEntries[1] as LibraryOrderEntry).getLibraryName())
    assert(orderEntries[2] is JdkOrderEntry)
    assert(orderEntries[3] is ModuleSourceOrderEntry)
  }

  @Test
  fun testBundleDependencyType() = runBlocking {
    importProjectAsync("""
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

    assertProjectLibraries("Maven: com.google.guava:guava:15.0")
    assertModuleLibDep("project", "Maven: com.google.guava:guava:15.0",
                       "jar://" + repositoryPath + "/com/google/guava/guava/15.0/guava-15.0.jar!/",
                       "jar://" + repositoryPath + "/com/google/guava/guava/15.0/guava-15.0-sources.jar!/",
                       "jar://" + repositoryPath + "/com/google/guava/guava/15.0/guava-15.0-javadoc.jar!/")
  }

  @Test
  fun testReimportingInheritedLibrary() = runBlocking {
    createProjectPom("""
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

    createModulePom("m1", """
      <parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          
          <version>1</version>
        </parent>
      <artifactId>m1</artifactId>
      """.trimIndent())


    importProjectAsync()

    updateProjectPom("""
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

    updateAllProjects()

    assertModuleLibDep("project", "Maven: junit:junit:4.0",
                       "jar://" + repositoryPath + "/junit/junit/4.0/junit-4.0.jar!/",
                       "jar://" + repositoryPath + "/junit/junit/4.0/junit-4.0-sources.jar!/",
                       "jar://" + repositoryPath + "/junit/junit/4.0/junit-4.0-javadoc.jar!/")

    assertModuleLibDep(mn("project", "m1"), "Maven: junit:junit:4.0",
                       "jar://" + repositoryPath + "/junit/junit/4.0/junit-4.0.jar!/",
                       "jar://" + repositoryPath + "/junit/junit/4.0/junit-4.0-sources.jar!/",
                       "jar://" + repositoryPath + "/junit/junit/4.0/junit-4.0-javadoc.jar!/")
  }

  @Test
  fun testRemoveInvalidOrderEntry() = runBlocking {
    val value = Registry.get("maven.always.remove.bad.entries")
    try {
      value.setValue(true)
      createProjectPom("""
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
      importProjectAsync()

      writeAction {
        val modifiableModel = ModuleRootManager.getInstance(getModule("project")).getModifiableModel()
        modifiableModel.addInvalidLibrary("SomeLibrary", LibraryTablesRegistrar.PROJECT_LEVEL)
        modifiableModel.addInvalidLibrary("Maven: AnotherLibrary", LibraryTablesRegistrar.PROJECT_LEVEL)
        modifiableModel.commit()
      }

      // incremental sync doesn't update module if effective pom dependencies haven't changed
      updateAllProjectsFullSync()

      assertModuleLibDeps("project", "Maven: junit:junit:4.0")
    }
    finally {
      value.resetToDefault()
    }
  }

  @Test
  fun testTransitiveProfileDependency() = runBlocking {
    needFixForMaven4()
    assumeVersionMoreThan("3.1.0")
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    createModulePom("m1", """
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

    createModulePom("m2", """
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

    importProjectWithProfiles("test")
    assertModuleLibDeps("m2", "Maven: junit:junit:4.0")
  }

  @Test
  fun testAttachedJarDependency() = runBlocking {
    // IDEA-86815 Recognize attached jar as library dependency

    createModulePom("m1", """
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

    val file = createProjectSubFile("m1/src/main/java/Foo.java",
                                    """
                                      class Foo {
                                        void foo() {
                                          junit.framework.TestCase a = null;
                                          junit.framework.<error>TestCase123</error> b = null;
                                        }
                                      }
                                      """.trimIndent())

    val jarPath = PlatformTestUtil.getCommunityPath() + "/plugins/maven/src/test/data/local1/junit/junit/3.8.1/junit-3.8.1.jar"

    createModulePom("m2", """
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

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>m1</module>
                      <module>m2</module>
                    </modules>
                    """.trimIndent())

    assertModuleModuleDeps("m1", "m2")
    assertModuleLibDeps("m1", "Maven: ATTACHED-JAR: test:m2:1")
    assertModuleLibDep("m1", "Maven: ATTACHED-JAR: test:m2:1", "jar://" + FileUtil.toSystemIndependentName(jarPath) + "!/")
    assertModuleLibDeps("m2")
  }
}
