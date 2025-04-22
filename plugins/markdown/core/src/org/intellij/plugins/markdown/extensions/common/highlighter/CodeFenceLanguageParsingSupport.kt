// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.extensions.common.highlighter

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.ui.jcef.JBCefBrowser
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.handler.CefDisplayHandlerAdapter
import org.intellij.plugins.markdown.ui.preview.jcef.impl.executeJavaScript

internal class MarkdownApplicationInitListener : ProjectActivity {
  override suspend fun execute(project: Project) {
    browser.jbCefClient.addDisplayHandler(object: CefDisplayHandlerAdapter() {
      override fun onConsoleMessage(
        browser: CefBrowser, level: CefSettings.LogSeverity,
        message: String, source: String, line: Int,
      ): Boolean {
        println(message)
        return false
      }
    }, browser.cefBrowser)

    browser.executeJavaScript("window.console.info('Hello, world.')")
  }

  companion object {
    var browser = JBCefBrowser("about:blank")
    var codeFenceId: Long = 0

    private val replacements: Map<Char, String> = mapOf('&' to "@amp;", '>' to "&gt;", '<' to "&lt;", '"' to "&quot;")

    private fun encodeEntities(text: String): String {
      return text.replace(Regex("""([&><"])""")) { match -> replacements[match.groupValues[1][0]].orEmpty() }
    }

    internal fun parseToHTML(language: String, content: String): String? {
      val codeLines = content.split(Regex("\r\n|\r|\n")).map { "<span>${encodeEntities(it)}</span>" }

      return """<div id="cfid-${++codeFenceId}" class="language-$language code-fence">${codeLines.joinToString("\n")}</div>"""
    }
  }
}
