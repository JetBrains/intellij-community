// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.dnd

import com.intellij.ide.dnd.DnDEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.runtime.product.ProductMode
import com.intellij.terminal.frontend.dnd.TerminalDropData
import com.intellij.terminal.frontend.dnd.TerminalDroppedContentResolver
import com.intellij.terminal.frontend.toolwindow.impl.TerminalFilePathHandler
import com.intellij.terminal.frontend.toolwindow.impl.TerminalProcessContext
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.jetbrains.plugins.terminal.fus.TerminalInsertedContentType
import org.jetbrains.plugins.terminal.session.ShellName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile

/**
 * Tests how [TerminalDroppedContentResolver] resolves dropped content into terminal text and file paths,
 * and how it classifies the dropped content.
 */
@TestApplication
internal class TerminalDroppedContentResolverTest {
  private val projectFixture = projectFixture()
  private val tempDirFixture = tempPathFixture()

  private val project: Project get() = projectFixture.get()
  private val tempDir: Path get() = tempDirFixture.get()

  // ---------- the text of a drop ----------

  @Test
  fun `resolveText returns the path of one file`() {
    timeoutRunBlocking {
      val file = tempDir.resolve("script.sh").createFile()

      assertThat(resolveText(ideDragOf(listOf(virtualFileOf(file)))))
        .isEqualTo(expectedText(file))
    }
  }

  @Test
  fun `resolveText joins the paths of two files with one space`() {
    timeoutRunBlocking {
      val first = tempDir.resolve("first.txt").createFile()
      val second = tempDir.resolve("second.txt").createFile()

      assertThat(resolveText(ideDragOf(listOf(virtualFileOf(first), virtualFileOf(second)))))
        .isEqualTo("${expectedText(first)} ${expectedText(second)}")
    }
  }

  @Test
  fun `resolveText escapes a path with spaces`() {
    timeoutRunBlocking {
      val file = tempDir.resolve("a file with spaces.txt").createFile()

      val text = resolveText(ideDragOf(listOf(virtualFileOf(file))))

      assertThat(text).isEqualTo(expectedText(file))
      assertThat(text).isNotEqualTo(file.toString())
    }
  }

  @Test
  fun `resolveText returns plain text as is`() {
    timeoutRunBlocking {
      assertThat(resolveText(nativeTextDrag("git status --short")))
        .isEqualTo("git status --short")
    }
  }

  // ---------- the paths of a drop ----------

  @Test
  fun `resolveFilePaths returns the nio path of a local file`() {
    timeoutRunBlocking {
      val file = tempDir.resolve("local.txt").createFile()

      assertThat(resolveFilePaths(virtualFileOf(file))).containsExactly(file)
    }
  }

  /**
   * The manual Eel parse handles `ThinClientNodeVirtualFile`, and it only runs on a remote development
   * frontend. A monolith gives up instead of guessing a path.
   */
  @Test
  fun `resolveFilePaths drops a file with no nio path in the monolith`(@TestDisposable disposable: Disposable) {
    timeoutRunBlocking {
      setProductMode(ProductMode.MONOLITH, disposable)
      val file = mock<VirtualFile> { on { path } doReturn "/home/user/project/file.txt" }

      assertThat(resolveFilePaths(file)).isEmpty()
    }
  }

  /**
   * A `ThinClientNodeVirtualFile` has no nio path, so a frontend rebuilds the path from the path of the
   * file and the Eel environment of the project.
   */
  @Test
  fun `resolveFilePaths parses the path with Eel on a frontend`(@TestDisposable disposable: Disposable) {
    timeoutRunBlocking {
      setProductMode(ProductMode.FRONTEND, disposable)
      // The resolver does no I/O, so the file does not have to exist.
      val expected = tempDir.resolve("remote.txt")
      val file = mock<VirtualFile> { on { path } doReturn expected.toString() }

      assertThat(resolveFilePaths(file)).containsExactly(expected)
    }
  }

  @Test
  fun `resolveFilePaths drops a relative path on a frontend`(@TestDisposable disposable: Disposable) {
    timeoutRunBlocking {
      setProductMode(ProductMode.FRONTEND, disposable)
      val file = mock<VirtualFile> { on { path } doReturn "relative/file.txt" }

      assertThat(resolveFilePaths(file)).isEmpty()
    }
  }

