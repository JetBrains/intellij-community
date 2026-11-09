// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.jcef.fileActions.export

import com.intellij.markdown.jcef.preview.HtmlExporter
import com.intellij.markdown.jcef.preview.HtmlSourceTextPreprocessor
import com.intellij.markdown.jcef.preview.MarkdownJCEFHtmlPanel
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.cef.misc.CefPdfPrintSettings
import org.intellij.plugins.markdown.fileActions.export.MarkdownHtmlExportResult
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val IMAGE_LOAD_TIMEOUT = 30.seconds

private val EXPORT_TIMEOUT = 2.minutes

/**
 * Renders [mdFile] in an offscreen Markdown preview and runs [action] on it.
 *
 * The preview serves its images and styles only until this function returns.
 * Throws [IllegalStateException] when the rendering and the action take longer than [EXPORT_TIMEOUT].
 * On the remote development host, the frontend renders the preview, and a lost frontend never answers.
 */
internal suspend fun <T : Any> withMarkdownPreview(
  project: Project,
  mdFile: VirtualFile,
  waitForImages: Boolean = false,
  action: suspend (MarkdownJCEFHtmlPanel) -> T,
): T {
  val panel = withContext(Dispatchers.EDT) {
    MarkdownJCEFHtmlPanel(project, mdFile).apply { createImmediately() }
  }
  try {
    return withTimeoutOrNull(EXPORT_TIMEOUT) {
      val document = readAction { FileDocumentManager.getInstance().getDocument(mdFile) } ?: error("Cannot load the Markdown document")
      val content = readAction { HtmlSourceTextPreprocessor().preprocessText(project, document, mdFile) }
      panel.setHtmlAndWait(content, mdFile, panel.imageResourceProvider)
      if (waitForImages) {
        panel.waitForImages(IMAGE_LOAD_TIMEOUT)
      }
      action(panel)
    } ?: error("The export of ${mdFile.path} took longer than $EXPORT_TIMEOUT")
  }
  finally {
    Disposer.dispose(panel)
  }
}

/**
 * Renders [mdFile] and prints it to the PDF file at [path]. Returns `false` when JCEF cannot print the file.
 */
internal suspend fun printMarkdownToPdf(project: Project, mdFile: VirtualFile, path: String): Boolean {
  return withMarkdownPreview(project, mdFile, waitForImages = true) { panel ->
    suspendCancellableCoroutine { continuation ->
      panel.cefBrowser.printToPDF(path, CefPdfPrintSettings()) { _, ok -> continuation.resume(ok) }
    }
  }
}

/**
 * Renders [mdFile] and converts it to a standalone HTML document. See [HtmlExporter] for [imagesSrcDir].
 */
internal suspend fun exportMarkdownToHtml(project: Project, mdFile: VirtualFile, imagesSrcDir: String?): MarkdownHtmlExportResult {
  return withMarkdownPreview(project, mdFile) { panel ->
    val source = suspendCancellableCoroutine { continuation ->
      panel.cefBrowser.getSource { continuation.resume(it) }
    }
    withContext(Dispatchers.IO) {
      HtmlExporter(source, project, imagesSrcDir).export()
    }
  }
}
