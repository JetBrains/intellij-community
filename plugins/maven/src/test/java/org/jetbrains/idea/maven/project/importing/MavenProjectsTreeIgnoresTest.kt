// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.importing

import com.intellij.openapi.util.text.StringUtil
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsTree
import org.junit.Test

class MavenProjectsTreeIgnoresTest : MavenProjectsTreeTestCase() {
  private var myLog = ""
  private var myRoots: List<MavenProject>? = null

  override fun setUp() = runBlocking {
    super.setUp()
    tree.addListener(MyLoggingListener(), getTestRootDisposable())
    val m1 = createModulePom("m1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    val m2 = createModulePom("m2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m2</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    updateAll(m1, m2)
    myRoots = tree.rootProjects
  }

  @Test
  fun testSendingNotifications() = runBlocking {
    tree.setIgnoredState(listOf(myRoots!![0]), true)
    assertEquals("ignored: m1 ", myLog)
    myLog = ""
    tree.ignoredFilesPaths = listOf(myRoots!![1].path)
    assertEquals("ignored: m2 unignored: m1 ", myLog)
    myLog = ""
    tree.ignoredFilesPatterns = listOf("*")
    assertEquals("ignored: m1 ", myLog)
    myLog = ""
    tree.ignoredFilesPatterns = emptyList()
    assertEquals("unignored: m1 ", myLog)
    myLog = ""
  }

  @Test
  fun testDoNotSendNotificationsIfNothingChanged() = runBlocking {
    tree.setIgnoredState(listOf(myRoots!![0]), true)
    assertEquals("ignored: m1 ", myLog)
    myLog = ""
    tree.setIgnoredState(listOf(myRoots!![0]), true)
    assertEquals("", myLog)
  }

  private inner class MyLoggingListener : MavenProjectsTree.Listener {
    override fun projectsIgnoredStateChanged(ignored: List<MavenProject>, unignored: List<MavenProject>, fromImport: Boolean) {
      if (!ignored.isEmpty()) myLog += "ignored: " + format(ignored) + " "
      if (!unignored.isEmpty()) myLog += "unignored: " + format(unignored) + " "
      if (ignored.isEmpty() && unignored.isEmpty()) myLog += "empty "
    }

    private fun format(projects: List<MavenProject>): String {
      return StringUtil.join(projects, { project: MavenProject -> project.mavenId.artifactId }, ", ")
    }
  }
}
