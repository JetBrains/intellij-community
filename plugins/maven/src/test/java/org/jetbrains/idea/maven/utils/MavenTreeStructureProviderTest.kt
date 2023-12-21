// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.projectView.TestProjectTreeStructure
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.ui.tree.TreeUtil
import junit.framework.TestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test
import javax.swing.JTree

class MavenTreeStructureProviderTest : MavenMultiVersionImportingTestCase() {
  private lateinit var myStructure: TestProjectTreeStructure

  override fun setUp() {
    super.setUp()
    myStructure = TestProjectTreeStructure(project, testRootDisposable)
  }

  override fun tearDown() = runBlocking {
    try {
      withContext(Dispatchers.EDT) {
        Disposer.dispose(myStructure)
      }
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  @Test
  fun testShouldCreateSpecialNode() = runBlocking {
    createProjectPom("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      <modules>
        <module>m1</module>
      </modules>""".trimIndent())

    createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>""".trimIndent())

    importProjectAsync()

    val actual = withContext(Dispatchers.EDT) {
      val projectTree = myStructure.createPane().tree
      expand(projectTree)
      PlatformTestUtil.print(projectTree)
    }
    TestCase.assertEquals("""
      -Project
       -PsiDirectory: project
        -PsiDirectory: m1
         -MavenPomFileNode:pom.xml
        -MavenPomFileNode:pom.xml
       External Libraries""".trimIndent(), actual)
  }

  @Test fun testShouldMarkNodeAsIgnored() = runBlocking {
    createProjectPom("""
      <groupId>test</groupId>
      <artifactId>project</artifactId>
      <version>1</version>
      <packaging>pom</packaging>
      <modules>
        <module>m1</module>
      </modules>""".trimIndent())

    val modulePom = createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>""".trimIndent())

    importProjectAsync()

    projectsManager.setIgnoredState(listOf(projectsManager.findProject(modulePom)), true)
    val actual = withContext(Dispatchers.EDT) {
      val projectTree = myStructure.createPane().tree
      expand(projectTree)
      PlatformTestUtil.print(projectTree)
    }
    TestCase.assertEquals("""
      -Project
       -PsiDirectory: project
        -PsiDirectory: m1
         -MavenPomFileNode:pom.xml (ignored)
        -MavenPomFileNode:pom.xml
       External Libraries""".trimIndent(), actual)
  }


  private fun expand(tree: JTree) {
    PlatformTestUtil.waitForPromise(TreeUtil.promiseExpand(tree) {
      TreeVisitor.Action.CONTINUE
    })
  }
}
