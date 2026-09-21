// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.hyperlinks

import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.hyperlinks.filter.TerminalFileKind
import org.jetbrains.plugins.terminal.hyperlinks.filter.TerminalNioFileLookup
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

internal class TerminalNioFileLookupTest {

  @Rule
  @JvmField
  val tempDir: TemporaryFolder = TemporaryFolder()

  private val lookup = TerminalNioFileLookup()

  private fun localPath(file: File): EelPath = EelPath.parse(file.absolutePath, LocalEelDescriptor)

  @Test
  fun `regular file`() {
    val file = tempDir.newFile("file.txt")
    assertThat(lookup.lookup(localPath(file))).isEqualTo(TerminalFileKind.FILE)
  }

  @Test
  fun `existing directory`() {
    val directory = tempDir.newFolder("dir")
    assertThat(lookup.lookup(localPath(directory))).isEqualTo(TerminalFileKind.DIRECTORY)
  }

  @Test
  fun `missing file`() {
    val missing = File(tempDir.root, "missing.txt")
    assertThat(lookup.lookup(localPath(missing))).isNull()
  }

  @Test
  fun `symbolic link is followed`() {
    Assume.assumeFalse("symbolic links need privileges on Windows", SystemInfo.isWindows)
    val target = tempDir.newFolder("target")
    val link = File(tempDir.root, "link")
    Files.createSymbolicLink(link.toPath(), target.toPath())
    assertThat(lookup.lookup(localPath(link))).isEqualTo(TerminalFileKind.DIRECTORY)
  }

  @Test
  fun `environment without a NIO file system has no files`() {
    val path = EelPath.parse("/some/file.txt", TestEelDescriptor.POSIX)
    assertThat(lookup.lookup(path)).isNull()
  }
}
