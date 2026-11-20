// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.ketraterm

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.terminal.backend.hyperlinks.filter.TerminalGenericFileFilter
import com.intellij.terminal.backend.hyperlinks.filter.TerminalVfsFileLookup
import com.intellij.terminal.frontend.view.ketraterm.KetraTermHyperlinkDetector
import com.intellij.terminal.frontend.view.ketraterm.isLocalFileLinkCandidate
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.ExtensionTestUtil
import io.github.ketraterm.ui.swing.api.SwingHyperlinkDetectionContext
import io.github.ketraterm.ui.swing.api.SwingHyperlinkDetectionRequest
import io.github.ketraterm.ui.swing.api.SwingHyperlinkActivation
import io.github.ketraterm.render.api.TerminalRenderUnderline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

@TestApplication
internal class KetraTermConsoleHyperlinksTest {
  @Test
  fun `URLs keep their source offsets across wrapped and plain lines`(): Unit = timeoutRunBlocking {
    val detector = KetraTermHyperlinkDetector(projectFixture.get())
    val first = "open https://www.jetbrains.com/idea/ now\n"
    val last = "next https://ketraterm.com/\n"
    val links = detector.detect(SwingHyperlinkDetectionRequest(
      listOf(first, "ordinary output\n", last), longArrayOf(50, 54, 55), longArrayOf(53, 54, 55),
    ))
    assertThat(links.map { it.uri }).containsExactly("https://www.jetbrains.com/idea/", "https://ketraterm.com/")
    assertThat(links[0].sourceRange.start.absoluteRow).isEqualTo(50)
    assertThat(links[0].sourceRange.start.offset).isEqualTo(5)
    assertThat(links[0].sourceRange.end.offset).isEqualTo(first.indexOf(" now"))
    assertThat(links[1].sourceRange.start.absoluteRow).isEqualTo(55)
  }

  @Test
  fun `bare names resolve against each line's directory and stay separate from URLs`(@TempDir directory: Path): Unit = timeoutRunBlocking {
    val first = directory.resolve("first").createDirectories()
    val second = directory.resolve("second").createDirectories()
    val readme = first.resolve("README").also { it.writeText("first") }
    val number = second.resolve("123").also { it.writeText("second") }
    val fileSystem = LocalFileSystem.getInstance()
    checkNotNull(fileSystem.refreshAndFindFileByNioFile(readme))
    checkNotNull(fileSystem.refreshAndFindFileByNioFile(number))
    val detector = KetraTermHyperlinkDetector(projectFixture.get(), directoryForLine = { id ->
      (if (id == 10L) first else second).toUri().toString()
    })
    val url = "https://example.org/README"
    val links = detector.detect(SwingHyperlinkDetectionRequest(
      listOf("$url README:1:1 123\n", "README 123\n", "README\n"),
      longArrayOf(0, 1, 2), firstLineIds = longArrayOf(10, 11, 10),
    ))
    val readmeUri = readme.toUri().toASCIIString()
    assertThat(links.map { it.uri }).containsExactly(url, readmeUri, number.toUri().toASCIIString(), readmeUri)
  }

  @Test
  fun `bare file links follow creation and deletion between requests`(@TempDir directory: Path): Unit = timeoutRunBlocking {
    val fileSystem = LocalFileSystem.getInstance()
    checkNotNull(fileSystem.refreshAndFindFileByNioFile(directory))
    val detector = KetraTermHyperlinkDetector(projectFixture.get(), { directory })
    val request = SwingHyperlinkDetectionRequest(listOf("README\n"), longArrayOf(0))
    assertThat(detector.detect(request)).isEmpty()
    val path = directory.resolve("README").also { it.writeText("new file") }
    val file = checkNotNull(fileSystem.refreshAndFindFileByNioFile(path))
    assertThat(detector.detect(request).single().uri).isEqualTo(path.toUri().toASCIIString())
    Files.delete(path)
    file.refresh(false, false)
    assertThat(detector.detect(request)).isEmpty()
  }

