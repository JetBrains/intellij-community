// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.jcef

import com.intellij.ui.webview.impl.WebViewAssetResponse
import org.cef.callback.CefCallback
import org.cef.handler.CefResourceHandlerAdapter
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefRequest
import org.cef.network.CefResponse

internal class JcefBytesResourceHandler(
  private val responseData: WebViewAssetResponse,
) : CefResourceHandlerAdapter() {
  private var offset = 0

  override fun processRequest(request: CefRequest, callback: CefCallback): Boolean {
    callback.Continue()
    return true
  }

  override fun getResponseHeaders(response: CefResponse, responseLength: IntRef, redirectUrl: StringRef) {
    response.status = responseData.statusCode
    response.statusText = responseData.statusText
    response.mimeType = responseData.mimeType
    response.setHeaderByName("Content-Type", responseData.contentType, true)
    for ((name, value) in responseData.headers) {
      response.setHeaderByName(name, value, true)
    }
    responseLength.set(responseData.bytes.size)
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
    bytesRead.set(count)
    return true
  }

  override fun cancel() {
    offset = responseData.bytes.size
  }
}
