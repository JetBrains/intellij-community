// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview.jcef

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.jcef.JBCefBrowser
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.handler.CefDisplayHandlerAdapter
import org.intellij.lang.annotations.Language
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.html.HtmlGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.intellij.plugins.markdown.ui.preview.html.DefaultCodeFenceGeneratingProvider
import org.intellij.plugins.markdown.ui.preview.jcef.impl.executeJavaScript
import org.intellij.plugins.markdown.ui.preview.jcef.impl.waitForPageLoad
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random

@Service(Service.Level.APP)
class CodeFenceParsingServiceImpl(cs: CoroutineScope? = null) : CodeFenceParsingService {
  private var MAX_HIGHLIGHT_JS_LOAD_TIME = 5000L // msec
  private var MAX_HIGHLIGHT_JS_WAIT_TIME = 2000L // msec
  private var MAX_ALLOWED_STARTUP_TIME = 10000L // msec

  @Volatile private var started = false
  @Volatile private var startupCompleted = false
  @Volatile private var available = true
  @Volatile private var startupLatch: CountDownLatch? = CountDownLatch(1)
  @Volatile private var jsExecLatch: CountDownLatch? = null
  @Volatile private var jsResultLatch: CountDownLatch? = null
  @Volatile private var jsResult: String? = null
  @Volatile private var jsError = false
  @Volatile private var unknownLanguage = false

  private var browser: JBCefBrowser? = null

  init {
    if (cs != null) {
      cs.launch { codeFenceParsingStartUp() }
    }
    else {
      val scope = object : CoroutineScope { override val coroutineContext: CoroutineContext = Job() + Dispatchers.IO }

      scope.launch { codeFenceParsingStartUp() }
    }
  }