  /** A drag from another application carries no virtual file, so the resolver keeps the paths as they are. */
  @Test
  fun `resolveFilePaths returns the paths of a native drag as is`() {
    timeoutRunBlocking {
      val first = tempDir.resolve("first.txt").createFile()
      val second = tempDir.resolve("second.txt").createFile()
      val data = TerminalDropData(nativeFileDrag(listOf(first.toFile(), second.toFile())))

      assertThat(TerminalDroppedContentResolver.resolveFilePaths(data, projectEel()))
        .containsExactly(first, second)
    }
  }

  // ---------- the content type of a drop ----------
  // The terminal reports this type to FUS. The resolver reads the virtual files of an IDE drag first, and
  // the paths of a native drag next, so each case runs through both kinds of drag.

  @Test
  fun `getDroppedContentType of one dragged virtual file is FILE`() {
    timeoutRunBlocking {
      val file = tempDir.resolve("file.txt").createFile()

      assertThat(contentTypeOf(ideDragOf(listOf(virtualFileOf(file))))).isEqualTo(TerminalInsertedContentType.FILE)
    }
  }

  @Test
  fun `getDroppedContentType of one dragged virtual directory is DIRECTORY`() {
    timeoutRunBlocking {
      val dir = tempDir.resolve("dir").createDirectory()

      assertThat(contentTypeOf(ideDragOf(listOf(virtualFileOf(dir))))).isEqualTo(TerminalInsertedContentType.DIRECTORY)
    }
  }

  @Test
  fun `getDroppedContentType of two dragged virtual files is MULTIPLE_ITEMS`() {
    timeoutRunBlocking {
      val first = tempDir.resolve("first.txt").createFile()
      val second = tempDir.resolve("second.txt").createFile()

      val event = ideDragOf(listOf(virtualFileOf(first), virtualFileOf(second)))

      assertThat(contentTypeOf(event)).isEqualTo(TerminalInsertedContentType.MULTIPLE_ITEMS)
    }
  }

  @Test
  fun `getDroppedContentType of one natively dragged file is FILE`() {
    timeoutRunBlocking {
      val file = tempDir.resolve("file.txt").createFile()

      assertThat(contentTypeOf(nativeFileDrag(listOf(file.toFile())))).isEqualTo(TerminalInsertedContentType.FILE)
    }
  }

  @Test
  fun `getDroppedContentType of one natively dragged directory is DIRECTORY`() {
    timeoutRunBlocking {
      val dir = tempDir.resolve("dir").createDirectory()

      assertThat(contentTypeOf(nativeFileDrag(listOf(dir.toFile())))).isEqualTo(TerminalInsertedContentType.DIRECTORY)
    }
  }

  @Test
  fun `getDroppedContentType of two natively dragged files is MULTIPLE_ITEMS`() {
    timeoutRunBlocking {
      val first = tempDir.resolve("first.txt").createFile()
      val second = tempDir.resolve("second.txt").createFile()

      val event = nativeFileDrag(listOf(first.toFile(), second.toFile()))

      assertThat(contentTypeOf(event)).isEqualTo(TerminalInsertedContentType.MULTIPLE_ITEMS)
    }
  }

  @Test
  fun `getDroppedContentType of dragged text is TEXT`() {
    timeoutRunBlocking {
      assertThat(contentTypeOf(nativeTextDrag("git status --short"))).isEqualTo(TerminalInsertedContentType.TEXT)
    }
  }

  /**
   * The caller classifies the content only after it accepts the drop, so an empty drop is a contract
   * violation and not a state to report.
   */
  @Test
  fun `getDroppedContentType of an empty drop fails`() {
    timeoutRunBlocking {
      assertThatIllegalStateException().isThrownBy { contentTypeOf(emptyDrag()) }
    }
  }

  // ---------- helpers ----------

  private fun resolveText(event: DnDEvent): String? =
    TerminalDroppedContentResolver.resolveText(
      data = TerminalDropData(event),
      terminalContext = TerminalProcessContext(projectEel(), ShellName.BASH),
      projectEelDescriptor = projectEel(),
    )

  private fun expectedText(vararg files: Path): String =
    TerminalFilePathHandler.getPathAsText(files.toList(), TerminalProcessContext(projectEel(), ShellName.BASH))

  private fun resolveFilePaths(vararg files: VirtualFile): List<Path> =
    TerminalDroppedContentResolver.resolveFilePaths(TerminalDropData(ideDragOf(files.toList())), projectEel())

  private fun contentTypeOf(event: DnDEvent): TerminalInsertedContentType =
    TerminalDropData(event).getContentType()

  private fun projectEel(): EelDescriptor = project.getEelDescriptor()
}
