// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.module.ModuleManager
import com.intellij.platform.util.progress.RawProgressReporter
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.buildtool.MavenEventHandler
import org.jetbrains.idea.maven.project.*
import org.junit.Test

class MavenProjectImporterTest : MavenMultiVersionImportingTestCase() {

  @Test
  fun `test maven import modules properly named`() = runBlocking {
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

    importProjectAsync()

    val moduleManager = ModuleManager.getInstance(project)
    val modules = moduleManager.modules
    assertEquals(2, modules.size)

    val parentModule = moduleManager.findModuleByName("parent")
    assertNotNull(parentModule)

    val projectModule = moduleManager.findModuleByName("project")
    assertNotNull(projectModule)
  }

  @Test
  fun `test do not resolve dependencies for ignored poms`() = runBlocking {
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

    projectsManager.initForTests()
    projectsManager.setIgnoredStateForPoms(listOf(projectFile.path), true)
    assertTrue(projectsManager.projectsTree.isIgnored(MavenProject(projectFile)))

    val resolvedProjects = mutableListOf<MavenProject>()

    val resolverMock: MavenProjectResolver = object : MavenProjectResolver {
      override suspend fun resolve(
        mavenProjects: Collection<MavenProject>,
        tree: MavenProjectsTree,
        generalSettings: MavenGeneralSettings,
        embeddersManager: MavenEmbeddersManager,
        progressReporter: RawProgressReporter,
        eventHandler: MavenEventHandler
      ): MavenProjectResolver.MavenProjectResolutionResult {
        resolvedProjects.addAll(mavenProjects)
        return MavenProjectResolver.MavenProjectResolutionResult(emptyMap())
      }
    }

    project.replaceService(MavenProjectResolver::class.java, resolverMock, testRootDisposable)

    importProjectAsync()

    assertEquals(1, resolvedProjects.size)
    assertEquals(parentFile.path, resolvedProjects[0].path)
  }
}
