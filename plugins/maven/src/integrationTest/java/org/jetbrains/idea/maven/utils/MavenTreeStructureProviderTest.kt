// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.projectView.TestProjectTreeStructure
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import javax.swing.JTree

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenTreeStructureProviderTest(mavenVersion: String, modelVersion: String) {
  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion,
  )

  @BeforeEach
  fun setUp() = runBlocking {
    edtWriteAction { ProjectRootManager.getInstance(maven.project).projectSdk = null }
  }

  @Test
  fun testShouldCreateSpecialNode() = runBlocking {
    maven.createProjectPom("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      <modules>
        <module>m1</module>
      </modules>""".trimIndent())

    maven.createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>""".trimIndent())

    maven.importProjectAsync()

    val actual = withTestStructure { structure ->
      val projectTree = structure.createPane().tree
      expand(projectTree)
      PlatformTestUtil.print(projectTree)
    }
    assertEquals("""
      -Project
       -PsiDirectory: project
        -PsiDirectory: m1
         -MavenPomFileNode:pom.xml
        -MavenPomFileNode:pom.xml
        settings.xml
       External Libraries""".trimIndent(), actual)
  }

  @Test
  fun testShouldMarkNodeAsIgnored() = runBlocking {
    maven.createProjectPom("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      <modules>
        <module>m1</module>
      </modules>""".trimIndent())

    val modulePom = maven.createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>""".trimIndent())

    maven.importProjectAsync()

    maven.projectsManager.setIgnoredState(listOf(maven.projectsManager.findProject(modulePom)), true)
    val actual = withTestStructure { structure ->
      val projectTree = structure.createPane().tree
      expand(projectTree)
      PlatformTestUtil.print(projectTree)
    }
    assertEquals("""
      -Project
       -PsiDirectory: project
        -PsiDirectory: m1
         -MavenPomFileNode:pom.xml (ignored)
        -MavenPomFileNode:pom.xml
        settings.xml
       External Libraries""".trimIndent(), actual)
  }


  private fun expand(tree: JTree) {
    PlatformTestUtil.waitForPromise(TreeUtil.promiseExpand(tree) {
      TreeVisitor.Action.CONTINUE
    })
  }

  /**
   * Creates a [TestProjectTreeStructure] on the EDT, runs [block] on it, then disposes it on the EDT.
   * The structure registers an [com.intellij.ui.tree.AsyncTreeModel] whose `dispose` asserts EDT, so its lifecycle
   * must not piggy-back on the background-thread fixture tear-down.
   */
  private suspend fun withTestStructure(block: (TestProjectTreeStructure) -> String): String = withContext(Dispatchers.EDT) {
    val parent = Disposer.newDisposable("MavenTreeStructureProviderTest")
    val structure = TestProjectTreeStructure(maven.project, parent)
    try {
      block(structure)
    }
    finally {
      Disposer.dispose(parent)
    }
  }
}
