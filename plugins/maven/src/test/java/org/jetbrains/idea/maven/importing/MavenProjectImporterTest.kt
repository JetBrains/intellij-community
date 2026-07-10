// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.testRootDisposable
import com.intellij.maven.testFramework.utils.RealMavenPreventionFixture
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.project.MavenImportListener
import org.jetbrains.idea.maven.project.MavenProject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenProjectImporterTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  


  @Test
  fun `import should stop if only static sync is enabled`() = runBlocking {
    val noRealMaven = RealMavenPreventionFixture(maven.project)
    try {
      noRealMaven.setUp()
      Registry.get("maven.preimport.only").setValue(true, maven.testRootDisposable)
      maven.importProjectAsync("""
                <groupId>group</groupId>
                <artifactId>onlystatic</artifactId>
                <version>1</version>
                """.trimIndent())
      maven.assertModules("onlystatic")
    }
    finally {
      noRealMaven.tearDown()
    }


  }

  @Test
  fun `test maven import modules properly named`() = runBlocking {
    val parentFile = maven.createProjectPom("""
                <groupId>group</groupId>
                <artifactId>parent</artifactId>
                <version>1</version>
                <packaging>pom</packaging>
                <modules>
                  <module>project</module>
                </modules>
                """.trimIndent())

    val projectFile = maven.createModulePom("project", """
                <artifactId>project</artifactId>
                <version>1</version>
                <parent>
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                </parent>
                """.trimIndent())

    maven.importProjectAsync()

    val moduleManager = ModuleManager.getInstance(maven.project)
    val modules = moduleManager.modules
    assertEquals(2, modules.size)

    val parentModule = moduleManager.findModuleByName("parent")
    assertNotNull(parentModule)

    val projectModule = moduleManager.findModuleByName("project")
    assertNotNull(projectModule)
  }

  @Test
  fun `test do not resolve dependencies for ignored poms`() = runBlocking {
    val parentFile = maven.createProjectPom("""
                <groupId>group</groupId>
                <artifactId>parent</artifactId>
                <version>1</version>
                <packaging>pom</packaging>
                <modules>
                  <module>project</module>
                </modules>
                """.trimIndent())

    val projectFile = maven.createModulePom("project", """
                <artifactId>project</artifactId>
                <version>1</version>
                <parent>
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                </parent>
                """.trimIndent())

    maven.projectsManager.initForTests()
    maven.projectsManager.setIgnoredStateForPoms(listOf(projectFile.path), true)
    assertTrue(maven.projectsManager.projectsTree.isIgnored(MavenProject(projectFile)))

    val resolvedProjects = mutableListOf<MavenProject>()

    maven.project.messageBus.connect(maven.testRootDisposable)
      .subscribe(MavenImportListener.TOPIC, object : MavenImportListener {
        override fun projectResolutionStarted(mavenProjects: MutableCollection<MavenProject>) {
          resolvedProjects.addAll(mavenProjects)
        }
      })

    maven.importProjectAsync()

    assertEquals(1, resolvedProjects.size)
    assertEquals(parentFile.path, resolvedProjects[0].path)
  }
}
