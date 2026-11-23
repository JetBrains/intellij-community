// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.getModule
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.projectPath
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.task.ModuleBuildTask
import com.intellij.task.impl.ModuleBuildTaskImpl
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.execution.build.mavenBuildCommands
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/** The Maven runs of a delegated Build Project and Build Module (IDEA-395862). */
@TestApplication
class MavenDelegatedBuildCommandsTest {

  private val maven by mavenImportingFixture()

  @Test
  fun `delegated build of all modules builds the root POM with all the modules`() = runBlocking {
    createProject()

    // The module order of the reproducer of IDEA-395862: a submodule before the root module.
    val commands = mavenBuildCommands(maven.project, listOf(rebuild("beta"), rebuild("alpha"), rebuild("zz-root")))

    assertEquals(1, commands.size, commands.toString())
    val command = commands.single()
    assertEquals(maven.projectPath.toCanonicalPath(), command.workingDirPath)
    assertEquals("pom.xml", command.pomFileName)
    assertEquals("clean", command.goals.first(), command.goals.toString())
    val projectsIndex = command.goals.indexOf("--projects")
    assertEquals(
      setOf("demo:beta", "demo:alpha", "demo:zz-root"),
      command.goals[projectsIndex + 1].split(",").toSet(),
      command.goals.toString(),
    )
  }

  @Test
  fun `delegated build of one module builds the POM of the module`() = runBlocking {
    createProject()

    val commands = mavenBuildCommands(maven.project, listOf(rebuild("beta")))

    assertEquals(1, commands.size, commands.toString())
    val command = commands.single()
    assertEquals(maven.projectPath.resolve("beta").toCanonicalPath(), command.workingDirPath)
    assertEquals("pom.xml", command.pomFileName)
    assertFalse("--projects" in command.goals, command.goals.toString())
  }

  /** The task of Rebuild Project for the module [name]. */
  private fun rebuild(name: String): ModuleBuildTask = ModuleBuildTaskImpl(maven.getModule(name), false, false, false)

  private suspend fun createProject() {
    maven.createProjectPom("""
      <groupId>demo</groupId>
      <artifactId>zz-root</artifactId>
      <version>1.0-SNAPSHOT</version>
      <packaging>pom</packaging>
      <modules>
        <module>alpha</module>
        <module>beta</module>
      </modules>
    """.trimIndent())
    maven.createModulePom("alpha", """
      <parent>
        <groupId>demo</groupId>
        <artifactId>zz-root</artifactId>
        <version>1.0-SNAPSHOT</version>
      </parent>
      <artifactId>alpha</artifactId>
    """.trimIndent())
    maven.createModulePom("beta", """
      <parent>
        <groupId>demo</groupId>
        <artifactId>zz-root</artifactId>
        <version>1.0-SNAPSHOT</version>
      </parent>
      <artifactId>beta</artifactId>
      <dependencies>
        <dependency>
          <groupId>demo</groupId>
          <artifactId>alpha</artifactId>
          <version>1.0-SNAPSHOT</version>
        </dependency>
      </dependencies>
    """.trimIndent())
    maven.importProjectAsync()
  }
}
