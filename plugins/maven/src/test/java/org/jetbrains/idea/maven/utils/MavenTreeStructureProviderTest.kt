// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils

import com.intellij.openapi.util.Disposer
import com.intellij.projectView.TestProjectTreeStructure
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.ui.tree.TreeUtil
import junit.framework.TestCase
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import org.junit.Test
import javax.swing.JTree

class MavenTreeStructureProviderTest : MavenMultiVersionImportingTestCase() {
  private lateinit var myStructure: TestProjectTreeStructure

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    myStructure = TestProjectTreeStructure(myProject, testRootDisposable)
  }

  @Throws(java.lang.Exception::class)
  override fun tearDown() {
    try {
      Disposer.dispose(myStructure)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  @Test
  fun testShouldCreateSpecialNode() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +
                     "<modules>" +
                     "  <module>m1</module>" +
                     "</modules>");

    createModulePom("m1", "<groupId>test</groupId>" +
                          "<artifactId>m1</artifactId>" +
                          "<version>1</version>" +
                          "<parent>" +
                          "  <groupId>test</groupId>" +
                          "  <artifactId>project</artifactId>" +
                          "  <version>1</version>" +
                          "</parent>")

    importProject()

    val projectTree = myStructure.createPane().tree
    expand(projectTree)
    var actual = PlatformTestUtil.print(projectTree)
    TestCase.assertEquals("-Project\n" +
                          " -PsiDirectory: project\n" +
                          "  -PsiDirectory: m1\n" +
                          "   -MavenPomFileNode:pom.xml\n" +
                          "  -MavenPomFileNode:pom.xml\n" +
                          " External Libraries", actual)

  }

  @Test fun testShouldMarkNodeAsIgnored() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>pom</packaging>" +
                     "<modules>" +
                     "  <module>m1</module>" +
                     "</modules>");

    val modulePom = createModulePom("m1", "<groupId>test</groupId>" +
                                          "<artifactId>m1</artifactId>" +
                                          "<version>1</version>" +
                                          "<parent>" +
                                          "  <groupId>test</groupId>" +
                                          "  <artifactId>project</artifactId>" +
                                          "  <version>1</version>" +
                                          "</parent>")

    importProject()

    myProjectsManager.setIgnoredState(listOf(myProjectsManager.findProject (modulePom)), true)
    val projectTree = myStructure.createPane().tree
    expand(projectTree)
    val actual = PlatformTestUtil.print(projectTree)
    TestCase.assertEquals("-Project\n" +
                          " -PsiDirectory: project\n" +
                          "  -PsiDirectory: m1\n" +
                          "   -MavenPomFileNode:pom.xml (ignored)\n" +
                          "  -MavenPomFileNode:pom.xml\n" +
                          " External Libraries", actual)

  }


  private fun expand(tree: JTree) {
    PlatformTestUtil.waitForPromise(TreeUtil.promiseExpand(tree) {
      TreeVisitor.Action.CONTINUE
    })
  }
}
