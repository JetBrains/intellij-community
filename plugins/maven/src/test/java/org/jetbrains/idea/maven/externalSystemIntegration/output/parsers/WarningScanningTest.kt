// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.externalSystemIntegration.output.parsers

import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenBuildToolLogTester
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenBuildToolLogTestUtils.WarningEventMatcher
import org.junit.jupiter.api.Test

@TestApplication
@RunInEdt
class WarningScanningTest {
  companion object {
    private val tempDir = tempPathFixture()
    private val project = projectFixture(tempDir, openAfterCreation = false)
  }

  private fun testCase(vararg lines: String): MavenBuildToolLogTester {
    return MavenBuildToolLogTester.forProject(project.get()).withLines(*lines)
  }

  @Test
  fun testWarningNotify() {
    val message = "The POM for some.maven:artifact:jar:1.2 is missing, no dependency information available"
    testCase(
      "[INFO] --------------------------------[ jar ]---------------------------------",
      "[WARNING] $message")
      .withParsers(WarningNotifier())
      .expect(message, WarningEventMatcher(message))
      .withSkippedOutput()
      .check()
  }

  @Test
  fun testWarningConcatenate() {
    val message = """
      Some problems were encountered while building the effective model for org.jb:m1-pom:jar:1
      'build.plugins.plugin.version' for org.apache.maven.plugins:maven-compiler-plugin is missing. @ line 30, column 21
      'build.plugins.plugin.version' for org.apache.maven.plugins:maven-surefire-plugin is missing. @ line 23, column 21
      It is highly recommended to fix these problems because they threaten the stability of your build.
      For this reason, future Maven versions might no longer support building such malformed projects""".trimIndent()
    testCase(
      "[INFO] --------------------------------[ jar ]---------------------------------",
      "[WARNING] ",
      "[WARNING] Some problems were encountered while building the effective model for org.jb:m1-pom:jar:1",
      "[WARNING] 'build.plugins.plugin.version' for org.apache.maven.plugins:maven-compiler-plugin is missing. @ line 30, column 21",
      "[WARNING] 'build.plugins.plugin.version' for org.apache.maven.plugins:maven-surefire-plugin is missing. @ line 23, column 21",
      "[WARNING] ",
      "[WARNING] It is highly recommended to fix these problems because they threaten the stability of your build.",
      "[WARNING] ",
      "[WARNING] For this reason, future Maven versions might no longer support building such malformed projects")
      .withParsers(WarningNotifier())
      .expect(message, WarningEventMatcher(message))
      .withSkippedOutput()
      .check()
  }
}
