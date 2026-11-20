/*
 * Copyright 2026 Gagik Sargsyan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.terminal.frontend.view.ketraterm

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.ConsoleFilterProviderEx
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.execution.filters.UrlFilter
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.search.GlobalSearchScope
import io.github.ketraterm.ui.swing.api.SwingHyperlink
import io.github.ketraterm.ui.swing.api.SwingHyperlinkActivation
import io.github.ketraterm.ui.swing.api.SwingHyperlinkDetectionContext
import io.github.ketraterm.ui.swing.api.SwingHyperlinkDetectionRequest
import io.github.ketraterm.ui.swing.api.SwingHyperlinkDetector
import io.github.ketraterm.ui.swing.api.SwingHyperlinkTextPosition
import io.github.ketraterm.ui.swing.api.SwingHyperlinkTextRange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.URI
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.TreeMap
import java.util.concurrent.atomic.AtomicLong
import java.util.regex.Pattern

/** Discovers file links and adapts console filters without an editor document. */
internal class KetraTermHyperlinkDetector(
  private val project: Project,
  private val launchDirectory: () -> Path? = { null },
  private val directoryForLine: (Long) -> String? = { null },
  private val localFileLinks: () -> Boolean = { true },
) : SwingHyperlinkDetector {
  private val urlFilter = UrlFilter(project)

  override val context = SwingHyperlinkDetectionContext.INDEPENDENT_AND_ORDERED
  private val revision = AtomicLong()
  override val configurationGeneration: Long
    get() = revision.get() + DumbService.getInstance(project).modificationTracker.modificationCount

  override val configurationChanges: Flow<Unit>
    get() =
      callbackFlow {
        val lifetime = Disposer.newDisposable("KetraTerm hyperlink providers")
        val changed = {
          revision.incrementAndGet()
          trySend(Unit)
          Unit
        }
        val connection = project.messageBus.connect(lifetime)
        connection.subscribe(
          DumbService.DUMB_MODE,
          object : DumbService.DumbModeListener {
            override fun enteredDumbMode() = changed()

            override fun exitDumbMode() = changed()
          },
        )
        connection.subscribe(
          ModuleRootListener.TOPIC,
          object : ModuleRootListener {
            override fun rootsChanged(event: ModuleRootEvent) = changed()
          },
        )
        val applicationConnection = ApplicationManager.getApplication().messageBus.connect(lifetime)
        applicationConnection.subscribe(
          VirtualFileManager.VFS_CHANGES,
          object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
              if (events.any { it !is VFileContentChangeEvent }) changed()
            }
          },
        )
        applicationConnection.subscribe(
          EditorColorsManager.TOPIC,
          object : EditorColorsListener {
            override fun globalSchemeChange(scheme: EditorColorsScheme?) = changed()
          },
        )
        ConsoleFilterProvider.FILTER_PROVIDERS.point.addChangeListener(this) { changed() }
        awaitClose { Disposer.dispose(lifetime) }
      }

  // Only ordered requests access this state. Independent calls do not wait for it.
  private var ordered: OrderedState? = null

  override fun discardOrderedState() {
    ordered = null
  }

  override suspend fun detect(request: SwingHyperlinkDetectionRequest): List<SwingHyperlink> {
    currentCoroutineContext().ensureActive()
    if (project.isDisposed) return emptyList()
    return when (request.context) {
      SwingHyperlinkDetectionContext.INDEPENDENT_LINE -> detectIndependent(request)
      SwingHyperlinkDetectionContext.ORDERED_CONTENT -> detectOrdered(request)
      SwingHyperlinkDetectionContext.INDEPENDENT_AND_ORDERED -> error("Discovery requests must select one lane")
    }
  }

  private suspend fun detectIndependent(request: SwingHyperlinkDetectionRequest): List<SwingHyperlink> = readAction {
    val results = ArrayList<SwingHyperlink>()
    val files = if (localFileLinks()) FILE_REFERENCE.matcher("") else null
    val fileSystem = LocalFileSystem.getInstance()
    var previousDirectory: String? = null
    var parent: VirtualFile? = null
    for (line in 0 until request.lineCount) {
      ProgressManager.checkCanceled()
      val text = request.lineText(line)
      val firstUrl = results.size
      collectUrls(request, line, text, results)
      if (files == null) continue
      val afterUrls = results.size
      val directory = outputDirectory(request.lineFirstId(line))
      files.reset(text)
      while (files.find()) {
        ProgressManager.checkCanceled()
        val start = files.start()
        val end = files.end()
        if ((firstUrl until afterUrls).any { start < results[it].sourceRange.end.offset && end > results[it].sourceRange.start.offset }) continue
        val raw = files.group(1) ?: files.group(2) ?: files.group(3)
        if (!isLocalFileLinkCandidate(raw)) continue
        val path: Path
        val file: VirtualFile
        if (raw.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' } && !raw.endsWith('.')) {
          if (directory == null) continue
          val directoryName = directory.toString()
          if (directoryName != previousDirectory) {
            parent = fileSystem.findFileByNioFile(directory.normalize())
            previousDirectory = directoryName
          }
          file = parent?.findChild(raw) ?: continue
          path = directory.resolve(raw).normalize()
        }
        else {
          path = try {
            val parsed = Path.of(raw)
            if (parsed.isAbsolute) parsed.normalize() else (directory ?: continue).resolve(parsed).normalize()
          }
          catch (_: InvalidPathException) {
            continue
          }
          file = fileSystem.findFileByNioFile(path) ?: continue
        }
        if (!file.isValid) continue
        val lineNumber = fileCoordinate(files.group(4) ?: files.group(6)) ?: continue
        val column = fileCoordinate(files.group(5) ?: files.group(7)) ?: continue
        val info = OpenFileHyperlinkInfo(project, file, lineNumber, column)
        results.add(request.hyperlink(line, start, end, KetraTermHyperlinkAction(project, info),
                                      uri = path.toUri().toASCIIString(), presentation = intellijImplicitHyperlinkPresentation(),
                                      activation = SwingHyperlinkActivation.MODIFIER))
      }
    }
    results
  }

  private fun collectUrls(request: SwingHyperlinkDetectionRequest, line: Int, text: String, results: MutableList<SwingHyperlink>) {
    for (item in urlFilter.applyFilter(text, text.length)?.resultItems.orEmpty()) {
      val info = item.hyperlinkInfo ?: continue
      val start = item.highlightStartOffset
      val end = item.highlightEndOffset
      if (start < 0 || end <= start || end > text.length) continue
      val validation = urlValidationRange(text, start, end)
      results.add(request.hyperlink(line, start, end, KetraTermHyperlinkAction(project, info),
                                    validation.startOffset, validation.endOffset, text.substring(start, end),
                                    intellijHyperlinkPresentation(item), SwingHyperlinkActivation.DIRECT))
    }
  }

  private fun fileCoordinate(value: String?): Int? = if (value == null) 0 else value.toIntOrNull()?.takeIf { it > 0 }?.minus(1)

  private fun outputDirectory(lineId: Long): Path? {
    val uri = directoryForLine(lineId) ?: return launchDirectory()
    return try {
      val parsed = URI(uri)
      if (parsed.scheme != "file" || !parsed.authority.isNullOrEmpty() && parsed.authority != "localhost") return null
      Path.of(URI("file", null, parsed.path, null)).normalize()
    }
    catch (_: IllegalArgumentException) {
      null
    }
    catch (_: java.net.URISyntaxException) {
      null
    }
  }

  private suspend fun detectOrdered(request: SwingHyperlinkDetectionRequest): List<SwingHyperlink> {
    if (request.lineCount == 0) return emptyList()
    val key = Epochs(request.bindingEpoch, request.sourceEpoch, request.providerEpoch, request.analysisEpoch, configurationGeneration)
    var state = ordered
    if (state == null || state.epochs != key) {
      val filters = readAction { providerFilters() }
      state = OrderedState(key, filters)
      ordered = state
    }
    val chain = state
    if (chain.filters.isEmpty()) return emptyList()
    val results = ArrayList<SwingHyperlink>()
    try {
      while (chain.lines.firstEntry()?.value?.lastRow?.let { it < request.firstRetainedRow } == true) {
        chain.lines.pollFirstEntry()
      }
      for (line in 0 until request.lineCount) {
        currentCoroutineContext().ensureActive()
        val text = request.lineText(line)
        val start = chain.offset
        val end = Math.addExact(start, text.length)
        val source = OrderedLine(request.lineFirstAbsoluteRow(line), request.lineLastAbsoluteRow(line))
        chain.lines[start] = source
        if (chain.start == null) chain.start = SwingHyperlinkTextPosition(source.firstRow, 0)
        for ((order, filter) in chain.filters.withIndex()) {
          currentCoroutineContext().ensureActive()
          var invoked = false
          var retried = false
          val providerResults =
            readAction {
              // A write action can retry this lambda. Never retry mutated provider state.
              if (invoked) {
                retried = true
                return@readAction emptyList()
              }
              if (!DumbService.getInstance(project).isUsableInCurrentContext(filter)) return@readAction emptyList()
              invoked = true
              val result = filter.applyFilter(text, end)
              ProgressManager.checkCanceled()
              val items = result?.resultItems ?: return@readAction emptyList()
              items.mapNotNull { item ->
                val info = item.hyperlinkInfo ?: return@mapNotNull null
                val first = chain.lines.floorEntry(item.highlightStartOffset) ?: return@mapNotNull null
                val last = chain.lines.floorEntry(item.highlightEndOffset - 1) ?: return@mapNotNull null
                if (item.highlightEndOffset <= item.highlightStartOffset ||
                  item.highlightEndOffset > end
                ) {
                  return@mapNotNull null
                }
                val range =
                  SwingHyperlinkTextRange(
                    SwingHyperlinkTextPosition(first.value.firstRow, item.highlightStartOffset - first.key),
                    SwingHyperlinkTextPosition(last.value.firstRow, item.highlightEndOffset - last.key),
                  )
                val consumed = SwingHyperlinkTextPosition(source.firstRow, text.length)
                SwingHyperlink(
                  range,
                  SwingHyperlinkTextRange(checkNotNull(chain.start), consumed),
                  KetraTermHyperlinkAction(project, info),
                  presentation = intellijHyperlinkPresentation(item),
                  activation = if (item.isInvisibleLink) SwingHyperlinkActivation.MODIFIER else SwingHyperlinkActivation.DIRECT,
                  consumedThrough = consumed,
                  providerOrder = order + 2,
                )
              }
            }
          if (retried) throw CancellationException("Console read interrupted; ordered replay required")
          results.addAll(providerResults)
        }
        chain.offset = end
      }
    }
    catch (failure: Throwable) {
      ordered = null
      throw failure
    }
    return results
  }

  private fun providerFilters(): List<Filter> {
    val scope = GlobalSearchScope.allScope(project)
    val providers = ConsoleFilterProvider.FILTER_PROVIDERS.extensionList
    return providers.flatMap { provider ->
      try {
        (
          if (provider is ConsoleFilterProviderEx) {
            provider.getDefaultFilters(
              project,
              scope,
            )
          }
          else {
            provider.getDefaultFilters(project)
          }
        ).filter { it.javaClass != UrlFilter::class.java }
      }
      catch (failure: Exception) {
        com.intellij.diagnostic.rethrowControlFlowException(failure)
        LOG.warn("Console hyperlink provider configuration failed: " + provider.javaClass.name, failure)
        throw failure
      }
    }
  }

  private data class Epochs(
    val binding: Long,
    val source: Long,
    val provider: Long,
    val replay: Long,
    val configuration: Long,
  )

  private class OrderedState(
    val epochs: Epochs,
    val filters: List<Filter>,
  ) {
    val lines = TreeMap<Int, OrderedLine>()
    var offset = 0
    var start: SwingHyperlinkTextPosition? = null
  }

  private class OrderedLine(
    val firstRow: Long,
    val lastRow: Long,
  )

  private companion object {
    val LOG = Logger.getInstance(KetraTermHyperlinkDetector::class.java)
    val FILE_REFERENCE =
      Pattern.compile("""(?:"([^"\r\n]+)"|'([^'\r\n]+)'|((?:[A-Za-z]:[\\/])?[^\s"'<>|:()]+))(?::(\d+)(?::(\d+))?|\((\d+)(?:,(\d+))?\))?""")
  }
}

internal fun isLocalFileLinkCandidate(text: String): Boolean =
  text != "." && text != ".." && !text.all { it == '/' || it == '\\' } && !(SystemInfoRt.isWindows && '*' in text)

/** Includes the full token and its delimiters because punctuation can extend a URL match later. */
internal fun urlValidationRange(
  lineText: String,
  startOffset: Int,
  endOffset: Int,
): TextRange {
  var start = startOffset
  while (start > 0 && !isUrlTokenDelimiter(lineText[start - 1])) start--
  if (start > 0) start--
  var end = endOffset
  while (end < lineText.length && !isUrlTokenDelimiter(lineText[end])) end++
  if (end < lineText.length) end++
  return TextRange(start, end)
}

// URLUtil's URL and file patterns stop at ASCII regex whitespace, not all Unicode whitespace.
private fun isUrlTokenDelimiter(character: Char): Boolean = character == ' ' || character in '\t'..'\r'
