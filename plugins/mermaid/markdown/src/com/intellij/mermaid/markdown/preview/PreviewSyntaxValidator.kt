// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.markdown.preview

import com.intellij.mermaid.lang.validation.MermaidSyntaxProblem
import com.intellij.mermaid.lang.validation.MermaidSyntaxValidator
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CancellationException

/**
 * Surfaces in the editor whatever the open preview is already showing as a render error.
 *
 * Reads the preview's DOM rather than calling `mermaid.parse` itself, because the bundle does not expose
 * mermaid as a global: the Kotlin/JS extension publishes only `updateMermaidDiagramContent` and
 * `collectDiagramContent` (see MermaidViewer.kt), and webpack keeps the library module-scoped. Reaching
 * `parse` would mean adding an export and rebuilding the committed mermaid.js bundle. The render path already
 * writes failures into the page as `.mermaid > .error-text` (see Render.kt), so the verdict is there for the
 * taking.
 *
 * That makes this report exactly what the user can see in the preview, which is the honest contract, with one
 * consequence: it reflects the *last render* rather than the current text. The preview re-renders on a short
 * debounce and the daemon re-runs as typing continues, so the two converge, but a report can lag by a keystroke.
 *
 * Reuses the preview's browser rather than starting one: a JCEF browser is expensive, the split preview is the
 * default for .mermaid files, and a hidden background browser would leave nobody able to see why validation
 * had stalled. The cost is that validation only runs while a preview is open, which is why [validate] returns
 * null -- distinct from an empty list -- when it could not run at all.
 */
internal class PreviewSyntaxValidator : MermaidSyntaxValidator {
  override suspend fun validate(project: Project, file: VirtualFile, text: String): List<MermaidSyntaxProblem>? {
    if (text.isBlank()) return emptyList()

    // A .mermaid file opens as MermaidEditorWithPreview, a TextEditorWithPreview, so getEditors() hands back
    // that composite rather than the preview itself. Both shapes are handled because the preview can also be
    // opened on its own.
    val editor = readAction {
      FileEditorManager.getInstance(project).getEditors(file).firstNotNullOfOrNull { editor ->
        when (editor) {
          is MermaidPreviewEditor -> editor
          is TextEditorWithPreview -> editor.previewEditor as? MermaidPreviewEditor
          else -> null
        }
      }
    } ?: return null

    val browser = try {
      editor.diagramComponent().browser
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      // The preview may still be loading, or be disposed while we asked.
      thisLogger().debug("Mermaid preview browser unavailable for validation", e)
      return null
    }

    // Messages are joined with a control character, not a newline: a single mermaid error is itself several
    // lines (the "Parse error on line N:" header, the echoed source, a caret pointer, then the expectation),
    // so splitting on newlines would shred one error into several and lose the line number from all but the
    // first of them.
    // language=JavaScript
    val code = """
      (function() {
        return new Promise(function(resolve) {
          const nodes = document.querySelectorAll(".mermaid > .error-text");
          const messages = [];
          for (let i = 0; i < nodes.length; i++) {
            const message = (nodes[i].textContent || "").trim();
            if (message.length > 0) { messages.push(message); }
          }
          resolve(messages.join(""));
        });
      })();
    """.trimIndent()

    val reported = try {
      browser.executeCancellableJavaScript(code)
    }
    catch (e: JsCallExecutionException) {
      thisLogger().debug("Mermaid validation call failed", e)
      return null
    }

    if (reported.isNullOrBlank()) return emptyList()
    return reported.split(MESSAGE_SEPARATOR)
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .map { MermaidSyntaxProblem(summarize(it), extractLine(it)) }
      .toList()
  }
}

private const val MESSAGE_SEPARATOR = ""

// mermaid phrases its parse failures as "Parse error on line 3: ..." -- the only positional information it
// gives us. Absent that, the problem is reported against the whole diagram.
private val LINE_PATTERN = Regex("""[Ll]ine (\d+)""")

private fun extractLine(message: String): Int? =
  LINE_PATTERN.find(message)?.groupValues?.get(1)?.toIntOrNull()

/**
 * Turns mermaid's multi-line dump into one readable sentence.
 *
 * It reports failures as a header, the offending source line echoed back, a run of dashes with a caret under
 * the offending column, and finally what it expected:
 * ```
 * Parse error on line 2:
 * venn-beta    set
 * -----------------^
 * Expecting 'IDENTIFIER', 'STRING', got 'NEWLINE'
 * ```
 * The echoed line and the caret only mean anything in a monospaced dump, and the editor already points at the
 * line, so only the header and the expectation are kept.
 */
private fun summarize(message: String): String {
  val lines = message.lines().map { it.trim() }.filter { it.isNotEmpty() }
  if (lines.isEmpty()) return message
  val informative = lines.filterNot { line -> line.all { it == '-' || it == '^' } }
  val header = informative.firstOrNull() ?: return message
  val expectation = informative.lastOrNull { it.startsWith("Expecting") || it.startsWith("Lexical error") }
  return when {
    expectation == null || expectation == header -> header
    header.endsWith(":") -> "$header $expectation"
    else -> "$header: $expectation"
  }
}
