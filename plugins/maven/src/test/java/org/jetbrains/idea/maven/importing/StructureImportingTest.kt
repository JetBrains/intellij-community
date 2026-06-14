// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertContentRoots
import com.intellij.maven.testFramework.fixtures.assertExcludes
import com.intellij.maven.testFramework.fixtures.assertMavenizedModule
import com.intellij.maven.testFramework.fixtures.assertModuleLibDeps
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.assertNotMavenizedModule
import com.intellij.maven.testFramework.fixtures.assertSources
import com.intellij.maven.testFramework.fixtures.assumeMaven3
import com.intellij.maven.testFramework.fixtures.assumeMaven4
import com.intellij.maven.testFramework.fixtures.assumeModel_4_0_0
import com.intellij.maven.testFramework.fixtures.assumeVersionMoreThan
import com.intellij.maven.testFramework.fixtures.createModule
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubDirs
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.getModule
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.importProjectWithProfiles
import com.intellij.maven.testFramework.fixtures.importProjectsWithProfiles
import com.intellij.maven.testFramework.fixtures.isMaven4
import com.intellij.maven.testFramework.fixtures.mavenGeneralSettings
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.mn
import com.intellij.maven.testFramework.fixtures.projectPath
import com.intellij.maven.testFramework.fixtures.projectRoot
import com.intellij.maven.testFramework.fixtures.projectsTree
import com.intellij.maven.testFramework.fixtures.refreshFiles
import com.intellij.maven.testFramework.fixtures.updateAllProjects
import com.intellij.maven.testFramework.fixtures.updateProjectPom
import com.intellij.maven.testFramework.fixtures.updateSettingsXml
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.JpsProjectFileEntitySource.FileInDirectory
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.PsiTestUtil.addContentRoot
import com.intellij.testFramework.UsefulTestCase.assertSameElements
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.fixtures.executeGoal
import org.jetbrains.idea.maven.fixtures.hasMavenInstallation
import org.jetbrains.idea.maven.fixtures.runWithoutStaticSync
import org.jetbrains.idea.maven.fixtures.setupJdkForModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.io.IOException
import java.nio.file.Files

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class StructureImportingTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @Test
  fun testInheritProjectJdkForModules() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    assertTrue(ModuleRootManager.getInstance(maven.getModule("project")).isSdkInherited())
  }

  @Test
  fun testDoNotResetSomeSettingsAfterReimport() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    val sdk = maven.setupJdkForModule("project")

    maven.updateAllProjects()

    assertFalse(ModuleRootManager.getInstance(maven.getModule("project")).isSdkInherited())
    assertEquals(sdk, ModuleRootManager.getInstance(maven.getModule("project")).getSdk())
  }

  @Test
  fun testImportWithAlreadyExistingModules() = runBlocking {
    maven.createModule("m1")
    maven.createModule("m2")
    maven.createModule("m3")
    maven.createModule("m4")

    PsiTestUtil.addSourceRoot(maven.getModule("m1"), maven.createProjectSubFile("m1/user-sources"))
    PsiTestUtil.addSourceRoot(maven.getModule("m2"), maven.createProjectSubFile("m2/user-sources"))
    PsiTestUtil.addSourceRoot(maven.getModule("m3"), maven.createProjectSubFile("m3/user-sources"))
    PsiTestUtil.addSourceRoot(maven.getModule("m4"), maven.createProjectSubFile("m4/user-sources"))
    PsiTestUtil.addSourceRoot(maven.getModule("m4"), maven.createProjectSubFile("m4/src/main/java"))

    maven.assertModules("m1", "m2", "m3", "m4")
    maven.assertSources("m1", "user-sources")
    maven.assertSources("m2", "user-sources")
    maven.assertSources("m3", "user-sources")
    maven.assertSources("m4", "user-sources", "src/main/java")

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
      """.trimIndent())
    maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())

    maven.createModulePom("m4", """
      <groupId>test</groupId>
      <artifactId>m4</artifactId>
      <version>1</version>
      """.trimIndent())

    maven.createProjectSubDirs("m1/src/main/java",
                         "m2/src/main/java",
                         "m3/src/main/java")

    maven.importProjectAsync()
    maven.assertModules("project", "m1", "m2", "m3", "m4")

    maven.assertSources("m1", "user-sources", "src/main/java")
    maven.assertSources("m2", "user-sources", "src/main/java")
    maven.assertSources("m3", "user-sources")
    maven.assertSources("m4", "user-sources", "src/main/java")

    val mFour = maven.project.workspaceModel.currentSnapshot.resolve(ModuleId("m4"))
    assertNotNull(mFour)
    val sourceEntitySource = mFour!!.contentRoots.first().sourceRoots.first { it.url.url.endsWith("java") }.entitySource
    assertTrue(sourceEntitySource is FileInDirectory)
  }

  @Test
  fun testImportWithAlreadyExistingModulesWithCustomExcludes() = runBlocking {
    maven.createModule("m1")
    maven.createModule("m2")
    maven.createModule("m3")

    PsiTestUtil.addExcludedRoot(maven.getModule("m1"), maven.createProjectSubFile("m1/user-sources"))
    PsiTestUtil.addExcludedRoot(maven.getModule("m2"), maven.createProjectSubFile("m2/user-sources"))
    PsiTestUtil.addExcludedRoot(maven.getModule("m3"), maven.createProjectSubFile("m3/user-sources"))

    maven.assertModules("m1", "m2", "m3")
    maven.assertExcludes("m1", "user-sources")
    maven.assertExcludes("m2", "user-sources")
    maven.assertExcludes("m3", "user-sources")

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
      """.trimIndent())
    maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())

    maven.createProjectSubDirs("m1/src/main/java",
                         "m2/src/main/java",
                         "m3/src/main/java")

    maven.importProjectAsync()
    maven.assertModules("project", "m1", "m2", "m3")

    maven.assertExcludes("m1", "target", "user-sources")
    maven.assertExcludes("m2", "target", "user-sources")
    maven.assertExcludes("m3", "user-sources")
  }

  /**
   * Keep the module if it has some custom content roots that doesn't intersect with imported
   */
  @Test
  fun testImportWithAlreadyExistingModuleWithDifferentNameButSameContentRoot() = runBlocking {
    val userModuleWithConflictingRoot = maven.createModule("userModuleWithConflictingRoot")
    PsiTestUtil.removeAllRoots(userModuleWithConflictingRoot, null)
    addContentRoot(userModuleWithConflictingRoot, maven.projectRoot)
    val anotherContentRoot = maven.createProjectSubFile("m1/user-content")
    addContentRoot(userModuleWithConflictingRoot, anotherContentRoot)
    maven.assertContentRoots(userModuleWithConflictingRoot.getName(), maven.projectPath.toString(), anotherContentRoot.getPath())

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    maven.importProjectAsync()
    maven.assertModules("project", userModuleWithConflictingRoot.getName())
    maven.assertContentRoots("project", maven.projectPath.toString())
    maven.assertContentRoots(userModuleWithConflictingRoot.getName(), anotherContentRoot.getPath())
  }

  @Test
  fun testImportWithAlreadyExistingModuleWithPartiallySameContentRoots() = runBlocking {
    val userModuleWithConflictingRoot = maven.createModule("userModuleWithConflictingRoot")
    PsiTestUtil.removeAllRoots(userModuleWithConflictingRoot, null)
    addContentRoot(userModuleWithConflictingRoot, maven.projectRoot)
    maven.assertContentRoots(userModuleWithConflictingRoot.getName(), maven.projectPath.toString())

    val userModuleWithUniqueRoot = maven.createModule("userModuleWithUniqueRoot")
    maven.assertContentRoots(userModuleWithUniqueRoot.getName(), "${maven.projectPath}/userModuleWithUniqueRoot")

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    maven.importProjectAsync()
    maven.assertModules("project", userModuleWithUniqueRoot.getName())
    maven.assertContentRoots("project", maven.projectPath.toString())
    maven.assertContentRoots(userModuleWithUniqueRoot.getName(), "${maven.projectPath}/userModuleWithUniqueRoot")
  }

  @Test
  fun testMarkModulesAsMavenized() = runBlocking {
    maven.createModule("userModule")

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      """.trimIndent())

    maven.importProjectAsync()
    maven.assertModules("project", "m1", "userModule")
    maven.assertMavenizedModule("project")
    maven.assertMavenizedModule("m1")
    maven.assertNotMavenizedModule("userModule")

    maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())

    maven.updateAllProjects()
    maven.assertModules("project", "m2", "userModule")
    maven.assertMavenizedModule("project")
    maven.assertMavenizedModule("m2")
    maven.assertNotMavenizedModule("userModule")
  }


  @Test
  fun testModulesWithSlashesRegularAndBack() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>dir\m1</module>
                         <module>dir/m2</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("dir/m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      """.trimIndent())

    maven.createModulePom("dir/m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())

    maven.importProjectAsync()
    maven.assertModules("project", "m1", "m2")

    val roots = maven.projectsTree.rootProjects
    assertEquals(1, roots.size)
    assertEquals("project", roots[0].mavenId.artifactId)

    val modules = maven.projectsTree.getModules(roots[0])
    assertEquals(2, modules.size)
    assertEquals("m1", modules[0].mavenId.artifactId)
    assertEquals("m2", modules[1].mavenId.artifactId)
  }

  @Test
  fun testModulesAreNamedAfterArtifactIds() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <name>name</name>
                       <modules>
                         <module>dir1</module>
                         <module>dir2</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("dir1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <name>name1</name>
      """.trimIndent())

    maven.createModulePom("dir2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      <name>name2</name>
      """.trimIndent())
    maven.importProjectAsync()
    maven.assertModules("project", "m1", "m2")
  }

  @Test
  fun testModulesWithSlashesAtTheEnds() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1/</module>
                         <module>m2\</module>
                         <module>m3//</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
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

    maven.importProjectAsync()
    maven.assertModules("project", "m1", "m2", "m3")
  }

  @Test
  fun testModulesWithSameArtifactId() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>dir1/m</module>
                         <module>dir2/m</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("dir1/m", """
      <groupId>test.group1</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      """.trimIndent())

    maven.createModulePom("dir2/m", """
      <groupId>test.group2</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      """.trimIndent())

    maven.importProjectAsync()
    maven.assertModules("project", "m (1) (test.group1)", "m (2) (test.group2)")
  }

  @Test
  fun testModulesWithSameArtifactIdAndGroup() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>dir1/m</module>
                         <module>dir2/m</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("dir1/m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      """.trimIndent())

    maven.createModulePom("dir2/m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      """.trimIndent())

    maven.importProjectAsync()
    maven.assertModules("project", "m (1)", "m (2)")
  }

  @Test
  fun testModuleWithRelativePath() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>../m</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("../m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      """.trimIndent())

    maven.importProjectAsync()
    maven.assertModules("project", "m")
  }

  @Test
  fun testModuleWithRelativeParent() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>parent</artifactId>
                         <version>1</version>
                         <relativePath>../parent</relativePath>
                       </parent>
                       """.trimIndent())

    maven.createModulePom("../parent", """
      <groupId>test</groupId>
      <artifactId>parent</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      """.trimIndent())

    maven.importProjectAsync()
    maven.assertModules("project")
  }

  @Test
  fun testModulePathsAsProperties() = runBlocking {
    // Maven 4 doesn't allow module paths as properties
    maven.assumeMaven3()
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <properties>
                         <module1>m1</module1>
                         <module2>m2</module2>
                       </properties>
                       <modules>
                         <module>${'$'}{module1}</module>
                         <module>${'$'}{module2}</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      """.trimIndent())

    maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())

    maven.importProjectAsync()
    maven.assertModules("project", "m1", "m2")

    val roots = maven.projectsTree.rootProjects
    assertEquals(1, roots.size)
    assertEquals("project", roots[0].mavenId.artifactId)

    val modules = maven.projectsTree.getModules(roots[0])
    assertEquals(2, modules.size)
    assertEquals("m1", modules[0].mavenId.artifactId)
    assertEquals("m2", modules[1].mavenId.artifactId)
  }

  @Test
  fun testRecursiveParent() = runBlocking {
    maven.createProjectPom("""
                       <parent>
                         <groupId>org.apache.maven.archetype.test</groupId>
                         <artifactId>test-create-2</artifactId>
                         <version>1.0-SNAPSHOT</version>
                       </parent>
                       <artifactId>test-create-2</artifactId>
                       <name>Maven archetype Test create-2-subModule</name>
                       <packaging>pom</packaging>
                       """.trimIndent())
    maven.importProjectAsync()
  }

  @Test
  fun testParentWithoutARelativePath() = runBlocking {
    maven.assumeModel_4_0_0("4.1.0 model does not allow such case: - [FATAL] 'artifactId' contains an expression but should be a constant")
    maven.runWithoutStaticSync()
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <properties>
                         <moduleName>m1</moduleName>
                       </properties>
                       <modules>
                         <module>modules/m</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("modules/m", """
      <groupId>test</groupId>
      <artifactId>${'$'}{moduleName}</artifactId>
      <version>1</version>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
      """.trimIndent())

    maven.importProjectAsync()
    maven.assertModules("project", maven.mn("project", "m1"))

    val roots = maven.projectsTree.rootProjects
    assertEquals(1, roots.size)
    assertEquals("project", roots[0].mavenId.artifactId)

    val modules = maven.projectsTree.getModules(roots[0])
    assertEquals(1, modules.size)
    assertEquals("m1", modules[0].mavenId.artifactId)
  }

  @Test
  fun testModuleWithPropertiesWithParentWithoutARelativePath() = runBlocking {
    maven.assumeModel_4_0_0("4.1.0 model does not allow such case: - [FATAL] 'artifactId' contains an expression but should be a constant")
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <properties>
                         <moduleName>m1</moduleName>
                       </properties>
                       <modules>
                         <module>modules/m</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("modules/m", """
      <groupId>test</groupId>
      <artifactId>${'$'}{moduleName}</artifactId>
      <version>1</version>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
      """.trimIndent())

    maven.importProjectAsync()
    maven.assertModules("project", maven.mn("project", "m1"))

    val roots = maven.projectsTree.rootProjects
    assertEquals(1, roots.size)
    assertEquals("project", roots[0].mavenId.artifactId)

    val modules = maven.projectsTree.getModules(roots[0])
    assertEquals(1, modules.size)
    assertEquals("m1", modules[0].mavenId.artifactId)
  }

  @Test
  fun testParentInLocalRepository() = runBlocking {
    if (!maven.hasMavenInstallation()) return@runBlocking

    val parent = maven.createModulePom("parent",
                                 """
                                                 <groupId>test</groupId>
                                                 <artifactId>parent</artifactId>
                                                 <version>1</version>
                                                 <packaging>pom</packaging>
                                                 <dependencies>
                                                   <dependency>
                                                     <groupId>junit</groupId>
                                                     <artifactId>junit</artifactId>
                                                     <version>4.0</version>
                                                   </dependency>
                                                 </dependencies>
                                                 """.trimIndent())
    maven.executeGoal("parent", "install")

    WriteAction.runAndWait<IOException> { parent.delete(null) }


    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>m</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>parent</artifactId>
                         <version>1</version>
                       </parent>
                       """.trimIndent())

    maven.importProjectAsync()
    maven.assertModules("m")
    maven.assertModuleLibDeps("m", "Maven: junit:junit:4.0")
  }

  @Test
  fun testParentInRemoteRepository() = runBlocking {///Users/dk/.m2/repository
    val defaultRepositoryPath = maven.repositoryPath
    maven.repositoryPath = maven.dir.resolve("repo")
    maven.updateSettingsXml("""
      <profiles>
        <profile>
          <id>custom-repos</id>
          <pluginRepositories>
            <pluginRepository>
              <id>local-file-repo</id>
              <url>file://$defaultRepositoryPath</url>
            </pluginRepository>
            <pluginRepository>
              <id>central</id>
              <url>https://cache-redirector.jetbrains.com/repo1.maven.org/maven2</url>
            </pluginRepository>
          </pluginRepositories>
        </profile>
      </profiles>
      <activeProfiles>
        <activeProfile>custom-repos</activeProfile>
      </activeProfiles>
      
      <localRepository>${maven.repositoryPath}</localRepository>
    """.trimIndent())

    val parentDir = maven.repositoryPath.resolve("asm/asm-parent/3.0")
    assertFalse(Files.exists(parentDir))

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId>asm</groupId>
                         <artifactId>asm-parent</artifactId>
                         <version>3.0</version>
                       </parent>
                       """.trimIndent())

    maven.importProjectAsync()
    maven.assertModules("project")

    assertTrue(Files.exists(parentDir), "File $parentDir doesn't exist")

    assertEquals("asm-parent", maven.projectsTree.rootProjects[0].parentId!!.artifactId)
    assertTrue(Files.exists(parentDir.resolve("asm-parent-3.0.pom")))
  }

  @Test
  fun testReleaseCompilerPropertyInPerSourceTypeModules() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                      <maven.compiler.release>8</maven.compiler.release>
                      <maven.compiler.testRelease>11</maven.compiler.testRelease>
                    </properties>
                     <build>
                      <plugins>
                        <plugin>
                          <artifactId>maven-compiler-plugin</artifactId>
                          <version>3.10.0</version>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent()
    )

    maven.assertModules("project", "project.main", "project.test")
  }

  @Test
  fun testProjectWithBuiltExtension() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                     <extensions>
                       <extension>
                         <groupId>org.apache.maven.wagon</groupId>
                         <artifactId>wagon-webdav</artifactId>
                         <version>1.0-beta-2</version>
                        </extension>
                      </extensions>
                    </build>
                    """.trimIndent())
    maven.assertModules("project")
  }

  @Test
  fun testUsingPropertyInBuildExtensionsOfChildModule() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <properties>
                         <xxx>1.0-beta-2</xxx>
                       </properties>
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
      <build>
        <extensions>
          <extension>
            <groupId>org.apache.maven.wagon</groupId>
            <artifactId>wagon-webdav</artifactId>
            <version>${'$'}{xxx}</version>
          </extension>
        </extensions>
      </build>
      """.trimIndent())

    maven.importProjectAsync()
    maven.assertModules("project", maven.mn("project", "m"))
  }

  @Test
  fun testFileProfileActivationInParentPom() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                         <profiles>
                           <profile>
                             <id>xxx</id>
                             <dependencies>
                               <dependency>
                                 <groupId>junit</groupId>
                                 <artifactId>junit</artifactId>
                                 <version>4.0</version>
                               </dependency>
                             </dependencies>
                             <activation>
                               <file>
                                 <exists>src/io.properties</exists>
                               </file>
                             </activation>
                           </profile>
                         </profiles>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    maven.createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
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
    maven.createProjectSubFile("m2/src/io.properties", "")

    maven.importProjectAsync()

    maven.assertModules("project", maven.mn("project", "m1"), maven.mn("project", "m2"))
    maven.assertModuleLibDeps(maven.mn("project", "m1"))
    maven.assertModuleLibDeps(maven.mn("project", "m2"), "Maven: junit:junit:4.0")
  }

  @Test
  fun testFileProfileActivationInParentPom2() = runBlocking {
    maven.assumeMaven4()
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                         <profiles>
                           <profile>
                             <id>xxx</id>
                             <dependencies>
                               <dependency>
                                 <groupId>junit</groupId>
                                 <artifactId>junit</artifactId>
                                 <version>4.0</version>
                               </dependency>
                             </dependencies>
                             <activation>
                               <file>
                                 <exists>src/io.properties</exists>
                               </file>
                             </activation>
                           </profile>
                         </profiles>
                       <modules>
                         <module>m1</module>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    val m1 =maven.createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
      """.trimIndent())

    val m2 = maven.createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
      """.trimIndent())
    maven.createProjectSubFile("m2/src/io.properties", "")

    assertTrue(Files.exists(m2.toNioPath().parent.resolve("src/io.properties")), "File src/io.properties not found")

    maven.importProjectAsync()

    val m1Project = maven.projectsManager.findProject(m1)!!
    assertSameElements("m1 enabled profiles", m1Project.activatedProfilesIds.enabledProfiles, emptyList())

    val m2Project = maven.projectsManager.findProject(m2)!!
    assertSameElements("m2 enabled profiles", m2Project.activatedProfilesIds.enabledProfiles, listOf("xxx"))
  }

  @Test
  fun testProjectWithProfiles() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <profiles>
                         <profile>
                           <id>one</id>
                           <activation>
                             <activeByDefault>false</activeByDefault>
                           </activation>
                           <properties>
                             <junit.version>4.0</junit.version>
                           </properties>
                         </profile>
                         <profile>
                           <id>two</id>
                           <activation>
                             <activeByDefault>false</activeByDefault>
                           </activation>
                           <properties>
                             <junit.version>3.8.1</junit.version>
                           </properties>
                         </profile>
                       </profiles>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <version>${'$'}{junit.version}</version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.importProjectWithProfiles("one")
    maven.assertModules("project")

    maven.assertModuleLibDeps("project", "Maven: junit:junit:4.0")

    maven.importProjectWithProfiles("two")
    maven.assertModules("project")

    maven.assertModuleLibDeps("project", "Maven: junit:junit:3.8.1")
  }

  @Test
  fun testProjectWithDefaultProfile() = runBlocking {
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <profiles>
                         <profile>
                           <id>one</id>
                           <activation>
                             <activeByDefault>true</activeByDefault>
                           </activation>
                           <properties>
                             <junit.version>4.0</junit.version>
                           </properties>
                         </profile>
                       </profiles>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <version>${'$'}{junit.version}</version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.importProjectAsync()
    maven.assertModules("project")

    maven.assertModuleLibDeps("project", "Maven: junit:junit:4.0")
  }

  @Test
  fun testErrorImportArtifactVersionCannotBeEmpty() = runBlocking {
    maven.assumeVersionMoreThan("3.0.5")
    maven.createProjectPom("""
                       <groupId>test</groupId>
                         <artifactId>parent</artifactId>
                         <packaging>pom</packaging>
                         <version>1</version>
                         <modules>
                          <module>m1</module>
                         </modules>
                         <properties>
                          <junit.group.id>junit</junit.group.id>
                          <junit.artifact.id>junit</junit.artifact.id>
                         </properties>
                         <profiles>
                           <profile>
                             <id>profile-test</id>
                             <dependencies>
                               <dependency>
                                 <groupId>${'$'}{junit.group.id}</groupId>
                                 <artifactId>${'$'}{junit.artifact.id}</artifactId>
                               </dependency>
                             </dependencies>
                           </profile>
                         </profiles>
                         
                         <dependencyManagement>
                           <dependencies>
                             <dependency>
                               <groupId>junit</groupId>
                               <artifactId>junit</artifactId>
                               <version>4.0</version> 
                             </dependency>
                           </dependencies>
                         </dependencyManagement>
                         """.trimIndent())

    maven.createModulePom("m1", """
      <parent>
      <groupId>test</groupId>
      <artifactId>parent</artifactId>
      <version>1</version>${'\t'}
      </parent>
      <artifactId>m1</artifactId>${'\t'}
      <dependencies>
        <dependency>
          <groupId>junit</groupId>
          <artifactId>junit</artifactId>
        </dependency>
      </dependencies>
      """.trimIndent())

    maven.importProjectWithProfiles("profile-test")
    maven.assertModuleLibDeps("parent", "Maven: junit:junit:4.0")
    if (maven.isMaven4) {
      maven.assertModuleLibDeps("m1", "Maven: junit:junit:4.0")
    }
  }

  @Test
  fun testProjectWithMavenConfigCustomUserSettingsXml() = runBlocking {
    maven.runWithoutStaticSync()
    maven.assumeModel_4_0_0("4.1.0 model does not allow such case: - [FATAL] 'artifactId' contains an expression but should be a constant")
    val configFile = maven.createProjectSubFile(".mvn/maven.config", "-s .mvn/custom-settings.xml")
    val settingsFile = maven.createProjectSubFile(".mvn/custom-settings.xml",
                         """
                           <settings>
                               <profiles>
                                   <profile>
                                       <id>custom1</id>
                                       <properties>
                                           <projectName>customName</projectName>
                                       </properties>
                                   </profile>
                               </profiles>
                               <activeProfiles>
                                   <activeProfile>custom1</activeProfile>
                               </activeProfiles></settings>
                               """.trimIndent())
    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>${'$'}{projectName}</artifactId>
                       <version>1</version>
                       """.trimIndent())
    maven.refreshFiles(listOf(configFile, settingsFile))

    val settings = maven.mavenGeneralSettings
    settings.setUserSettingsFile("")
    settings.isUseMavenConfig = true
    maven.importProjectAsync()
    maven.assertModules("customName")
  }

  @Test
  fun testProjectWithActiveProfilesFromSettingsXml() = runBlocking {
    maven.updateSettingsXml("""
                        <activeProfiles>
                          <activeProfile>one</activeProfile>
                        </activeProfiles>
                        """.trimIndent())

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <profiles>
                         <profile>
                           <id>one</id>
                           <properties>
                               <myProp>1.2.3</myProp>
                           </properties>
                         </profile>
                       </profiles>
                       <dependencies>
                           <dependency>
                               <groupId>group</groupId>
                               <artifactId>artifact</artifactId>
                               <version>${'$'}{myProp}</version>
                           </dependency>
                       </dependencies>
                       """.trimIndent())

    maven.importProjectAsync()
    maven.assertModuleLibDeps("project", "Maven: group:artifact:1.2.3")
  }

  @Test
  fun testProjectWithActiveProfilesAndInactiveFromSettingsXml() = runBlocking {
    maven.runWithoutStaticSync()
    maven.assumeModel_4_0_0("4.1.0 model does not allow such case: - [FATAL] 'artifactId' contains an expression but should be a constant")
    maven.updateSettingsXml("""
                        <activeProfiles>
                          <activeProfile>one</activeProfile>
                          <activeProfile>two</activeProfile>
                        </activeProfiles>
                        """.trimIndent())

    maven.createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>${'$'}{projectName}</artifactId>
                       <version>1</version>
                       <profiles>
                         <profile>
                           <id>one</id>
                           <properties>
                             <projectName>project-one</projectName>
                           </properties>
                         </profile>
                         <profile>
                           <id>two</id>
                           <properties>
                             <projectName>project-two</projectName>
                           </properties>
                         </profile>
                       </profiles>
                       """.trimIndent())

    maven.importProjectsWithProfiles(listOf(maven.projectPom), "one")
    maven.assertModules("project-two")
  }
}
