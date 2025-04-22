// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.extensions.common.highlighter

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.ui.jcef.JBCefBrowser
import java.util.concurrent.CountDownLatch
import org.cef.CefSettings.LogSeverity
import org.cef.browser.CefBrowser
import org.cef.handler.CefDisplayHandlerAdapter
import org.intellij.lang.annotations.Language
import org.intellij.plugins.markdown.ui.preview.jcef.impl.executeJavaScript
import org.intellij.plugins.markdown.ui.preview.jcef.impl.waitForPageLoad
import java.net.URL
import java.util.concurrent.TimeUnit

internal class CodeFenceLanguageParsingSupport : ProjectActivity {
  override suspend fun execute(project: Project) {
    val highlightJS = URL("https://unpkg.com/@highlightjs/cdn-assets/highlight.min.js").readText()
    @Suppress("JSUnresolvedReference")
    @Language("JavaScript")
    val jsExtra = """
      |function markdownHighlighter(language, content) {
      |  let html = '';
      |
      |  if (language === 'json5')
      |    language = 'jsonc';
      |
      |  if (language) {
      |    try {
      |      html = hljs.highlight(content, { language }).value;
      |    }
      |    catch {}
      |  }
      |
      |  if (!html) {
      |    try {
      |      html = hljs.highlightAuto(content).value;
      |    }
      |    catch {}
      |  }
      |
      |  console.info('highlighter:' + html);
      |}
      """.trimMargin()

    browser.createImmediately()
    browser.waitForPageLoad("about:blank")
    browser.executeJavaScript(highlightJS)
    browser.executeJavaScript(jsExtra)
    startupCompleted = true
    startupLatch?.countDown()
  }

  companion object {
    @Volatile private var startupCompleted = false
    @Volatile private var startupLatch: CountDownLatch? = CountDownLatch(1)
    @Volatile private var jsExecLatch: CountDownLatch? = null
    @Volatile private var jsResultLatch: CountDownLatch? = null
    @Volatile private var jsResult: String? = null
    @Volatile private var jsError = false

    private val browser = object : JBCefBrowser() {
      init {
        jbCefClient.addDisplayHandler(object: CefDisplayHandlerAdapter() {
          override fun onConsoleMessage(browser: CefBrowser, level: LogSeverity, message: String, source: String, line: Int): Boolean {
            if (level == LogSeverity.LOGSEVERITY_INFO && message.startsWith("highlighter:")) {
              jsExecLatch?.await()
              jsResult = message.substring(12)
              jsResultLatch?.countDown()
              return true
            }
            else if (level == LogSeverity.LOGSEVERITY_ERROR) {
              System.err.println("Error while executing highlighter: $message")
              jsError = true
            }

            return false
          }
        }, cefBrowser)
      }
    }

    private val entities = mapOf("amp" to "&", "gt" to ">", "lt" to "<", "quot" to "\"")

    private fun decodeEntities(text: String): String {
      return text.replace(Regex("&(amp|gt|lt|quot);")) { matchResult -> entities[matchResult.groupValues[1]] ?: "" }
    }

    private val escapees: Map<Char, String> =
      mapOf('\\' to "\\\\",  '\'' to "\\'", '"' to "\\\"", '\n' to "\\n", '\r' to "\\r", '\t' to "\\t")

    private fun escapeForJs(text: String): String {
      return text.replace(Regex("""([\\'"\n\r])""")) { match -> escapees[match.groupValues[1][0]].orEmpty() }
        .replace(Regex("""([\x00-\x1F])""")) { match -> "\\x" + match.groupValues[1][0].code.toString(16).padStart(2, '0') }
    }

    private data class TagInfo(val line: Int, val offset: Int)

    private fun addSourceRange(tag: String, start: Int, end: Int): String {
      return tag.replace(Regex("^(<\\w+)")) { match -> "${match.value} md-src-pos=\"$start..$end\"" }
    }

    private fun convertToRangedSpans(html: String, startOffset: Int): String {
      val chunks = html.split(Regex("""(?<=(>|\r\n|\r|\n))|(?=<)""")).toTypedArray()
      val nesting = ArrayDeque<TagInfo>()
      var offset = startOffset
      var wasNewLine = false

      for (i in chunks.indices) {
        val chunk = chunks[i]
        val isNewLine = Regex("[\\n\\r]").containsMatchIn(chunk) || i == chunk.lastIndex

        if (chunk.startsWith("</")) {
          val match = nesting.removeLastOrNull()

          if (match != null) {
            chunks[match.line] = addSourceRange(chunks[match.line], match.offset, offset)
          }
        }
        else if (chunk.startsWith("<")) {
          nesting.addLast(TagInfo(i, offset))
        }
        else {
          val decodedChunk = decodeEntities(chunk)

          if (nesting.isNotEmpty() && !isNewLine && !wasNewLine) {
            offset += decodedChunk.length
          }
          else if (chunk.isNotEmpty()) {
            chunks[i] = addSourceRange("<span>$chunk</span>", offset, offset + decodedChunk.length)
            offset += decodedChunk.length
          }
        }

        wasNewLine = isNewLine
      }

      return chunks.joinToString("")
    }

    internal fun parseToHighlightedHtml(language: String, content: String, startOffset: Int): String? {
      synchronized(browser) {
        startupLatch?.await(10, TimeUnit.SECONDS)
        startupLatch = null

        if (!startupCompleted) {
          return null
        }

        jsExecLatch = CountDownLatch(1)

        val execThread = Thread {
          jsError = false
          @Suppress("JSUnresolvedReference")
          browser.executeJavaScript("markdownHighlighter('${escapeForJs(language)}', '${escapeForJs(content)}')")
          jsExecLatch!!.countDown()
          jsExecLatch = null
          jsResultLatch = CountDownLatch(1)
          jsResultLatch!!.await(2, TimeUnit.SECONDS)
        }

        execThread.start()
        execThread.join(2000)

        if (jsError || jsResult == null) {
          return null
        }

        return convertToRangedSpans(jsResult!!, startOffset)
      }
    }
  }
}
