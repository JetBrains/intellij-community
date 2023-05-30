// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.module.ModuleManager
import com.intellij.testFramework.replaceService
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectResolver
import org.junit.Test

class MavenProjectImporterTest : MavenMultiVersionImportingTestCase() {
  @Test
  fun `test maven import modules properly named`() {
    val previewModule = MavenImportUtil.createPreviewModule(myProject, myProjectRoot)

    myProjectsManager.addManagedFilesWithProfiles(listOf(myProjectRoot), MavenExplicitProfiles(emptyList(), emptyList()), previewModule)

    val parentFile = createProjectPom("""
                <groupId>group</groupId>
                <artifactId>parent</artifactId>
                <version>1</version>
                <packaging>pom</packaging>
                <modules>
                  <module>project</module>
                </modules>
                """.trimIndent())

    val projectFile = createModulePom("project", """
                <artifactId>project</artifactId>
                <version>1</version>
                <parent>
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                </parent>
                """.trimIndent())

    importProject()

    val moduleManager = ModuleManager.getInstance(myProject)
    val modules = moduleManager.modules
    assertEquals(2, modules.size)

    val parentModule = moduleManager.findModuleByName("parent")
    assertNotNull(parentModule)

    val projectModule = moduleManager.findModuleByName("project")
    assertNotNull(projectModule)
  }

  @Test
  fun `test do not resolve dependencies for ignored poms`() {
    val parentFile = createProjectPom("""
                <groupId>group</groupId>
                <artifactId>parent</artifactId>
                <version>1</version>
                <packaging>pom</packaging>
                <modules>
                  <module>project</module>
                </modules>
                """.trimIndent())

    val projectFile = createModulePom("project", """
                <artifactId>project</artifactId>
                <version>1</version>
                <parent>
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                </parent>
                """.trimIndent())

    myProjectsManager.initForTests()
    myProjectsManager.setIgnoredStateForPoms(listOf(projectFile.path), true)
    assertTrue(myProjectsManager.projectsTree.isIgnored(MavenProject(projectFile)))

    val resolvedProjects = mutableListOf<MavenProject>()

    val resolverMock = MavenProjectResolver { mavenProjects, _, _, _, _, _, _ ->
      resolvedProjects.addAll(mavenProjects)
      MavenProjectResolver.MavenProjectResolutionResult(mapOf())
    }

    myProject.replaceService(MavenProjectResolver::class.java, resolverMock, testRootDisposable)

    importProject()

    assertEquals(1, resolvedProjects.size)
    assertEquals(parentFile.path, resolvedProjects[0].path)
  }
}
