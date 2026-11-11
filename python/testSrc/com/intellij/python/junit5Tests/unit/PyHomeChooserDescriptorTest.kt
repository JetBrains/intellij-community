// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.PythonSdkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Regression coverage for [PY-89236](https://youtrack.jetbrains.com/issue/PY-89236).
 *
 * A wrapper script (e.g. `python.bat`) or any other executable must be selectable as a Python interpreter.
 * The home chooser descriptor's [com.intellij.openapi.fileChooser.FileChooserDescriptor.getFileToSelect] returning
 * `null` is what makes the file chooser report the selected file as missing ("The IDE was unable to locate the
 * following files"), so the descriptor must resolve a plain file to itself rather than restricting selection to
 * `python*`-named binaries.
 */
@TestApplication
class PyHomeChooserDescriptorTest {

  @Test
  fun `wrapper script is selectable as interpreter`(@TempDir tempDir: Path) {
    val wrapper = tempDir.resolve("python.bat")
    wrapper.writeText("@echo off\npython %*\n")

    val file = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(wrapper)
    assertNotNull(file)

    val descriptor = PythonSdkType.getInstance().homeChooserDescriptor
    assertEquals(file, descriptor.getFileToSelect(file!!), "A wrapper script must be selectable as a Python interpreter")
  }

  /** [PY-93918](https://youtrack.jetbrains.com/issue/PY-93918): the universal file chooser validates on a background thread. */
  @Test
  fun `the validation runs on a background thread`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    val notPython = tempDir.resolve("notes.txt")
    notPython.writeText("not a Python interpreter")
    val file = checkNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(notPython))

    val descriptor = PythonSdkType.getInstance().homeChooserDescriptor
    val error = withContext(Dispatchers.IO) {
      assertThrows<Exception> { descriptor.validateSelectedFiles(arrayOf(file)) }
    }
    assertEquals(PyBundle.message("python.sdk.error.invalid.interpreter.selected", file.name), error.message)
  }

  /**
   * PY-93918: a chooser dropped a directory that the descriptor did not select, and then passed an empty selection on.
   * So a directory without a Python binary is selectable, and the validation rejects it with a message.
   */
  @Test
  fun `a directory without Python is selectable and rejected by the validation`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    val dir = checkNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(tempDir.resolve("empty").createDirectories()))

    val descriptor = PythonSdkType.getInstance().homeChooserDescriptor
    assertEquals(dir, descriptor.getFileToSelect(dir), "A directory must be selectable")
    val error = withContext(Dispatchers.IO) {
      assertThrows<Exception> { descriptor.validateSelectedFiles(arrayOf(dir)) }
    }
    assertEquals(PyBundle.message("python.sdk.error.invalid.venv.selected", dir.name), error.message)
  }
}
