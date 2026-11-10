// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import java.io.BufferedReader
import java.io.IOException
import java.io.Reader
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.parser.ParserDelegator
import kotlin.coroutines.CoroutineContext

internal class CancelableParserDelegator(private val coroutineContext: CoroutineContext) : ParserDelegator() {
  override fun parse(r: Reader?, cb: HTMLEditorKit.ParserCallback?, ignoreCharSet: Boolean) {
    try {
      r?.also { reader ->
        super.parse(CancelableReader(reader, coroutineContext), cb, ignoreCharSet)
      }
    }
    catch (e: CancelableReader.IOCancellationException) {
      throw e.cancellationException
    }
  }

  private class CancelableReader(
    reader: Reader,
    private val coroutineContext: CoroutineContext,
  ) : BufferedReader(reader) {
    override fun read(): Int {
      checkActive()
      return super.read()
    }

    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
      checkActive()
      return super.read(cbuf, off, len)
    }

    private fun checkActive() {
      try {
        coroutineContext.ensureActive()
      }
      catch (e: CancellationException) {
        throw IOCancellationException(e)
      }
    }

    /**
     * [ParserDelegator] swallows non-IOException errors, so we need to use this wrapper to catch and rethrow cancellation as necessary.
     */
    class IOCancellationException(val cancellationException: CancellationException) : IOException()
  }
}