  private suspend fun codeFenceParsingStartUp() {
    if (started)
      return

    started = true
    browser = object : JBCefBrowser() {
      init {
        jbCefClient.addDisplayHandler(object: CefDisplayHandlerAdapter() {
          override fun onConsoleMessage(browser: CefBrowser, level: CefSettings.LogSeverity, message: String, source: String, line: Int): Boolean {
            if (level == CefSettings.LogSeverity.LOGSEVERITY_INFO && message.startsWith("highlighter:")) {
              jsExecLatch?.await()
              jsResult = message.substring(12)
              jsResultLatch?.countDown()
            }
            else if (level == CefSettings.LogSeverity.LOGSEVERITY_ERROR) {
              if (message.contains("Could not find the language")) {
                unknownLanguage = true
              }
              else {
                LOG.error("Error while executing highlighter: $message")
                jsError = true
              }
            }

            return true // Prevent creation of jcef_*.log files
          }
        }, cefBrowser)
      }
    }

    var highlightJS: String? = null
    val scriptLatch = CountDownLatch(1)

    Thread {
      try {
        highlightJS = highlighterCache.readCache()
      }
      catch (_: Exception) {}

      if (highlightJS == null) {
         try {
          highlightJS = ::CodeFenceParsingServiceImpl.javaClass
            .getResourceAsStream("highlighter.js")?.bufferedReader().use { it?.readText() }
          LOG.info("Using bundled highlight.js code.")
        }
        catch (_: Exception) {}
      }

      scriptLatch.countDown()
    }.start()

    Thread {
      // It takes to long during initialization to wait for the latest highlight.js version
      //   to load, so get it now if possible and use it the next time.
      try {
        val latestJS = URL("https://unpkg.com/@highlightjs/cdn-assets/highlight.min.js").readText()
        highlighterCache.writeCache(latestJS)
      }
      catch (_: Exception) {
        LOG.warn("Failed to cache latest highlight.js code.")
      }
    }.start()

    scriptLatch.await(MAX_HIGHLIGHT_JS_LOAD_TIME, TimeUnit.MILLISECONDS)

    if (highlightJS == null) {
      LOG.error("Failed to load highlight.js code.")
      available = false
      return
    }

    // LOG.info("Using ${if (highlightJS == latestJS) "latest" else "built-in"} highlight.js.")

    @Suppress("JSUnresolvedReference")
    @Language("JavaScript")
    val jsExtra = """
      |function markdownHighlighter(language, content) {
      |  let html = '';
      |
      |  if (language === 'json5')
      |    language = 'javascript';
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

    browser!!.createImmediately()
    LOG.info("CodeFenceParsing browser created")
    browser!!.waitForPageLoad("about:blank")
    LOG.info("CodeFenceParsing browser loaded")
    browser!!.executeJavaScript(highlightJS!!)
    browser!!.executeJavaScript(jsExtra)
    startupCompleted = true
    startupLatch?.countDown()
    LOG.info("CodeFenceParsing browser set--up completed")
  }

  override fun altHighlighterAvailable() = available

  @Suppress("PrivatePropertyName")
  private val md_src_pos = HtmlGenerator.SRC_ATTRIBUTE_NAME

  private fun parseSegment(language: String, content: String): String? {
    jsExecLatch = CountDownLatch(1)

    val execThread = Thread {
      jsError = false
      unknownLanguage = false
      @Suppress("JSUnresolvedReference")
      browser!!.executeJavaScript("markdownHighlighter('${escapeForJs(language)}', '${escapeForJs(content)}')")
      jsExecLatch!!.countDown()
      jsExecLatch = null
      jsResultLatch = CountDownLatch(1)
      jsResultLatch!!.await(MAX_HIGHLIGHT_JS_WAIT_TIME, TimeUnit.MILLISECONDS)
    }

    execThread.start()
    execThread.join(MAX_HIGHLIGHT_JS_WAIT_TIME)

    if (unknownLanguage || jsError || jsResult.isNullOrEmpty()) {
      return null
    }
    else {
      return jsResult
    }
  }

  private val scriptFinder = Regex("""(<script[^>]*>)(.*?)(</\s*script\s*>)""",
                                   setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

  private fun parseMixedHtmlAndJavaScript(content: String): String? {
    var scriptIndex = 0
    val javaScript = HashMap<String, String>()
    val html = scriptFinder.replace(content) { match ->
      val js = match.groups[2]?.value ?: ""

      if (js.trim() == "")
        match.groups[0]!!.value
      else {
        val key = "$•••${++scriptIndex}•${Random.nextInt(0, Int.MAX_VALUE)}•••$"

        javaScript[key] = parseSegment("javascript", js) ?: "<span>${encodeEntities(js)}</span>"
        match.groups[1]!!.value + key + match.groups[3]!!.value
      }
    }
    var markedUpHtml = parseSegment("html", html) ?: return null

    javaScript.forEach { (key, value) -> markedUpHtml = markedUpHtml.replace(key, "<span class=\"language-javascript\">$value</span>") }

    return markedUpHtml
  }

  private val scriptTag = Regex("""<\s*script[^>]*>""", RegexOption.IGNORE_CASE)

  @Synchronized
  override fun parseToHighlightedHtml(language: String, content: String, node: ASTNode): String? {
    startupLatch?.await(MAX_ALLOWED_STARTUP_TIME, TimeUnit.MILLISECONDS)
    startupLatch = null

    if (!startupCompleted) {
      LOG.error("Startup timed out")
      available = false
      return null
    }

    var parsed: String

    if (language != "html" || !scriptTag.containsMatchIn(content)) {
      parsed = parseSegment(language, content) ?: return null
    }
    else {
      parsed = parseMixedHtmlAndJavaScript(content) ?: return null
    }

    val startOffset = DefaultCodeFenceGeneratingProvider.calculateCodeFenceContentBaseOffset(node)
    var html = convertToRangedSpans(parsed, startOffset)

    if (node.startOffset < startOffset) {
      // Needed to match code fence start, even though the fence start text itself isn't inside the span.
      html = "<span $md_src_pos=\"${node.startOffset}..$startOffset\"></span>" + html
    }

    if (node.endOffset > startOffset + content.length) {
      // Needed to match code fence end, even though the fence end text itself isn't inside the span.
      html += "<span $md_src_pos=\"${startOffset + content.length}..${node.endOffset}\"></span>"
    }

    return html
  }

  companion object {
    private val highlighterCache = HighlighterCache()
    private val LOG = logger<CodeFenceParsingService>()

    private val entitiesMap = mapOf("amp" to "&", "gt" to ">", "lt" to "<", "quot" to "\"")
    private val entities = Regex("&(amp|gt|lt|quot);")

    private fun decodeEntities(text: String): String {
      return text.replace(entities) { matchResult -> entitiesMap[matchResult.groupValues[1]] ?: "" }
    }

    private val entityCandidatesMap = mapOf("&" to "&amp;", ">" to "&gt;", "<" to "&lt;", "\"" to "&quot;")
    private val entityCandidates = Regex("[&<>\"']")

    private fun encodeEntities(text: String): String {
      return text.replace(entityCandidates) { matchResult -> entityCandidatesMap[matchResult.value] ?: "" }
    }

    private val escapees: Map<Char, String> =
      mapOf('\\' to "\\\\",  '\'' to "\\'", '"' to "\\\"", '\n' to "\\n", '\r' to "\\r", '\t' to "\\t")
    private val basicEscapes = Regex("""([\\'"\n\r])""")
    private val controlEscapes = Regex("""([\x00-\x1F])""")

    private fun escapeForJs(text: String): String {
      return text.replace(basicEscapes) { match -> escapees[match.groupValues[1][0]].orEmpty() }
        .replace(controlEscapes) { match -> "\\x" + match.groupValues[1][0].code.toString(16).padStart(2, '0') }
    }

    private data class TagInfo(val line: Int, val offset: Int)

    private val tagStart = Regex("^(<\\w+)")

    private fun addSourceRange(tag: String, start: Int, end: Int): String {
      return tag.replace(tagStart) { match -> "${match.value} md-src-pos=\"$start..$end\"" }
    }

    private val tagAndLineBreaks = Regex("""(?<=(>|\r\n|\r|\n))|(?=(<|\r\n|\r|\n))""")

    private fun convertToRangedSpans(html: String, startOffset: Int): String {
      val chunks = html.split(tagAndLineBreaks).toTypedArray()
      val nesting = ArrayDeque<TagInfo>()
      var offset = startOffset
      var justStartedTag = false

      for (i in chunks.indices) {
        val chunk = chunks[i]

        if (chunk.startsWith("</")) {
          val match = nesting.removeLastOrNull()

          if (match != null) {
            chunks[match.line] = addSourceRange(chunks[match.line], match.offset, offset)
          }

          justStartedTag = false
        }
        else if (chunk.startsWith("<")) {
          nesting.addLast(TagInfo(i, offset))
          justStartedTag = true
        }
        else if (chunk.isNotEmpty()) {
          val decodedChunk = decodeEntities(chunk)

          if (!justStartedTag) {
            // highlight.js doesn't wrap all text in span tags, but the preview display works better that way.
            chunks[i] = addSourceRange("<span>$chunk</span>", offset, offset + decodedChunk.length)
          }

          offset += decodedChunk.length
          justStartedTag = false
        }
      }

      return chunks.joinToString("")
    }
  }
}
