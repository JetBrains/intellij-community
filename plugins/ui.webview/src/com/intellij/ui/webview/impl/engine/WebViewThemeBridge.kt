// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.engine

import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.ui.webview.api.WebViewApiId
import com.intellij.ui.webview.api.WebViewCallable
import com.intellij.ui.webview.api.WebViewImplementable
import com.intellij.ui.webview.api.WebViewInterop
import com.intellij.ui.webview.api.WebViewMessageRegistration
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import kotlinx.serialization.Serializable
import javax.swing.UIManager
import kotlin.math.ceil

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
  connection.subscribe(UISettingsListener.TOPIC, UISettingsListener { _ ->
    sendThemeChanged(themeEvents)
  })
  connection.subscribe(EditorColorsManager.TOPIC, EditorColorsListener { _ ->
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
    themeEvents.themeChanged(WebViewThemeChangedPayload(currentWebViewTheme(), currentWebViewFonts()))
  }
}

private fun currentWebViewTheme(): String = if (StartupUiUtil.isDarkTheme) "dark" else "light"

private fun currentWebViewFonts(): WebViewThemeFontsPayload {
  return WebViewThemeFontsPayload(
    ui = currentUiFont(),
    editor = currentEditorFont(),
  )
}

private fun currentUiFont(): WebViewThemeFontInfoPayload {
  val regularFont = JBFont.regular()
  val font = UIManager.getFont("Label.font") ?: regularFont
  val size = regularFont.size2D
  return WebViewThemeFontInfoPayload(
    families = bundledFontFamilyAliases(listOf(font.family, font.name, font.fontName)),
    size = size,
    lineHeight = ceil(size + UI_FONT_LINE_HEIGHT_EXTRA),
    sizes = currentUiFontSizes(),
  )
}

private fun currentUiFontSizes(): WebViewThemeFontSizesPayload {
  return WebViewThemeFontSizesPayload(
    h0 = JBFont.h0().size2D,
    h1 = JBFont.h1().size2D,
    h2 = JBFont.h2().size2D,
    h3 = JBFont.h3().size2D,
    h4 = JBFont.h4().size2D,
    regular = JBFont.regular().size2D,
    medium = JBFont.medium().size2D,
    small = JBFont.small().size2D,
    mini = JBUI.Fonts.miniFont().size2D,
  )
}

private fun currentEditorFont(): WebViewThemeEditorFontInfoPayload {
  val scheme = EditorColorsManager.getInstance().globalScheme
  val preferences = scheme.fontPreferences
  val families = bundledFontFamilyAliases(preferences.effectiveFontFamilies.ifEmpty { listOf(scheme.editorFontName) })
  val primaryFamily = families.firstOrNull() ?: scheme.editorFontName
  val size = if (preferences.hasSize(primaryFamily)) preferences.getSize2D(primaryFamily) else scheme.editorFontSize2D
  return WebViewThemeEditorFontInfoPayload(
    families = families,
    size = size,
    lineHeight = scheme.lineSpacing,
    ligatures = scheme.isUseLigatures,
    fontFeatureSettings = preferences.characterVariants.sorted(),
  )
}

private fun bundledFontFamilyAliases(families: Iterable<String>): List<String> {
  val result = LinkedHashSet<String>()
  for (family in families) {
    val normalized = family.trim()
    if (normalized.isEmpty()) continue
    bundledFontFamilyAlias(normalized)?.let { result.add(it) }
    result.add(normalized)
  }
  return result.toList()
}

private fun bundledFontFamilyAlias(family: String): String? {
  return when {
    family == INTER_FONT_FAMILY || family.startsWith("$INTER_FONT_FAMILY ") -> INTER_FONT_FAMILY
    family == JETBRAINS_MONO_FONT_FAMILY || family.startsWith("$JETBRAINS_MONO_FONT_FAMILY ") -> JETBRAINS_MONO_FONT_FAMILY
    else -> null
  }
}

@Serializable
private data class WebViewThemeChangedPayload(
  val theme: String,
  val fonts: WebViewThemeFontsPayload,
)

@Serializable
private data class WebViewThemeFontsPayload(
  val ui: WebViewThemeFontInfoPayload,
  val editor: WebViewThemeEditorFontInfoPayload,
)

@Serializable
private data class WebViewThemeFontInfoPayload(
  val families: List<String>,
  val size: Float,
  val lineHeight: Float,
  val sizes: WebViewThemeFontSizesPayload,
)

@Serializable
private data class WebViewThemeFontSizesPayload(
  val h0: Float,
  val h1: Float,
  val h2: Float,
  val h3: Float,
  val h4: Float,
  val regular: Float,
  val medium: Float,
  val small: Float,
  val mini: Float,
)

@Serializable
private data class WebViewThemeEditorFontInfoPayload(
  val families: List<String>,
  val size: Float,
  val lineHeight: Float,
  val ligatures: Boolean,
  val fontFeatureSettings: List<String>,
)

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
private const val UI_FONT_LINE_HEIGHT_EXTRA = 3f
private const val INTER_FONT_FAMILY = "Inter"
private const val JETBRAINS_MONO_FONT_FAMILY = "JetBrains Mono"
