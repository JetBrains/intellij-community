// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.module.ModuleManager
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.project.MavenImportListener
import org.jetbrains.idea.maven.project.MavenProject
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

    project.messageBus.connect(testRootDisposable)
      .subscribe(MavenImportListener.TOPIC, object : MavenImportListener {
        override fun projectResolutionStarted(mavenProjects: MutableCollection<MavenProject>) {
          resolvedProjects.addAll(mavenProjects)
        }
      })

    importProjectAsync()

    assertEquals(1, resolvedProjects.size)
    assertEquals(parentFile.path, resolvedProjects[0].path)
  }
}
