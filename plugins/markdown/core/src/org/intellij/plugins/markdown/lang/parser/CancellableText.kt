// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.parser

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.text.CharSequenceWithStringHash
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.ApiStatus

/** Faster 20-30% than [StringUtil.BombedCharSequence] when parsing a Markdown file */
@ApiStatus.Internal
class CancellableText private constructor(private val text: String) : CharSequenceWithStringHash {
  private var counter = 0

  override val length: Int
    get() = text.length

  override fun get(index: Int): Char {
    if ((++counter and CHECK_MASK) == 0) {
      ProgressManager.checkCanceled()
    }
    return text[index]
  }

  override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
    ProgressManager.checkCanceled()
    val piece = text.substring(startIndex, endIndex)
    if (piece.length > LONG_LINE_THRESHOLD) {
      return CancellableText(piece)
    }
    return piece
  }

  override fun toString(): String = text

  override fun hashCode(): Int = text.hashCode()

  override fun equals(other: Any?): Boolean {
    return this === other || (other is CancellableText && text == other.text)
  }

  companion object {
    private const val CHECK_MASK = 1023

    /** A piece of this length or shorter becomes a plain `String`. A longer piece keeps a cancellable wrapper. */
    const val LONG_LINE_THRESHOLD: Int = 10_000

    /** Flattens [text] into a `String` once, then wraps it. Returns [text] itself when it is already a [CancellableText]. */
    @JvmStatic
    fun of(text: CharSequence): CharSequence {
      return text as? CancellableText ?: CancellableText(text as? String ?: text.toString())
    }
  }
}
