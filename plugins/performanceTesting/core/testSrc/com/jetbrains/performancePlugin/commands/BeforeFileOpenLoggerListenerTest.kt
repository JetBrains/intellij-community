// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.io.path.createFile

@TestApplication
internal class BeforeFileOpenLoggerListenerTest {
  private val project = projectFixture()
  private val tempDir = tempPathFixture()

  /**
   * Another test of this JVM can open a file before this test.
   */
  @BeforeEach
  fun forgetFileOfEarlierTest() {
    reopenedFileOpenedAt.value = null
  }

  @AfterEach
  fun forgetFileOfThisTest() {
    reopenedFileOpenedAt.value = null
    System.clearProperty(REOPENED_FILE_PROPERTY)
  }

  @Test
  fun `the opening of the file named by the property is recorded`() {
    val reopened = createFile("reopened.txt")
    System.setProperty(REOPENED_FILE_PROPERTY, reopened.path)
    open(reopened)

    assertNotNull(reopenedFileOpenedAt.value)
  }

  @Test
  fun `the opening of another file is not recorded`() {
    System.setProperty(REOPENED_FILE_PROPERTY, createFile("reopened.txt").path)
    open(createFile("other.txt"))

    assertNull(reopenedFileOpenedAt.value)
  }

  @Test
  fun `nothing is recorded without the property`() {
    open(createFile("reopened.txt"))

    assertNull(reopenedFileOpenedAt.value)
  }

  @Test
  fun `a later opening of the file keeps the moment of its first opening`() {
    val reopened = createFile("reopened.txt")
    System.setProperty(REOPENED_FILE_PROPERTY, reopened.path)
    open(reopened)
    val firstOpenedAt = reopenedFileOpenedAt.value
    open(reopened)

    assertEquals(firstOpenedAt, reopenedFileOpenedAt.value)
  }

  private fun createFile(name: String): VirtualFile {
    val path = tempDir.get().resolve(name).createFile()
    return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)!!
  }

  private fun open(file: VirtualFile) {
    BeforeFileOpenLoggerListener().beforeFileOpened(FileEditorManager.getInstance(project.get()), file)
  }
}
