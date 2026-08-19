// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes

import com.intellij.build.events.MessageEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assumeModel_4_0_0
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.buildtool.MavenBuildIssueHandler
import org.jetbrains.idea.maven.model.MavenProjectProblem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import java.nio.file.Path
import kotlin.io.path.absolute

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class Maven4ModelVersionErrorParserTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  

  @Test
  fun testShouldCreateBuildIssue() = runBlocking {
    maven.assumeModel_4_0_0("test is applicable for model 4.0 only")
    val pomFilePath = maven.createProjectPom("""
      <groupId>test</groupId>
      <artifactId>test</artifactId>
      <version>1.0.0</version>
      
""").toNioPath()
    val issues = testString("[ERROR] Maven model problem: 'subprojects' unexpected subprojects element at $pomFilePath:-1:-1",
                            if (SystemInfo.isWindows) TRIGGER_LINES_WINDOWS else TRIGGER_LINES_UNIX) {
      it == pomFilePath
    }
    assertEquals(1, issues.size)
    assertEquals(MessageEvent.Kind.ERROR, issues[0].second)
  }

  @Test
  fun testShouldCreateBuildIssueForProblem() = runBlocking {
    maven.assumeModel_4_0_0("test is applicable for model 4.0 only")
    maven.createProjectPom("""
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
      .processProjectProblem(maven.project,
                             MavenProjectProblem.createStructureProblem("${Path.of("path", "to", "file").absolute()}:-1:-1",
                                                                        "'subprojects' unexpected subprojects element",
                                                                        false))
    assertEquals(1, issues.size)
    assertEquals(MessageEvent.Kind.ERROR, issues[0].second)
  }

  private suspend fun testString(
    message: String,
    triggerLines: List<Regex>,
    pathChecker: (Path) -> Boolean,
  ): ArrayList<Pair<BuildIssue, MessageEvent.Kind>> {
    val issues = ArrayList<Pair<BuildIssue, MessageEvent.Kind>>()

    maven.importProjectAsync("""
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
      maven.project,
      message,
      null,
    ) {}

    assertEquals(1, issues.size, "Expected 1 event, ")
    assertNotNull(issues[0], "No message error events received")
    assertTrue(issues[0].first.quickFixes.isNotEmpty(), "No quickfixes available")
    return issues
  }
}