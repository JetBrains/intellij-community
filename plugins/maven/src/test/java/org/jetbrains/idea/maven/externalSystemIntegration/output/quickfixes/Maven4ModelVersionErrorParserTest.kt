// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes

import com.intellij.build.events.MessageEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.util.SystemInfo
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.buildtool.MavenBuildIssueHandler
import org.jetbrains.idea.maven.model.MavenProjectProblem
import org.junit.Assume
import org.junit.Test
import java.nio.file.Path
import kotlin.io.path.pathString

class Maven4ModelVersionErrorParserTest : MavenMultiVersionImportingTestCase() {

  @Test
  fun testShouldCreateBuildIssue() = runBlocking {
    assumeModel_4_0_0("test is applicable for model 4.0 only")
    Assume.assumeTrue("hard to mock the path check on windows, we test only strings", SystemInfo.isUnix)
    createProjectPom("""
      <groupId>test</groupId>
      <artifactId>test</artifactId>
      <version>1.0.0</version>
      
""")
    val issues = testString("[ERROR] Maven model problem: 'subprojects' unexpected subprojects element at ${projectPom.path}:-1:-1",
                            TRIGGER_LINES_UNIX) {
      it.pathString == projectPom.path
    }
    assertEquals(1, issues.size)
    assertEquals(MessageEvent.Kind.ERROR, issues[0].second)
  }

  @Test
  fun testShouldCreateBuildIssueForProblem() = runBlocking {
    assumeModel_4_0_0("test is applicable for model 4.0 only")
    createProjectPom("""
      <groupId>test</groupId>
      <artifactId>test</artifactId>
      <version>1.0.0</version>
""")
    val issues = ArrayList<Pair<BuildIssue, MessageEvent.Kind>>()

    Maven4ModelVersionErrorParser(
      {
        object : MavenBuildIssueHandler {
          override fun addBuildIssue(issue: BuildIssue, kind: MessageEvent.Kind) {
            issues.add(issue to kind)
          }
        }
      }, { true }, listOf())
      .processProjectProblem(project,
                             MavenProjectProblem.createStructureProblem("/path/to/file:-1:-1",
                                                                        "'subprojects' unexpected subprojects element",
                                                                        false))
    assertEquals(1, issues.size)
    assertEquals(MessageEvent.Kind.ERROR, issues[0].second)
  }


  @Test
  fun testShouldCreateBuildIssueForWindowsPath() = runBlocking {
    assumeModel_4_0_0("test is applicable for model 4.0 only")
    Assume.assumeTrue("hard to mock the path check on windows, we test only strings", SystemInfo.isUnix)
    testString("[ERROR] Maven model problem: 'subprojects' unexpected subprojects element at C:\\Users\\User.Name\\IdeaProjects\\spring-petclinic\\pom.xml:-1:-1",
               TRIGGER_LINES_WINDOWS) {
      it.pathString == "C:\\Users\\User.Name\\IdeaProjects\\spring-petclinic\\pom.xml"
    }
    Unit
  }


  private suspend fun testString(
    message: String,
    triggerLines: List<Regex>,
    pathChecker: (Path) -> Boolean,
  ): ArrayList<Pair<BuildIssue, MessageEvent.Kind>> {
    val issues = ArrayList<Pair<BuildIssue, MessageEvent.Kind>>()

    importProjectAsync("""
      <groupId>com.example</groupId>
      <artifactId>my-project</artifactId>
      <version>1.0.0</version>
    """)

    Maven4ModelVersionErrorParser(
      {
        object : MavenBuildIssueHandler {
          override fun addBuildIssue(issue: BuildIssue, kind: MessageEvent.Kind) {
            issues.add(issue to kind)
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
    assertTrue("No quickfixes available", issues[0].first.quickFixes.isNotEmpty())
    return issues
  }
}