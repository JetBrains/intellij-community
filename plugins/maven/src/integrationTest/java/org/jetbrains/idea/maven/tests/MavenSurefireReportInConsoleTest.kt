// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.tests

import com.intellij.execution.filters.Filter
import com.intellij.testFramework.javaCodeInsightFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.jetbrains.idea.maven.project.MavenTestConsoleFilter
import org.jetbrains.idea.maven.server.MavenServerManager
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
class MavenSurefireReportInConsoleTest {
  companion object {
    private val tempDir = tempPathFixture()
    private val project = projectFixture(tempDir, openAfterCreation = true)

    @Suppress("unused") // required by codeInsightFixture
    private val module by project.moduleFixture(tempDir, addPathToSourceRoot = true)

    @JvmStatic
    @AfterAll
    fun afterAll() {
      MavenServerManager.getInstance().closeAllConnectorsAndWait()
    }
  }

  private val fixture by javaCodeInsightFixture(project, tempDir)
  private val filter: Filter = MavenTestConsoleFilter()

  private fun passLine(line: String): List<String> {
    val l = if (line.endsWith("\n")) line else line + "\n"
    val result = filter.applyFilter(l, l.length) ?: return emptyList()
    return result.resultItems.map { l.substring(it.highlightStartOffset, it.highlightEndOffset) }
  }

  @Test
  fun testSurefire2_14() {
    fixture.addClass("""
      public class CccTest {
        public void testTtt() {}
        public void testTtt2() {}
      }""".trimIndent())

    val tempDirPath = fixture.tempDirPath

    assertEquals(emptyList<String>(), passLine("[INFO] Scanning for projects..."))
    assertEquals(listOf(tempDirPath), passLine("[INFO] Surefire report directory: $tempDirPath"))
    assertEquals(listOf(tempDirPath), passLine("[ERROR] Please refer to $tempDirPath for the individual test results."))
  }
}
