// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.hyperlinks

import com.intellij.openapi.project.Project
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.terminal.backend.hyperlinks.TerminalHyperlinkFilterContextImpl
import com.intellij.terminal.backend.hyperlinks.filter.TerminalGenericFileFilter
import com.intellij.terminal.backend.hyperlinks.filter.TerminalNioFileLookup
import com.intellij.testFramework.junit5.fixture.TestFixtures
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.hyperlinks.TerminalFileHyperlinkInfo
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createFile

/**
 * Runs the file path filter against the file system of the host, as the hover mode
 * does. The other filter tests use a fake file tree.
 */
@TestFixtures
internal class TerminalGenericFileFilterNioLookupTest {

  private val tempDir: Path by tempPathFixture()

  private val project = mock(Project::class.java)

  @Test
  fun `trailing dot after an existing file`() {
    val absolutePath = tempDir.resolve("a.txt").createFile().absolutePathString()
    val line = "see $absolutePath."
    val filter = TerminalGenericFileFilter(project, LocalEelDescriptor, null, TerminalNioFileLookup())
    val item = filter.applyFilter(line, line.length)!!.resultItems.single()
    assertThat(line.substring(item.highlightStartOffset, item.highlightEndOffset)).isEqualTo(absolutePath)
    assertThat((item.hyperlinkInfo as TerminalFileHyperlinkInfo).path).isEqualTo(EelPath.parse(absolutePath, LocalEelDescriptor))
  }

  @Test
  fun `trailing dots after an existing relative file are not a part of the link`() {
    val file = EelPath.parse(tempDir.resolve("a.txt").createFile().toString(), LocalEelDescriptor)
    val workingDirectory = tempDir.absolutePathString()
    val context = TerminalHyperlinkFilterContextImpl(LocalEelDescriptor, EelPath.parse(workingDirectory, LocalEelDescriptor))
    context.updateCurrentDirectory(workingDirectory)
    val filter = TerminalGenericFileFilter(project, LocalEelDescriptor, context, TerminalNioFileLookup())
    for ((line, expectedPaths) in listOf("Compiling a.txt..." to listOf(file), "see a.txt.:10" to emptyList())) {
      val linkedPaths = filter.applyFilter(line, line.length)?.resultItems.orEmpty().map {
        (it.hyperlinkInfo as TerminalFileHyperlinkInfo).path
      }
      assertThat(linkedPaths).`as`(line).isEqualTo(expectedPaths)
    }
  }
}
