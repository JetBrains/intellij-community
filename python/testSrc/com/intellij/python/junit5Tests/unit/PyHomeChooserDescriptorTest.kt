// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.sdk.PythonSdkType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
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

    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(wrapper)
    assertNotNull(file)

    val descriptor = PythonSdkType.getInstance().homeChooserDescriptor
    assertEquals(file, descriptor.getFileToSelect(file!!), "A wrapper script must be selectable as a Python interpreter")
  }
}
