// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.UsefulTestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.tasks.MavenKeymapExtension
import org.jetbrains.idea.maven.tasks.MavenShortcutsManager
import org.junit.Test
import java.io.IOException

class MavenShortcutsManagerTest : MavenMultiVersionImportingTestCase() {
  
  private var myShortcutsManager: MavenShortcutsManager? = null


  override fun setUp() {
    super.setUp()
    myShortcutsManager = MavenShortcutsManager.getInstance(project)
    myShortcutsManager!!.doInit(project)
    initProjectsManager(true)
  }

  public override fun tearDown() {
    try {
      MavenKeymapExtension.clearActions(project)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      myShortcutsManager = null
      super.tearDown()
    }
  }

  @Test
  fun testRefreshingActionsOnImport() = runBlocking {
    assertTrue(projectActions.isEmpty())

    val p1 = createModulePom("p1", """
      <groupId>test</groupId>
      <artifactId>p1</artifactId>
      <version>1</version>
      """.trimIndent())

    val p2 = createModulePom("p2", """
      <groupId>test</groupId>
      <artifactId>p2</artifactId>
      <version>1</version>
      """.trimIndent())
    importProjects(p1, p2)

    assertEmptyKeymap()
  }

  @Test
  fun testRefreshingOnProjectRead() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    assertEmptyKeymap()
    val goal = "clean"
    assignShortcut(projectPom, goal, "alt shift X")

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.apache.maven.plugins</groupId>
                          <artifactId>maven-surefire-plugin</artifactId>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())

    assertKeymapContains(projectPom, goal)
  }

  @Test
  fun testRefreshingOnPluginResolve() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    assertEmptyKeymap()

    val goal = "org.apache.maven.plugins:maven-surefire-plugin:2.4.3:test"
    assignShortcut(projectPom, goal, "alt shift X")

    importProjectAsync("""
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

    assertKeymapContains(projectPom, goal)
  }

  @Test
  fun testActionWhenSeveralSimilarPlugins() = runBlocking {
    needFixForMaven4()
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    importProjectAsync("""
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
    val goal = "org.apache.maven.plugins:maven-surefire-plugin:2.4.3:test"
    assignShortcut(projectPom, goal, "alt shift X")

    assertKeymapContains(projectPom, goal)
  }

  @Test
  fun testRefreshingOnProjectAddition() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())

    val m = createModulePom("module", """
      <groupId>test</groupId>
      <artifactId>module</artifactId>
      <version>1</version>
      """.trimIndent())

    val goal = "clean"
    assertKeymapDoesNotContain(m, goal)

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                         <module>module</module>
                       </modules>
                       """.trimIndent())

    importProjectAsync()

    assertEmptyKeymap()
    assignShortcut(m, goal, "alt shift X")
    assertKeymapContains(m, goal)
  }

  @Test
  fun testDeletingActionOnProjectRemoval() = runBlocking {
    val p1 = createModulePom("p1", """
      <groupId>test</groupId>
      <artifactId>p1</artifactId>
      <version>1</version>
      """.trimIndent())

    val p2 = createModulePom("p2", """
      <groupId>test</groupId>
      <artifactId>p2</artifactId>
      <version>1</version>
      """.trimIndent())

    importProjects(p1, p2)

    assertModules("p1", "p2")

    assertEmptyKeymap()
    val goal = "clean"
    assignShortcut(p1, goal, "alt shift X")
    assignShortcut(p2, goal, "alt shift Y")

    assertKeymapContains(p1, goal)
    assertKeymapContains(p2, goal)

    WriteCommandAction.writeCommandAction(project).run<IOException> { p1.delete(this) }

    importProjects(p1, p2)

    assertModules("p2")

    assertKeymapDoesNotContain(p1, goal)
    assertKeymapContains(p2, goal)
  }

  @Test
  fun testRefreshingActionsOnChangingIgnoreFlag() = runBlocking {
    val p1 = createModulePom("p1", """
      <groupId>test</groupId>
      <artifactId>p1</artifactId>
      <version>1</version>
      """.trimIndent())

    val p2 = createModulePom("p2", """
      <groupId>test</groupId>
      <artifactId>p2</artifactId>
      <version>1</version>
      """.trimIndent())
    importProjects(p1, p2)

    assertEmptyKeymap()
    val goal = "clean"
    assignShortcut(p1, goal, "alt shift X")
    assignShortcut(p2, goal, "alt shift Y")

    assertKeymapContains(p1, goal)
    assertKeymapContains(p2, goal)

    projectsManager.setIgnoredState(listOf(projectsManager.findProject(p1)), true)


    assertKeymapDoesNotContain(p1, goal)
    assertKeymapContains(p2, goal)

    projectsManager.setIgnoredState(listOf(projectsManager.findProject(p1)), false)

    assertKeymapContains(p1, goal)
    assertKeymapContains(p2, goal)
  }

  private fun assertKeymapContains(pomFile: VirtualFile, goal: String) {
    val id = myShortcutsManager!!.getActionId(pomFile.getPath(), goal)
    assertContain(projectActions, id)
  }

  private fun assertEmptyKeymap() {
    UsefulTestCase.assertEmpty(projectActions)
  }

  private fun assertKeymapDoesNotContain(pomFile: VirtualFile, goal: String) {
    val id = myShortcutsManager!!.getActionId(pomFile.getPath(), goal)
    assertDoNotContain(projectActions, id)
  }

  private fun assignShortcut(pomFile: VirtualFile, goal: String, shortcut: String) {
    val mavenProject = projectsManager.findProject(pomFile)!!
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
      val prefix = MavenKeymapExtension.getActionPrefix(project, null)
      return ActionManager.getInstance().getActionIdList(prefix!!)
    }
}
