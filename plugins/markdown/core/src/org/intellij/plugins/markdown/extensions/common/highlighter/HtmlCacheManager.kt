package org.intellij.plugins.markdown.extensions.common.highlighter

import com.intellij.diagnostic.LoadingState
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.processOpenedProjects
import com.intellij.util.application
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil
import java.lang.ref.SoftReference
import java.util.concurrent.ConcurrentHashMap

@Service
internal class HtmlCacheManager {
  private data class CachedHtmlResult(val html: SoftReference<String>, var expires: Long) {
    data class HtmlResult(val html: String, val expires: Long)

    fun resolve(): HtmlResult? {
      return html.get()?.let { HtmlResult(it, expires) }
    }
  }

  private val values = ConcurrentHashMap<String, CachedHtmlResult>()

  fun obtainCacheKey(content: String, language: String): String {
    return MarkdownUtil.md5(content, language)
  }

  fun obtainCachedHtml(key: String): String? {
    val entry = values[key]
    val resolved = entry?.resolve()
    if (resolved != null) {
      entry.expires += expiration
      return resolved.html
    }
    cleanup()
    return null
  }

  fun cacheHtml(key: String, html: String) {
    val expires = System.currentTimeMillis() + expiration
    values[key] = CachedHtmlResult(SoftReference(html), expires)
  }

  fun cleanup() {
    val time = System.currentTimeMillis()
    val expired = values.filter { it.value.expires < time }.keys
    for (key in expired) {
      values.remove(key)
    }
  }

  fun invalidate() {
    values.clear()
  }

  internal class InvalidateHtmlCacheLafListener: LafManagerListener {
    override fun lookAndFeelChanged(source: LafManager) {
      if (!LoadingState.APP_STARTED.isOccurred) {
        return
      }
      application.serviceIfCreated<HtmlCacheManager>()?.invalidate()
      processOpenedProjects { project ->
        project.serviceIfCreated<HtmlCacheManager>()?.invalidate()
      }
    }
  }

  companion object {
    private const val expiration = 5 * 60 * 1000

    fun getInstance(project: Project? = null): HtmlCacheManager {
      return when (project) {
        null -> service()
        else -> project.service()
      }
    }
  }
}
