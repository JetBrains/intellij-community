// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.JpsProjectFileEntitySource.FileInDirectory
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.testFramework.PsiTestUtil
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.IOException
import java.nio.file.Files

class StructureImportingTest : MavenMultiVersionImportingTestCase() {
  @Test
  fun testInheritProjectJdkForModules() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    assertTrue(ModuleRootManager.getInstance(getModule("project")).isSdkInherited())
  }

  @Test
  fun testDoNotResetSomeSettingsAfterReimport() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    val sdk = setupJdkForModule("project")

    updateAllProjects()

    assertFalse(ModuleRootManager.getInstance(getModule("project")).isSdkInherited())
    assertEquals(sdk, ModuleRootManager.getInstance(getModule("project")).getSdk())
  }

  @Test
  fun testImportWithAlreadyExistingModules() = runBlocking {
    createModule("m1")
    createModule("m2")
    createModule("m3")
    createModule("m4")

    PsiTestUtil.addSourceRoot(getModule("m1"), createProjectSubFile("m1/user-sources"))
    PsiTestUtil.addSourceRoot(getModule("m2"), createProjectSubFile("m2/user-sources"))
    PsiTestUtil.addSourceRoot(getModule("m3"), createProjectSubFile("m3/user-sources"))
    PsiTestUtil.addSourceRoot(getModule("m4"), createProjectSubFile("m4/user-sources"))
    PsiTestUtil.addSourceRoot(getModule("m4"), createProjectSubFile("m4/src/main/java"))

    assertModules("m1", "m2", "m3", "m4")
    assertSources("m1", "user-sources")
    assertSources("m2", "user-sources")
    assertSources("m3", "user-sources")
    assertSources("m4", "user-sources", "src/main/java")

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
      """.trimIndent())
    createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())

    createModulePom("m4", """
      <groupId>test</groupId>
      <artifactId>m4</artifactId>
      <version>1</version>
      """.trimIndent())

    createProjectSubDirs("m1/src/main/java",
                         "m2/src/main/java",
                         "m3/src/main/java")

    importProjectAsync()
    assertModules("project", "m1", "m2", "m3", "m4")

    assertSources("m1", "user-sources", "src/main/java")
    assertSources("m2", "user-sources", "src/main/java")
    assertSources("m3", "user-sources")
    assertSources("m4", "user-sources", "src/main/java")

    val mFour = project.workspaceModel.currentSnapshot.resolve(ModuleId("m4"))
    assertNotNull(mFour)
    val sourceEntitySource = mFour!!.contentRoots.first().sourceRoots.first { it.url.url.endsWith("java") }.entitySource
    assertTrue(sourceEntitySource is FileInDirectory)
  }

  @Test
  fun testImportWithAlreadyExistingModulesWithCustomExcludes() = runBlocking {
    createModule("m1")
    createModule("m2")
    createModule("m3")

    PsiTestUtil.addExcludedRoot(getModule("m1"), createProjectSubFile("m1/user-sources"))
    PsiTestUtil.addExcludedRoot(getModule("m2"), createProjectSubFile("m2/user-sources"))
    PsiTestUtil.addExcludedRoot(getModule("m3"), createProjectSubFile("m3/user-sources"))

    assertModules("m1", "m2", "m3")
    assertExcludes("m1", "user-sources")
    assertExcludes("m2", "user-sources")
    assertExcludes("m3", "user-sources")

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
      """.trimIndent())
    createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())

    createProjectSubDirs("m1/src/main/java",
                         "m2/src/main/java",
                         "m3/src/main/java")

    importProjectAsync()
    assertModules("project", "m1", "m2", "m3")

    assertExcludes("m1", "target", "user-sources")
    assertExcludes("m2", "target", "user-sources")
    assertExcludes("m3", "user-sources")
  }

  /**
   * Keep the module if it has some custom content roots that doesn't intersect with imported
   */
  @Test
  fun testImportWithAlreadyExistingModuleWithDifferentNameButSameContentRoot() = runBlocking {
    val userModuleWithConflictingRoot = createModule("userModuleWithConflictingRoot")
    PsiTestUtil.removeAllRoots(userModuleWithConflictingRoot, null)
    PsiTestUtil.addContentRoot(userModuleWithConflictingRoot, projectRoot)
    val anotherContentRoot = createProjectSubFile("m1/user-content")
    PsiTestUtil.addContentRoot(userModuleWithConflictingRoot, anotherContentRoot)
    assertContentRoots(userModuleWithConflictingRoot.getName(), projectPath.toString(), anotherContentRoot.getPath())

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    importProjectAsync()
    assertModules("project", userModuleWithConflictingRoot.getName())
    assertContentRoots("project", projectPath.toString())
    assertContentRoots(userModuleWithConflictingRoot.getName(), anotherContentRoot.getPath())
  }

  @Test
  fun testImportWithAlreadyExistingModuleWithPartiallySameContentRoots() = runBlocking {
    val userModuleWithConflictingRoot = createModule("userModuleWithConflictingRoot")
    PsiTestUtil.removeAllRoots(userModuleWithConflictingRoot, null)
    PsiTestUtil.addContentRoot(userModuleWithConflictingRoot, projectRoot)
    assertContentRoots(userModuleWithConflictingRoot.getName(), projectPath.toString())

    val userModuleWithUniqueRoot = createModule("userModuleWithUniqueRoot")
    assertContentRoots(userModuleWithUniqueRoot.getName(), "$projectPath/userModuleWithUniqueRoot")

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       """.trimIndent())

    importProjectAsync()
    assertModules("project", userModuleWithUniqueRoot.getName())
    assertContentRoots("project", projectPath.toString())
    assertContentRoots(userModuleWithUniqueRoot.getName(), "$projectPath/userModuleWithUniqueRoot")
  }

  @Test
  fun testMarkModulesAsMavenized() = runBlocking {
    createModule("userModule")

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m1</module>
                       </modules>
                       """.trimIndent())

    createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      """.trimIndent())

    importProjectAsync()
    assertModules("project", "m1", "userModule")
    assertMavenizedModule("project")
    assertMavenizedModule("m1")
    assertNotMavenizedModule("userModule")

    updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>m2</module>
                       </modules>
                       """.trimIndent())

    createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())

    updateAllProjects()
    assertModules("project", "m2", "userModule")
    assertMavenizedModule("project")
    assertMavenizedModule("m2")
    assertNotMavenizedModule("userModule")
  }


  @Test
  fun testModulesWithSlashesRegularAndBack() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>dir\m1</module>
                         <module>dir/m2</module>
                       </modules>
                       """.trimIndent())

    createModulePom("dir/m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      """.trimIndent())

    createModulePom("dir/m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())

    importProjectAsync()
    assertModules("project", "m1", "m2")

    val roots = projectsTree.rootProjects
    assertEquals(1, roots.size)
    assertEquals("project", roots[0].mavenId.artifactId)

    val modules = projectsTree.getModules(roots[0])
    assertEquals(2, modules.size)
    assertEquals("m1", modules[0].mavenId.artifactId)
    assertEquals("m2", modules[1].mavenId.artifactId)
  }

  @Test
  fun testModulesAreNamedAfterArtifactIds() = runBlocking {
    createProjectPom("""
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

    createModulePom("dir1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <name>name1</name>
      """.trimIndent())

    createModulePom("dir2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      <name>name2</name>
      """.trimIndent())
    importProjectAsync()
    assertModules("project", "m1", "m2")
  }

  @Test
  fun testModulesWithSlashesAtTheEnds() = runBlocking {
    createProjectPom("""
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

    createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
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

    importProjectAsync()
    assertModules("project", "m1", "m2", "m3")
  }

  @Test
  fun testModulesWithSameArtifactId() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>dir1/m</module>
                         <module>dir2/m</module>
                       </modules>
                       """.trimIndent())

    createModulePom("dir1/m", """
      <groupId>test.group1</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      """.trimIndent())

    createModulePom("dir2/m", """
      <groupId>test.group2</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      """.trimIndent())

    importProjectAsync()
    assertModules("project", "m (1) (test.group1)", "m (2) (test.group2)")
  }

  @Test
  fun testModulesWithSameArtifactIdAndGroup() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>dir1/m</module>
                         <module>dir2/m</module>
                       </modules>
                       """.trimIndent())

    createModulePom("dir1/m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      """.trimIndent())

    createModulePom("dir2/m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      """.trimIndent())

    importProjectAsync()
    assertModules("project", "m (1)", "m (2)")
  }

  @Test
  fun testModuleWithRelativePath() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <packaging>pom</packaging>
                       <version>1</version>
                       <modules>
                         <module>../m</module>
                       </modules>
                       """.trimIndent())

    createModulePom("../m", """
      <groupId>test</groupId>
      <artifactId>m</artifactId>
      <version>1</version>
      """.trimIndent())

    importProjectAsync()
    assertModules("project", "m")
  }

  @Test
  fun testModuleWithRelativeParent() = runBlocking {
    createProjectPom("""
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

    createModulePom("../parent", """
      <groupId>test</groupId>
      <artifactId>parent</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      """.trimIndent())

    importProjectAsync()
    assertModules("project")
  }

  @Test
  fun testModulePathsAsProperties() = runBlocking {
    // Maven 4 doesn't allow module paths as properties
    assumeMaven3()
    createProjectPom("""
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

    createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      """.trimIndent())

    createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <version>1</version>
      """.trimIndent())

    importProjectAsync()
    assertModules("project", "m1", "m2")

    val roots = projectsTree.rootProjects
    assertEquals(1, roots.size)
    assertEquals("project", roots[0].mavenId.artifactId)

    val modules = projectsTree.getModules(roots[0])
    assertEquals(2, modules.size)
    assertEquals("m1", modules[0].mavenId.artifactId)
    assertEquals("m2", modules[1].mavenId.artifactId)
  }

  @Test
  fun testRecursiveParent() = runBlocking {
    createProjectPom("""
                       <parent>
                         <groupId>org.apache.maven.archetype.test</groupId>
                         <artifactId>test-create-2</artifactId>
                         <version>1.0-SNAPSHOT</version>
                       </parent>
                       <artifactId>test-create-2</artifactId>
                       <name>Maven archetype Test create-2-subModule</name>
                       <packaging>pom</packaging>
                       """.trimIndent())
    importProjectAsync()
  }

  @Test
  fun testParentWithoutARelativePath() = runBlocking {
    runWithoutStaticSync()
    createProjectPom("""
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

    createModulePom("modules/m", """
      <groupId>test</groupId>
      <artifactId>${'$'}{moduleName}</artifactId>
      <version>1</version>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
      """.trimIndent())

    importProjectAsync()
    assertModules("project", mn("project", "m1"))

    val roots = projectsTree.rootProjects
    assertEquals(1, roots.size)
    assertEquals("project", roots[0].mavenId.artifactId)

    val modules = projectsTree.getModules(roots[0])
    assertEquals(1, modules.size)
    assertEquals("m1", modules[0].mavenId.artifactId)
  }

  @Test
  fun testModuleWithPropertiesWithParentWithoutARelativePath() = runBlocking {
    createProjectPom("""
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

    createModulePom("modules/m", """
      <groupId>test</groupId>
      <artifactId>${'$'}{moduleName}</artifactId>
      <version>1</version>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
      """.trimIndent())

    importProjectAsync()
    assertModules("project", mn("project", "m1"))

    val roots = projectsTree.rootProjects
    assertEquals(1, roots.size)
    assertEquals("project", roots[0].mavenId.artifactId)

    val modules = projectsTree.getModules(roots[0])
    assertEquals(1, modules.size)
    assertEquals("m1", modules[0].mavenId.artifactId)
  }

  @Test
  fun testParentInLocalRepository() = runBlocking {
    if (!hasMavenInstallation()) return@runBlocking

    val parent = createModulePom("parent",
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
    executeGoal("parent", "install")

    WriteAction.runAndWait<IOException> { parent.delete(null) }


    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>m</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId>test</groupId>
                         <artifactId>parent</artifactId>
                         <version>1</version>
                       </parent>
                       """.trimIndent())

    importProjectAsync()
    assertModules("m")
    assertModuleLibDeps("m", "Maven: junit:junit:4.0")
  }

  @Test
  fun testParentInRemoteRepository() = runBlocking {
    val pathToJUnit = "asm/asm-parent/3.0"
    val parentDir = repositoryPath.resolve(pathToJUnit)

    removeFromLocalRepository(pathToJUnit)
    assertFalse(Files.exists(parentDir))

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <parent>
                         <groupId>asm</groupId>
                         <artifactId>asm-parent</artifactId>
                         <version>3.0</version>
                       </parent>
                       """.trimIndent())

    importProjectAsync()
    assertModules("project")

    assertTrue("File $parentDir doesn't exist", Files.exists(parentDir))

    assertEquals("asm-parent", projectsTree.rootProjects[0].parentId!!.artifactId)
    assertTrue(Files.exists(parentDir.resolve("asm-parent-3.0.pom")))
  }

  @Test
  fun testReleaseCompilerPropertyInPerSourceTypeModules() = runBlocking {
    importProjectAsync("""
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

    assertModules("project", "project.main", "project.test")
  }

  @Test
  fun testProjectWithBuiltExtension() = runBlocking {
    importProjectAsync("""
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
    assertModules("project")
  }

  @Test
  fun testUsingPropertyInBuildExtensionsOfChildModule() = runBlocking {
    createProjectPom("""
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

    createModulePom("m", """
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

    importProjectAsync()
    assertModules("project", mn("project", "m"))
  }

  @Test
  fun testFileProfileActivationInParentPom() = runBlocking {
    createProjectPom("""
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

    createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
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
    createProjectSubFile("m2/src/io.properties", "")

    importProjectAsync()

    assertModules("project", mn("project", "m1"), mn("project", "m2"))
    assertModuleLibDeps(mn("project", "m1"))
    assertModuleLibDeps(mn("project", "m2"), "Maven: junit:junit:4.0")
  }

  @Test
  fun testFileProfileActivationInParentPom2() = runBlocking {
    assumeMaven4()
    createProjectPom("""
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

    val m1 =createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
      """.trimIndent())

    val m2 = createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2</artifactId>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
      """.trimIndent())
    createProjectSubFile("m2/src/io.properties", "")

    assertTrue("File src/io.properties not found", Files.exists(m2.toNioPath().parent.resolve("src/io.properties")))

    importProjectAsync()

    val m1Project = projectsManager.findProject(m1)!!
    assertSameElements("m1 enabled profiles", m1Project.activatedProfilesIds.enabledProfiles, emptyList())

    val m2Project = projectsManager.findProject(m2)!!
    assertSameElements("m2 enabled profiles", m2Project.activatedProfilesIds.enabledProfiles, listOf("xxx"))
  }

  @Test
  fun testProjectWithProfiles() = runBlocking {
    createProjectPom("""
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

    importProjectWithProfiles("one")
    assertModules("project")

    assertModuleLibDeps("project", "Maven: junit:junit:4.0")

    importProjectWithProfiles("two")
    assertModules("project")

    assertModuleLibDeps("project", "Maven: junit:junit:3.8.1")
  }

  @Test
  fun testProjectWithDefaultProfile() = runBlocking {
    createProjectPom("""
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

    importProjectAsync()
    assertModules("project")

    assertModuleLibDeps("project", "Maven: junit:junit:4.0")
  }

  @Test
  fun testErrorImportArtifactVersionCannotBeEmpty() = runBlocking {
    assumeVersionMoreThan("3.0.5")
    createProjectPom("""
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

    createModulePom("m1", """
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

    doImportProjectsAsync(listOf(projectPom), false, "profile-test")
  }

  @Test
  fun testProjectWithMavenConfigCustomUserSettingsXml() = runBlocking {
    runWithoutStaticSync()
    val configFile = createProjectSubFile(".mvn/maven.config", "-s .mvn/custom-settings.xml")
    val settingsFile = createProjectSubFile(".mvn/custom-settings.xml",
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
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>${'$'}{projectName}</artifactId>
                       <version>1</version>
                       """.trimIndent())
    refreshFiles(listOf(configFile, settingsFile))

    val settings = mavenGeneralSettings
    settings.setUserSettingsFile("")
    settings.isUseMavenConfig = true
    importProjectAsync()
    assertModules("customName")
  }

  @Test
  fun testProjectWithActiveProfilesFromSettingsXml() = runBlocking {
    updateSettingsXml("""
                        <activeProfiles>
                          <activeProfile>one</activeProfile>
                        </activeProfiles>
                        """.trimIndent())

    createProjectPom("""
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

    importProjectAsync()
    assertModuleLibDeps("project", "Maven: group:artifact:1.2.3")
  }

  @Test
  fun testProjectWithActiveProfilesAndInactiveFromSettingsXml() = runBlocking {
    runWithoutStaticSync()
    updateSettingsXml("""
                        <activeProfiles>
                          <activeProfile>one</activeProfile>
                          <activeProfile>two</activeProfile>
                        </activeProfiles>
                        """.trimIndent())

    createProjectPom("""
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

    val disabledProfiles = listOf("one")
    doImportProjectsAsync(listOf(projectPom), true, disabledProfiles)
    assertModules("project-two")
  }
}
