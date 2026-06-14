// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.runBlocking
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertContain
import com.intellij.maven.testFramework.fixtures.assertDoNotContain
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.importProjectsAsync
import com.intellij.maven.testFramework.fixtures.initProjectsManager
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.updateAllProjects
import com.intellij.maven.testFramework.fixtures.updateProjectPom
import org.jetbrains.idea.maven.fixtures.waitForImportWithinTimeout
import org.jetbrains.idea.maven.tasks.MavenKeymapExtension
import org.jetbrains.idea.maven.tasks.MavenShortcutsManager
import java.io.IOException
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import com.intellij.testFramework.UsefulTestCase.assertEmpty

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenShortcutsManagerTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion,
    skipPluginResolution = false,
  )
  

  private var myShortcutsManager: MavenShortcutsManager? = null


  @BeforeEach
  fun setUp() {
    myShortcutsManager = MavenShortcutsManager.getInstance(maven.project)
    myShortcutsManager!!.doInit(maven.project)

    // turn auto-import on
    maven.initProjectsManager(true)
  }

  public @AfterEach
  fun tearDown() {
    try {
      MavenKeymapExtension.clearActions(maven.project)
    }
    finally {
      myShortcutsManager = null
    }
  }

  @Test
  fun testRefreshingActionsOnImport() = runBlocking {
    assertTrue(projectActions.isEmpty())

    val p1 = maven.createModulePom("p1", """
      <groupId>test</groupId>
      <artifactId>p1</artifactId>
      <version>1</version>
      """.trimIndent())

    val p2 = maven.createModulePom("p2", """
      <groupId>test</groupId>
      <artifactId>p2</artifactId>
      <version>1</version>
      """.trimIndent())
    maven.importProjectsAsync(p1, p2)

    assertEmptyKeymap()
  }

  @Test
  fun testRefreshingOnProjectRead() = runBlocking {

    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    assertEmptyKeymap()
    val goal = "clean"
    assignShortcut(maven.projectPom, goal, "alt shift X")

    // auto-import is turned on
    maven.waitForImportWithinTimeout {
      maven.updateProjectPom("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.apache.maven.plugins</groupId>
                          <artifactId>maven-surefire-plugin</artifactId>
                          <version>2.4.3</version>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())
    }

    assertKeymapContains(maven.projectPom, goal)
  }

  @Test
  fun testRefreshingOnPluginResolve() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    assertEmptyKeymap()

    val goal = "org.apache.maven.plugins:maven-surefire-plugin:2.4.3:test"
    assignShortcut(maven.projectPom, goal, "alt shift X")

    // auto-import is turned on
    maven.waitForImportWithinTimeout {
      maven.updateProjectPom("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.apache.maven.plugins</groupId>
                          <artifactId>maven-surefire-plugin</artifactId>
                          <version>2.4.3</version>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())
    }

    assertKeymapContains(maven.projectPom, goal)
  }

  @Test
  fun testActionWhenSeveralSimilarPlugins() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    // auto-import is turned on
    maven.waitForImportWithinTimeout {
      maven.updateProjectPom("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.apache.maven.plugins</groupId>
                          <artifactId>maven-surefire-plugin</artifactId>
                          <version>2.4.3</version>
                        </plugin>
                        <plugin>
                          <groupId>org.apache.maven.plugins</groupId>
                          <artifactId>maven-surefire-plugin</artifactId>
                          <version>2.4.3</version>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())
    }
    val goal = "org.apache.maven.plugins:maven-surefire-plugin:2.4.3:test"
    assignShortcut(maven.projectPom, goal, "alt shift X")

    assertKeymapContains(maven.projectPom, goal)
  }

  @Test
  fun testRefreshingOnProjectAddition() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    val m = maven.createModulePom("module", """
      <groupId>test</groupId>
      <artifactId>module</artifactId>
      <version>1</version>
      """.trimIndent())

    val goal = "clean"
    assertKeymapDoesNotContain(m, goal)

    // auto-import is turned on
    maven.waitForImportWithinTimeout {
      maven.updateProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>module</module>
                       </modules>
                       """.trimIndent())
    }

    assertEmptyKeymap()
    assignShortcut(m, goal, "alt shift X")
    assertKeymapContains(m, goal)
  }

  @Test
  fun testDeletingActionOnProjectRemoval() = runBlocking {
    val p1 = maven.createModulePom("p1", """
      <groupId>test</groupId>
      <artifactId>p1</artifactId>
      <version>1</version>
      """.trimIndent())

    val p2 = maven.createModulePom("p2", """
      <groupId>test</groupId>
      <artifactId>p2</artifactId>
      <version>1</version>
      """.trimIndent())

    maven.importProjectsAsync(p1, p2)

    maven.assertModules("p1", "p2")

    assertEmptyKeymap()
    val goal = "clean"
    assignShortcut(p1, goal, "alt shift X")
    assignShortcut(p2, goal, "alt shift Y")

    assertKeymapContains(p1, goal)
    assertKeymapContains(p2, goal)

    WriteCommandAction.writeCommandAction(maven.project).run<IOException> { p1.delete(this) }

    maven.updateAllProjects()

    maven.assertModules("p2")

    assertKeymapDoesNotContain(p1, goal)
    assertKeymapContains(p2, goal)
  }

  @Test
  fun testRefreshingActionsOnChangingIgnoreFlag() = runBlocking {
    val p1 = maven.createModulePom("p1", """
      <groupId>test</groupId>
      <artifactId>p1</artifactId>
      <version>1</version>
      """.trimIndent())

    val p2 = maven.createModulePom("p2", """
      <groupId>test</groupId>
      <artifactId>p2</artifactId>
      <version>1</version>
      """.trimIndent())
    maven.importProjectsAsync(p1, p2)

    assertEmptyKeymap()
    val goal = "clean"
    assignShortcut(p1, goal, "alt shift X")
    assignShortcut(p2, goal, "alt shift Y")

    assertKeymapContains(p1, goal)
    assertKeymapContains(p2, goal)

    maven.projectsManager.setIgnoredState(listOf(maven.projectsManager.findProject(p1)), true)


    assertKeymapDoesNotContain(p1, goal)
    assertKeymapContains(p2, goal)

    maven.projectsManager.setIgnoredState(listOf(maven.projectsManager.findProject(p1)), false)

    assertKeymapContains(p1, goal)
    assertKeymapContains(p2, goal)
  }

  private fun assertKeymapContains(pomFile: VirtualFile, goal: String) {
    val id = myShortcutsManager!!.getActionId(pomFile.getPath(), goal)
    assertContain(projectActions, id)
  }

  private fun assertEmptyKeymap() {
    assertEmpty(projectActions)
  }

  private fun assertKeymapDoesNotContain(pomFile: VirtualFile, goal: String) {
    val id = myShortcutsManager!!.getActionId(pomFile.getPath(), goal)
    assertDoNotContain(projectActions, id)
  }

  private fun assignShortcut(pomFile: VirtualFile, goal: String, shortcut: String) {
    val mavenProject = maven.projectsManager.findProject(pomFile)!!
    val actionId = myShortcutsManager!!.getActionId(mavenProject.path, goal)
    val action = ActionManager.getInstance().getAction(actionId)
    if (action == null) {
      MavenKeymapExtension.getOrRegisterAction(mavenProject, actionId, goal)
    }
    val activeKeymap = KeymapManager.getInstance().getActiveKeymap()
    activeKeymap.addShortcut(actionId, KeyboardShortcut.fromString(shortcut))
  }

  private val projectActions: List<String?>
    get() {
      val prefix = MavenKeymapExtension.getActionPrefix(maven.project, null)
      return ActionManager.getInstance().getActionIdList(prefix!!)
    }
}
