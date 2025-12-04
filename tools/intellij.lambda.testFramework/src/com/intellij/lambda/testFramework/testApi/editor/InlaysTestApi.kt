package com.intellij.lambda.testFramework.testApi.editor

import com.intellij.codeInsight.inline.completion.render.InlineCompletionLineRenderer
import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.TextRange
import com.intellij.remoteDev.tests.impl.utils.waitSuspending
import kotlin.time.Duration.Companion.seconds

/**
 * Only InlineSuffixRenderer and InlineBlockElementRenderer are supported now
 * To be expanded when the following is done:
 * RDCT-400 Support inlays via API for predefined set of popular inlay types (text, etc...)
 */

suspend fun EditorImpl.waitExpectedInlays(
  range: TextRange = ReadAction.compute<TextRange, Throwable> { TextRange(caretModel.offset, caretModel.offset + 1) },
  expectedCount: Int,
  expectedCustomString: String? = null,
  filter: (Inlay<*>) -> Boolean = { true },
) {
  waitSuspending("Waiting for amount of inlays to become right", 5.seconds,
                 getter = { inlays(range, filter) },
                 checker = { it.size == expectedCount && (expectedCustomString == null || it.customToString() == expectedCustomString) }
  )
}

fun EditorImpl.inlays(
  range: TextRange = TextRange(caretModel.offset, caretModel.offset + 1),
  filter: (Inlay<*>) -> Boolean = { true },
): List<Inlay<*>> =
  (
    inlayModel.getInlineElementsInRange(range.startOffset, range.endOffset) +
    inlayModel.getBlockElementsInRange(range.startOffset, range.endOffset)
  ).filter { filter(it) }


fun Inlay<*>.customToString(): String =
  when (val currentRenderer = renderer) {
    is InlineCompletionLineRenderer -> currentRenderer.getText()
    else -> error("Non expected inlay type")
  }

fun List<Inlay<*>>.customToString(): String = joinToString("\n") { it.customToString() }

private fun InlineCompletionLineRenderer.getText(): String = blocks.joinToString("") { it.text }

val editorsWithInlineCompletionContext
  get() = EditorFactory.getInstance().allEditors.filter {
    InlineCompletionContext.getOrNull(it) != null
  }
