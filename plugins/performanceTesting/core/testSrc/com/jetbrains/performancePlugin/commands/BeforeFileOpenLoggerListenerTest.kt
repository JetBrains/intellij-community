// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
internal class BeforeFileOpenLoggerListenerTest {
  private val project = projectFixture()

  /** Only the first file counts, and another test of this JVM can open a file before this test. */
  @BeforeEach
  fun forgetFileOfEarlierTest() {
    reopenedFile.value = null
  }

  @AfterEach
  fun forgetFileOfThisTest() {
    reopenedFile.value = null
  }

  @Test
  fun `the first opened file stays as its url, not as the file`() {
    val file = FirstOpenedFile()
    BeforeFileOpenLoggerListener().beforeFileOpened(FileEditorManager.getInstance(project.get()), file)

    assertEquals(file.url, reopenedFile.value?.url)
    LeakHunter.checkLeak(reopenedFile, FirstOpenedFile::class.java)
  }

  @Test
  fun `a later opened file does not replace the first one`() {
    val first = FirstOpenedFile()
    val listener = BeforeFileOpenLoggerListener()
    val fileEditorManager = FileEditorManager.getInstance(project.get())
    listener.beforeFileOpened(fileEditorManager, first)
    listener.beforeFileOpened(fileEditorManager, LightVirtualFile("second.txt"))

    assertEquals(first.url, reopenedFile.value?.url)
  }

  private class FirstOpenedFile : LightVirtualFile("first.txt")
}
