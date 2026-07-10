// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.jcef

import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.webview.impl.WebViewAssetResponse
import com.intellij.ui.webview.impl.traceWebViewPerf
import com.intellij.ui.webview.impl.traceWebViewPerfSince
import org.cef.callback.CefCallback
import org.cef.handler.CefResourceHandlerAdapter
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import kotlin.time.TimeSource

private val LOG = logger<JcefBytesResourceHandler>()

internal class JcefBytesResourceHandler(
  private val responseData: WebViewAssetResponse,
) : CefResourceHandlerAdapter() {
  private val handlerCreatedAt = TimeSource.Monotonic.markNow()
  private var offset = 0
  private var chunkCount = 0
  private var totalBytesRead = 0

  override fun processRequest(request: CefRequest, callback: CefCallback): Boolean {
    return LOG.traceWebViewPerf("webview.asset.jcef.processRequest", responseData.diagnosticDetails("jcef")) {
      callback.Continue()
      true
    }
  }

  override fun getResponseHeaders(response: CefResponse, responseLength: IntRef, redirectUrl: StringRef) {
    LOG.traceWebViewPerf("webview.asset.jcef.headers", responseData.diagnosticDetails("jcef")) {
      response.status = responseData.statusCode
      response.statusText = responseData.statusText
      response.mimeType = responseData.mimeType
      response.setHeaderByName("Content-Type", responseData.contentType, true)
      for ((name, value) in responseData.headers) {
        response.setHeaderByName(name, value, true)
      }
      responseLength.set(responseData.bytes.size)
    }
  }

  override fun readResponse(dataOut: ByteArray, bytesToRead: Int, bytesRead: IntRef, callback: CefCallback): Boolean {
    val bytes = responseData.bytes
    val remaining = bytes.size - offset
    if (remaining <= 0) {
      bytesRead.set(0)
      return false
    }

    val count = minOf(bytesToRead, remaining)
    bytes.copyInto(dataOut, destinationOffset = 0, startIndex = offset, endIndex = offset + count)
    offset += count
    chunkCount++
    totalBytesRead += count
    bytesRead.set(count)
    if (offset == bytes.size) {
      logReadComplete("eof")
    }
    return true
  }

  override fun cancel() {
    if (offset < responseData.bytes.size) {
      logReadComplete("cancel")
    }
    offset = responseData.bytes.size
  }

  private fun logReadComplete(reason: String) {
    LOG.traceWebViewPerfSince(
      "webview.asset.jcef.readResponse.total",
      handlerCreatedAt,
      responseData.diagnosticDetails("jcef", "chunks=$chunkCount, bytesRead=$totalBytesRead, reason=$reason"),
    )
  }
}
