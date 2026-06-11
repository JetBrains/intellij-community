// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.engine

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.webview.api.WebViewApiId
import com.intellij.ui.webview.api.WebViewCallable
import com.intellij.ui.webview.api.WebViewImplementable
import com.intellij.ui.webview.api.WebViewInterop
import com.intellij.ui.webview.api.WebViewMessageRegistration
import com.intellij.util.ui.StartupUiUtil
import kotlinx.serialization.Serializable

internal fun String?.withWebViewTheme(): String {
  val themeQuery = "$WEBVIEW_THEME_QUERY_PARAMETER=${currentWebViewTheme()}"
  return if (isNullOrEmpty()) themeQuery else "$this&$themeQuery"
}

internal fun WebViewInterop.registerThemeHandler(): WebViewMessageRegistration {
  val themeEvents = callable(WebViewThemeHostEvents.ID)
  val connection = ApplicationManager.getApplication().messageBus.connect()
  val themeRequestRegistration = implement(WebViewThemePageEvents.ID, object : WebViewThemePageEvents {
    override fun themeRequest() {
      sendThemeChanged(themeEvents)
    }
  })
  connection.subscribe(LafManagerListener.TOPIC, LafManagerListener { _ ->
    sendThemeChanged(themeEvents)
  })
  return object : WebViewMessageRegistration {
    @Volatile private var closed = false

    override fun close() {
      if (closed) return
      closed = true
      themeRequestRegistration.close()
      connection.disconnect()
    }
  }
}

private fun sendThemeChanged(themeEvents: WebViewThemeHostEvents) {
  runCatching {
    themeEvents.themeChanged(WebViewThemeChangedPayload(currentWebViewTheme()))
  }
}

private fun currentWebViewTheme(): String = if (StartupUiUtil.isDarkTheme) "dark" else "light"

@Serializable
private data class WebViewThemeChangedPayload(val theme: String)

private interface WebViewThemeHostEvents : WebViewCallable {
  fun themeChanged(params: WebViewThemeChangedPayload)

  companion object {
    val ID: WebViewApiId<WebViewThemeHostEvents> = WebViewApiId.of("webview.theme")
  }
}

private interface WebViewThemePageEvents : WebViewImplementable {
  fun themeRequest()

  companion object {
    val ID: WebViewApiId<WebViewThemePageEvents> = WebViewApiId.of("webview.theme")
  }
}

private const val WEBVIEW_THEME_QUERY_PARAMETER = "__webviewTheme"