  @Test
  fun `asterisks in Windows path candidates are rejected before parsing`() {
    for (candidate in listOf("*", "**", "src/*.kt", "file*name.txt")) {
      assertThat(isLocalFileLinkCandidate(candidate)).describedAs(candidate).isEqualTo(!SystemInfoRt.isWindows)
    }
  }

  @Test
  fun `a wildcard token preserves neighboring file links and literal Unix filenames`(@TempDir directory: Path): Unit = timeoutRunBlocking {
    val plain = directory.resolve("README").also { it.writeText("text") }
    val fileSystem = LocalFileSystem.getInstance()
    fileSystem.refreshAndFindFileByNioFile(plain)
    val expected = mutableListOf<String>()
    if (!SystemInfoRt.isWindows) {
      val literal = directory.resolve("file*name.txt").also { it.writeText("text") }
      fileSystem.refreshAndFindFileByNioFile(literal)
      expected.add(literal.toUri().toASCIIString())
    }
    expected.add(plain.toUri().toASCIIString())
    val detector = KetraTermHyperlinkDetector(projectFixture.get(), { directory })
    val request = SwingHyperlinkDetectionRequest(listOf("* \"file*name.txt\" README\n"), longArrayOf(0))
    assertThat(detector.detect(request).map { it.uri }).isEqualTo(expected)
  }

  @Test
  fun `invisible console path links preserve their presentation and activation`(
    @TestDisposable disposable: Disposable,
    @TempDir directory: Path,
  ): Unit = timeoutRunBlocking {
    val fileSystem = LocalFileSystem.getInstance()
    fileSystem.refreshAndFindFileByNioFile(directory)
    installFilter(disposable, TerminalGenericFileFilter(
      projectFixture.get(), LocalEelDescriptor, null, TerminalVfsFileLookup(fileSystem),
    ))
    val detector = KetraTermHyperlinkDetector(projectFixture.get())
    val link = detector.detect(ordered("PS $directory> \n", 0)).single()
    assertThat(link.activation).isEqualTo(SwingHyperlinkActivation.MODIFIER)
    assertThat(link.presentation.isVisible).isFalse()
    assertThat(link.presentation.normal?.foregroundArgb).isNull()
    assertThat(link.presentation.normal?.underlineStyle).isEqualTo(TerminalRenderUnderline.NONE)
    assertThat(link.presentation.hovered?.underlineStyle).isEqualTo(TerminalRenderUnderline.SINGLE)
  }

  @Test
  fun `prompt paths retain terminal colors and require a modifier`(@TempDir directory: Path): Unit = timeoutRunBlocking {
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(directory)
    val detector = KetraTermHyperlinkDetector(projectFixture.get())
    val text = "PS $directory> "
    val link = detector.detect(SwingHyperlinkDetectionRequest(listOf(text + "\n"), longArrayOf(0))).single()
    assertThat(link.sourceRange.start.offset).isEqualTo(3)
    assertThat(link.sourceRange.end.offset).isEqualTo(text.length - 2)
    assertThat(link.activation).isEqualTo(SwingHyperlinkActivation.MODIFIER)
    assertThat(link.presentation?.normal?.foregroundArgb).isNull()
    assertThat(link.presentation?.normal?.underlineStyle).isEqualTo(TerminalRenderUnderline.NONE)
    assertThat(link.presentation?.hovered?.underlineStyle).isEqualTo(TerminalRenderUnderline.SINGLE)
    assertThat(link.presentation?.isVisible).isFalse()
  }

