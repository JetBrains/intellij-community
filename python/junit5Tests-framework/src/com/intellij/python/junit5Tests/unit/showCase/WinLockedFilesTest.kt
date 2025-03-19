package com.intellij.python.junit5Tests.unit.showCase

import com.intellij.python.junit5Tests.framework.winLockedFile.FileLockedException
import com.intellij.python.junit5Tests.framework.winLockedFile.deleteCheckLocking
import com.intellij.python.junit5Tests.framework.winLockedFile.getProcessLockedPath
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.writeText

class WinLockedFilesTest {

  @EnabledOnOs(value = [OS.WINDOWS])
  @Test
  fun testNotAFile(@TempDir path: Path) {
    assertNull(getProcessLockedPath(path.resolve("junk")).successOrNull, "Directory isn't a file")
  }

  @EnabledOnOs(value = [OS.WINDOWS])
  @Test
  fun testNoProcess(@TempDir path: Path) {
    val file = path.resolve("1.txt").also { it.writeText("hello") }
    assertThat("File is not locked, no process should be reported",
               getProcessLockedPath(file).orThrow(), CoreMatchers.`is`(Matchers.empty()))
  }

  @EnabledOnOs(value = [OS.WINDOWS])
  @Test
  fun testFileLocked(@TempDir path: Path) {
    val file = path.resolve("1.txt")
    Files.newOutputStream(file).use {
      val processes = getProcessLockedPath(file).orThrow()
      assertArrayEquals(arrayOf(ProcessHandle.current()), processes.toTypedArray(),
                        "No locked process info returned")
    }
  }

  @OptIn(ExperimentalPathApi::class)
  @EnabledOnOs(value = [OS.WINDOWS])
  @Test
  fun testDirectoryLocked(@TempDir path: Path) {
    val systemRoot = System.getenv("SystemRoot") ?: "c:\\windows"
    val process = ProcessBuilder("$systemRoot/system32/cmd.exe")
      .directory(path.toFile())
      .start()
    Assertions.assertThrows(FileLockedException::class.java) {
      deleteCheckLocking(path)
    }
    process.destroy()
    process.waitFor()
    deleteCheckLocking(path)
  }
}