// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.externalSystemIntegration.output.parsers

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenBuildToolLogTester
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenBuildToolLogTestUtils.FileEventMatcher
import org.junit.jupiter.api.Test

@TestApplication
@RunInEdt
class TestBuildErrorNotification {
  companion object {
    private val tempDir = tempPathFixture()
    private val project = projectFixture(tempDir, openAfterCreation = false)
  }

  private fun testCase(vararg lines: String): MavenBuildToolLogTester {
    return MavenBuildToolLogTester.forProject(project.get()).withLines(*lines)
  }

  @Test
  fun testParseJavaError() {
    val expectedFileName = FileUtil.toSystemDependentName("C:/path/to/MyFile.java")
    val expectedMessage = "';' expected"
    testCase("""
      [INFO] -------------------------------------------------------------
      [ERROR] /C:/path/to/MyFile.java:[13,21] ';' expected
      [INFO] 1 error""".trimIndent())
      .withParsers(JavaBuildErrorNotification())
      .expect(expectedMessage, FileEventMatcher(expectedMessage, expectedFileName, 12, 20))
      .withSkippedOutput()
      .check()
  }

  @Test
  fun testParseKotlinError() {
    val expectedFileName = FileUtil.toSystemDependentName("C:\\path\\to\\MyFile.kt")
    val expectedMessage = "Data class primary constructor must have only property (val / var) parameters"
    testCase("""
      [INFO] --- kotlin-maven-plugin:1.3.21:compile (compile) @ test-11 ---
      [ERROR] C:\path\to\MyFile.kt: (3, 16) Data class primary constructor must have only property (val / var) parameters
      [INFO] ------------------------------------------------------------------------""".trimIndent())
      .withParsers(KotlinBuildErrorNotification())
      .expect(expectedMessage, FileEventMatcher(expectedMessage, expectedFileName, 2, 15))
      .withSkippedOutput()
      .check()
  }

  @Test
  fun testParseJavaCheckstyle() {
    val expectedFileName = FileUtil.toSystemDependentName("C:\\path\\to\\MyFile.java")
    val expectedMessage = "Line matches the illegal pattern 'System\\.(out|err).*?${'$'}'. [RegexpSinglelineJava]"
    testCase("""
      [INFO] Starting audit...
      [ERROR] C:\path\to\MyFile.java:9: Line matches the illegal pattern 'System\.(out|err).*?${'$'}'. [RegexpSinglelineJava]
      Audit done.""".trimIndent())
      .withParsers(JavaBuildErrorNotification())
      .expect(expectedMessage, FileEventMatcher(expectedMessage, expectedFileName, 8, 0))
      .withSkippedOutput()
      .check()
  }
}