  @Test
  fun `URLs use console styling and validate the complete token`(): Unit = timeoutRunBlocking {
    val detector = KetraTermHyperlinkDetector(projectFixture.get())
    for (uri in listOf("https://example.org/docs", "file:///C:/work/file.txt")) {
      val text = "open $uri next"
      val link = detector.detect(SwingHyperlinkDetectionRequest(listOf(text + "\n"), longArrayOf(0))).single()
      assertThat(link.uri).isEqualTo(uri)
      assertThat(link.activation).isEqualTo(SwingHyperlinkActivation.DIRECT)
      assertThat(link.presentation?.isVisible).isTrue()
      assertThat(link.dependencyRange.start.offset).isEqualTo(4)
      assertThat(link.dependencyRange.end.offset).isEqualTo(6 + uri.length)
    }
  }

  @Test
  fun `relative file links use the command directory and navigate to line and column`(@TempDir directory: Path): Unit = timeoutRunBlocking {
    val launch = directory.resolve("launch").createDirectories()
    val historical = directory.resolve("historical").createDirectories()
    val file = historical.resolve("file name.txt").also { it.writeText("first\nsecond\nthird\n") }
    val virtualFile = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file))
    val detector = KetraTermHyperlinkDetector(projectFixture.get(), { launch }, { id ->
      if (id == 42L) historical.toUri().toString() else null
    })
    val request = SwingHyperlinkDetectionRequest(
      listOf("see \"file name.txt\":2:3\n"), longArrayOf(10), longArrayOf(12), firstLineIds = longArrayOf(42),
    )
    val link = detector.detect(request).single()
    assertThat(link.uri).isEqualTo(file.toUri().toASCIIString())
    assertThat(link.sourceRange.start.absoluteRow).isEqualTo(10)
    assertThat(link.sourceRange.start.offset).isEqualTo(4)
    withContext(Dispatchers.EDT) {
      try {
        assertThat(link.action.open()).isTrue()
        val editor = checkNotNull(FileEditorManager.getInstance(projectFixture.get()).selectedTextEditor)
        assertThat(editor.caretModel.logicalPosition.line).isEqualTo(1)
        assertThat(editor.caretModel.logicalPosition.column).isEqualTo(2)
      }
      finally {
        FileEditorManager.getInstance(projectFixture.get()).closeFile(virtualFile)
        EditorHistoryManager.getInstance(projectFixture.get()).removeFile(virtualFile)
      }
    }
  }

  @Test
  fun `unknown remote directory does not resolve against the launch directory`(@TempDir directory: Path): Unit = timeoutRunBlocking {
    val file = directory.resolve("local.txt").also { it.writeText("text") }
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file)
    val detector = KetraTermHyperlinkDetector(projectFixture.get(), { directory }, { "ssh://other/home" })
    assertThat(detector.detect(SwingHyperlinkDetectionRequest(listOf("local.txt\n"), longArrayOf(0)))).isEmpty()
    val remote = KetraTermHyperlinkDetector(projectFixture.get(), { directory }, localFileLinks = { false })
    assertThat(remote.detect(SwingHyperlinkDetectionRequest(listOf("$file\n"), longArrayOf(0)))).isEmpty()
  }

  @Test
  fun `ordered filters retain earlier lines across batches and restart on replay`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    var creations = 0
    val calls = ArrayList<Pair<String, Int>>()
    val provider = ConsoleFilterProvider {
      creations++
      arrayOf(object : Filter, DumbAware {
        private var header: Int? = null
        override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
          calls.add(line to entireLength)
          if (line == "header\n") header = entireLength - line.length
          return if (line == "frame\n") header?.let { Filter.Result(it, it + 6, HyperlinkInfo {}) } else null
        }
      })
    }
    ExtensionTestUtil.maskExtensions(
      ConsoleFilterProvider.FILTER_PROVIDERS, listOf(provider), disposable,
    )
    val detector = KetraTermHyperlinkDetector(projectFixture.get())
    assertThat(detector.detect(ordered("header\n", 10))).isEmpty()
    val link = detector.detect(ordered("frame\n", 13)).single()
    assertThat(link.sourceRange.start.absoluteRow).isEqualTo(10)
    assertThat(link.sourceRange.end.offset).isEqualTo(6)
    assertThat(link.consumedThrough.absoluteRow).isEqualTo(13)
    assertThat(calls).containsExactly("header\n" to 7, "frame\n" to 13)
    assertThat(detector.detect(ordered("frame\n", 20, replay = 1))).isEmpty()
    assertThat(creations).isEqualTo(2)
  }

  @Test
  fun `evicted source ranges cannot produce links`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    installFilter(disposable, object : Filter, DumbAware {
      override fun applyFilter(line: String, entireLength: Int): Filter.Result? =
        if (line == "frame\n") Filter.Result(0, 6, HyperlinkInfo {}) else null
    })
    val detector = KetraTermHyperlinkDetector(projectFixture.get())
    detector.detect(ordered("header\n", 10))
    assertThat(detector.detect(ordered("frame\n", 13, retained = 13))).isEmpty()
  }

  @Test
  fun `absolute and parenthesized file locations preserve Unicode offsets`(@TempDir directory: Path): Unit = timeoutRunBlocking {
    val file = directory.resolve("source.kt").also { it.writeText("one\ntwo\n") }
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file)
    val detector = KetraTermHyperlinkDetector(projectFixture.get(), { directory })
    for (reference in listOf("source.kt(2,1)", "\"$file\":2:1")) {
      val prefix = "🙂 error "
      val request = SwingHyperlinkDetectionRequest(listOf("$prefix$reference\n"), longArrayOf(7), longArrayOf(9))
      val link = detector.detect(request).single()
      assertThat(link.sourceRange.start.offset).isEqualTo(prefix.length)
      assertThat(link.sourceRange.end.offset).isEqualTo(prefix.length + reference.length)
      assertThat(link.uri).isEqualTo(file.toUri().toASCIIString())
    }
  }

  @Test
  fun `invalid provider offsets are ignored`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    installFilter(disposable, object : Filter, DumbAware {
      override fun applyFilter(line: String, entireLength: Int): Filter.Result =
        Filter.Result(entireLength + 1, entireLength + 8, HyperlinkInfo {})
    })
    assertThat(KetraTermHyperlinkDetector(projectFixture.get()).detect(ordered("header\n", 0))).isEmpty()
  }

  @Test
  fun `cancelled provider state is discarded before the next request`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    var creations = 0
    val provider = ConsoleFilterProvider {
      val instance = ++creations
      arrayOf(object : Filter, DumbAware {
        override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
          if (instance == 1) throw CancellationException("Interrupted provider")
          return Filter.Result(0, 6, HyperlinkInfo {})
        }
      })
    }
    ExtensionTestUtil.maskExtensions(
      ConsoleFilterProvider.FILTER_PROVIDERS, listOf(provider), disposable,
    )
    val detector = KetraTermHyperlinkDetector(projectFixture.get())
    val failure = runCatching { detector.detect(ordered("header\n", 0)) }.exceptionOrNull()
    assertThat(failure).isInstanceOf(CancellationException::class.java)
    assertThat(detector.detect(ordered("header\n", 0))).hasSize(1)
    assertThat(creations).isEqualTo(2)
  }

  private fun installFilter(disposable: Disposable, filter: Filter) {
    ExtensionTestUtil.maskExtensions(
      ConsoleFilterProvider.FILTER_PROVIDERS, listOf(ConsoleFilterProvider { arrayOf(filter) }), disposable,
    )
  }

  private fun ordered(text: String, row: Long, replay: Long = 0, retained: Long = 0): SwingHyperlinkDetectionRequest =
    SwingHyperlinkDetectionRequest(
      listOf(text), longArrayOf(row), context = SwingHyperlinkDetectionContext.ORDERED_CONTENT,
      analysisEpoch = replay, firstRetainedRow = retained,
    )

  companion object {
    private val projectFixture = projectFixture()
  }
}
