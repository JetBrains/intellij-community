// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes

import com.intellij.build.events.MessageEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.util.SystemInfo
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.buildtool.MavenBuildIssueHandler
import org.junit.Assume
import org.junit.Test
import java.nio.file.Path
import kotlin.io.path.pathString

class Maven4ModelVersionErrorParserTest : MavenMultiVersionImportingTestCase() {

  @Test
  fun testShouldCreateBuildIssue() = runBlocking {
    assumeMaven4()
    Assume.assumeTrue(SystemInfo.isUnix)
    createProjectPom("""
      <groupId>test</groupId>
      <artifactId>test</artifactId>
      <version>1.0.0</version>
      
""")
    testString("[ERROR] Maven model problem: 'subprojects' unexpected subprojects element at ${projectPom.path}:-1:-1", TRIGGER_LINES_UNIX) {
      it.pathString == projectPom.path
    }
  }


  @Test
  fun testShouldCreateBuildIssueForWindowsPath() = runBlocking {
    Assume.assumeTrue(SystemInfo.isUnix)
    testString("[ERROR] Maven model problem: 'subprojects' unexpected subprojects element at C:\\Users\\User.Name\\IdeaProjects\\spring-petclinic\\pom.xml:-1:-1", TRIGGER_LINES_WINDOWS) {
      it.pathString == "C:\\Users\\User.Name\\IdeaProjects\\spring-petclinic\\pom.xml"
    }
  }


  private suspend fun testString(message: String, triggerLines: List<Regex>, pathChecker: (Path) -> Boolean) {
    assumeMaven4()
    val issues = ArrayList<BuildIssue>()

    importProjectAsync("""
      <groupId>com.example</groupId>
      <artifactId>my-project</artifactId>
      <version>1.0.0</version>
    """)

    Maven4ModelVersionErrorParser(
      {
        object : MavenBuildIssueHandler {
          override fun addBuildIssue(issue: BuildIssue, kind: MessageEvent.Kind) {
            issues.add(issue)
          }

        }
      },
      pathChecker,
      triggerLines
    ).processLogLine(
      project,
      message,
      null,
    ) {}

    assertEquals("Expected 1 event, ", 1, issues.size)
    assertNotNull("No message error events received", issues[0])
    assertTrue("No quickfixes available", issues[0].quickFixes.isNotEmpty())
  }
}