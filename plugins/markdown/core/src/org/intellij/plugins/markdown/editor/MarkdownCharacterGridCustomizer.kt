// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.view.DoubleWidthCharacterStrategy
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorCustomizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.editor.tables.TableCharacterWidthUtils.isFullWidthCharacter
import org.intellij.plugins.markdown.lang.hasMarkdownType
import kotlin.time.Duration.Companion.milliseconds

private val TABLE_SEPARATOR = Regex("""\|\s*:?-{3,}""")
internal fun CharSequence.hasFullWidth() = any { isFullWidthCharacter(it.code) }
internal fun CharSequence.hasTableSeparator() = TABLE_SEPARATOR.containsMatchIn(this)

internal fun enableGridMode(editor: EditorImpl) {
  editor.settings.characterGridWidthMultiplier = 1.0f
  editor.reinitSettings()
  editor.characterGrid?.doubleWidthCharacterStrategy = DoubleWidthCharacterStrategy(::isFullWidthCharacter)
}

internal class MarkdownCharacterGridCustomizer : TextEditorCustomizer {
  override fun customize(textEditor: TextEditor, coroutineScope: CoroutineScope) {
    coroutineScope.launch { execute(textEditor) }
  }

  @OptIn(FlowPreview::class)
  private suspend fun execute(textEditor: TextEditor) {
    if (!textEditor.file.hasMarkdownType()) return
    val editor = textEditor.editor as? EditorImpl ?: return
    val document = editor.document

    val initialText = document.immutableCharSequence
    if (initialText.hasFullWidth() && initialText.hasTableSeparator()) {
      withContext(Dispatchers.EDT) { enableGridMode(editor) }
      return
    }

    // BulkAwareDocumentListener funnels per-change events outside bulk mode and a single
    // bulkUpdateFinished after a bulk operation (paste/replace-all/multi-caret).
    // Each fire pings the rescan flow which debounces and coalesces via Flow operators.
    val rescan = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val listener = object : BulkAwareDocumentListener {
      override fun documentChangedNonBulk(event: DocumentEvent) {
        val fragment = event.newFragment
        val mightMatter = fragment.any { isFullWidthCharacter(it.code) || it == '-' || it == '|' }
        if (mightMatter) rescan.tryEmit(Unit)
      }
      override fun bulkUpdateFinished(document: Document) {
        rescan.tryEmit(Unit)
      }
    }
    document.addDocumentListener(listener, textEditor)

    // Suspend until a debounced scan shows both CJK and a table separator are present.
    // Scans run off-EDT via Dispatchers.Default. The flow is cancelled when the editor closes
    // (scope ends) or once first { it } finds the qualifying snapshot.
    rescan
      .debounce(150.milliseconds)
      .map {
        withContext(Dispatchers.Default) {
          val text = document.immutableCharSequence
          text.hasFullWidth() && text.hasTableSeparator()
        }
      }
      .first { it }

    withContext(Dispatchers.EDT) {
      document.removeDocumentListener(listener)
      enableGridMode(editor)
    }
  }
}
