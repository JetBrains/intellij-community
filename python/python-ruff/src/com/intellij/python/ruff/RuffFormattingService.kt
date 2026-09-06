// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ruff

import com.intellij.formatting.FormattingContext
import com.intellij.formatting.service.AsyncDocumentFormattingService
import com.intellij.formatting.service.AsyncFormattingRequest
import com.intellij.formatting.service.FormattingService
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.psi.PsiFile
import com.intellij.python.community.execService.Args
import com.intellij.python.pytools.backend.isEnabledOn
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.Result
import com.jetbrains.python.pyi.PyiFileType
import com.jetbrains.python.sdk.ModuleOrProject
import kotlin.coroutines.cancellation.CancellationException

private val LOG = logger<RuffFormattingService>()

// Reuse the shared Python LSP tools notification group (registered in intellij.python.lsp.core).
private const val NOTIFICATION_GROUP_ID: String = "Python LSP Tools"

/**
 * Formats a Python file with the Ruff executable, **only** for the combined "sort imports when formatting" case.
 *
 * Plain `Reformat Code` stays with the platform's LSP formatter (`textDocument/formatting`, see
 * [com.intellij.python.ruff.server.RuffLspClientDescriptor]'s `formattingCustomizer`). That keeps the long-lived server
 * in charge and spawns no process. This service takes over only when the "Sort imports when formatting" option is on,
 * because import sorting is a lint fix and not a part of `ruff format`, so it needs a second pass.
 *
 * The two passes run as a text pipeline: `ruff format`, then `ruff check --fix-only --select I`, each over stdin. The
 * text pipeline is what makes the result correct. `ruff format` reports its result as one edit that spans everything it
 * changed, so an import-sort edit set computed against the same original text always overlaps it, and the two sets
 * cannot merge. The second pass instead reads the formatted text, exactly as `ruff format | ruff check --fix I` does in
 * a shell.
 *
 * The service declares no [FormattingService.Feature], so the platform sends only an explicit whole-file reformat here.
 * A fragment selection and on-typing formatting keep their existing route, where import sorting means nothing anyway.
 * The destructive removal of an unused import stays exclusive to `Optimize Imports`.
 */
internal class RuffFormattingService : AsyncDocumentFormattingService() {
  override fun getName(): String = "Ruff"

  override fun getNotificationGroupId(): String = NOTIFICATION_GROUP_ID

  override fun getFeatures(): Set<FormattingService.Feature> = emptySet()

  // Ruff reads the source over stdin, so the file on disk does not have to match the editor.
  override fun prepareForFormatting(document: Document, formattingContext: FormattingContext): Unit = Unit

  override fun canFormat(source: PsiFile): Boolean {
    val virtualFile = source.virtualFile ?: return false
    // An injected fragment or a non-local file has no path that Ruff can read its configuration from.
    if (!virtualFile.isInLocalFileSystem || virtualFile is VirtualFileWindow) return false
    val fileType = virtualFile.fileType
    if (fileType != PythonFileType.INSTANCE && fileType != PyiFileType.INSTANCE) return false

    val project = source.project
    if (project.isDefault) return false
    if (!RuffPyTool.getInstance().isEnabledOn(project)) return false

    val config = project.service<RuffConfiguration>()
    // Plain formatting goes through the platform LSP formatter; this service only owns the format + import-sort combo.
    return config.formatting && config.formatSortImports
  }

  override fun createFormattingTask(formattingRequest: AsyncFormattingRequest): FormattingTask? {
    val context = formattingRequest.context
    val virtualFile = context.virtualFile ?: return null
    val path = virtualFile.ruffPath() ?: return null
    val moduleOrProject = ruffScopeOf(context.project, virtualFile)
    val originalText = formattingRequest.documentText

    return object : FormattingTask {
      override fun run() {
        try {
          val newText = runBlockingCancellable { formatAndSortImports(moduleOrProject, path, originalText) }
          formattingRequest.onTextReady(newText?.takeIf { it != originalText })
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: Exception) {
          LOG.warn("Ruff formatting failed for $path", e)
          formattingRequest.onError(RuffBundle.message("ruff.formatting.failed.title"), e.localizedMessage ?: e.toString())
        }
      }

      override fun cancel(): Boolean = true

      override fun isRunUnderProgress(): Boolean = true
    }
  }

  /** `ruff format` piped into `ruff check --fix-only --select I`, or `null` when either pass fails. */
  private suspend fun formatAndSortImports(moduleOrProject: ModuleOrProject, path: String, originalText: String): String? {
    val ruff = RuffPyTool.getInstance()

    val formatArgs = Args("format", "--stdin-filename", path, "-")
    val formatted = when (val result = ruff.runOnStdin(moduleOrProject, formatArgs, originalText)) {
      is Result.Success -> result.result
      is Result.Failure -> {
        LOG.warn("Ruff format failed for $path: ${result.error.message}")
        return null
      }
    }

    // `I` sorts imports. It is force-selected, so the option works whatever the project's rule set enables.
    val sortArgs = Args("check", "--fix-only", "--select", "I", "--stdin-filename", path, "-")
    return when (val result = ruff.runOnStdin(moduleOrProject, sortArgs, formatted)) {
      is Result.Success -> result.result
      is Result.Failure -> {
        LOG.warn("Ruff import sort failed for $path: ${result.error.message}")
        null
      }
    }
  }
}
