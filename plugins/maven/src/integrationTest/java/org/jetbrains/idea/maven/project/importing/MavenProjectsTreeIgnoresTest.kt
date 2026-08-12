// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.importing

import com.intellij.openapi.util.text.StringUtil
import kotlinx.coroutines.runBlocking
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.testRootDisposable
import org.jetbrains.idea.maven.fixtures.tree
import org.jetbrains.idea.maven.fixtures.updateAll
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsTree
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.api.BeforeEach

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenProjectsTreeIgnoresTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  private var myLog = ""
  private var myRoots: List<MavenProject>? = null

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    maven.project.messageBus.connect(maven.testRootDisposable).subscribe(MavenProjectsTree.Listener.TOPIC, MyLoggingListener())
    val m1 = maven.createModulePom("m1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    val m2 = maven.createModulePom("m2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m2</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    maven.updateAll(m1, m2)
    myRoots = maven.tree.rootProjects
  }

  @Test
  fun testSendingNotifications() = runBlocking {
    maven.tree.setIgnoredState(listOf(myRoots!![0]), true)
    assertEquals("ignored: m1 ", myLog)
    myLog = ""
    maven.tree.ignoredFilesPaths = listOf(myRoots!![1].path)
    assertEquals("ignored: m2 unignored: m1 ", myLog)
    myLog = ""
    maven.tree.ignoredFilesPatterns = listOf("*")
    assertEquals("ignored: m1 ", myLog)
    myLog = ""
    maven.tree.ignoredFilesPatterns = emptyList()
    assertEquals("unignored: m1 ", myLog)
    myLog = ""
  }

  @Test
  fun testDoNotSendNotificationsIfNothingChanged() = runBlocking {
    maven.tree.setIgnoredState(listOf(myRoots!![0]), true)
    assertEquals("ignored: m1 ", myLog)
    myLog = ""
    maven.tree.setIgnoredState(listOf(myRoots!![0]), true)
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
