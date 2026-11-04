package org.intellij.plugins.markdown.extensions.common.highlighter

import com.intellij.diagnostic.LoadingState
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.util.application
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil
import java.lang.ref.SoftReference
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.APP)
internal class HtmlCacheManager {
  private class CachedRanges(val ranges: SoftReference<List<HighlightedRange>>, var expires: Long)

  private val values = ConcurrentHashMap<String, CachedRanges>()

  fun obtainCacheKey(content: String, language: String): String {
    return MarkdownUtil.md5(content, language)
  }

  fun obtainCachedRanges(key: String): List<HighlightedRange>? {
    val entry = values[key]
    val ranges = entry?.ranges?.get()
    if (ranges != null) {
      entry.expires += expiration
      return ranges
    }
    cleanup()
    return null
  }

  fun cacheRanges(key: String, ranges: List<HighlightedRange>) {
    val expires = System.currentTimeMillis() + expiration
    values[key] = CachedRanges(SoftReference(ranges), expires)
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
    }
  }

  companion object {
    private const val expiration = 5 * 60 * 1000

    fun getInstance(): HtmlCacheManager {
      return service()
    }
  }
}
