package com.intellij.mermaid.jcef

import com.intellij.mermaid.api.Mermaid
import com.intellij.mermaid.api.renderAsync
import com.intellij.mermaid.jcef.impl.decode
import kotlinx.browser.window
import kotlinx.coroutines.async
import kotlinx.coroutines.await
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.w3c.dom.HTMLElement
import org.w3c.dom.parsing.XMLSerializer

suspend fun renderBlock(block: HTMLElement, cacheId: String, content: String): String? {
  val id = "mermaid-generated-$cacheId"
  val promise = Mermaid.api.renderAsync(id, content, block) { svg ->
    block.innerHTML = svg
  }
  return promise.await()
}

fun cacheBlock(cacheId: String, block: HTMLElement) {
  val serializer = XMLSerializer()
  val serialized = serializer.serializeToString(block)
  val data = "$cacheId;$serialized"
  window.obtainMessagePipe()?.post("storeMermaidFile", data)
}

suspend fun processBlock(block: HTMLElement): String? {
  block.removeAttribute("data-processed")
  val cacheId = block.getAttribute("data-cache-id")
  checkNotNull(cacheId) { "data-cache-id was not set for block" }
  val content = block.getAttribute("data-actual-fence-content")
  checkNotNull(content) { "data-actual-fence-content was not set for block" }
  val actualContent = decode(content)
  // TODO: Validate result
  val generated = renderBlock(block, cacheId, actualContent) ?: return null
  cacheBlock(cacheId, block)
  return generated
}

suspend fun renderBlocks(blocks: List<HTMLElement>) {
  coroutineScope {
    val jobs = blocks.map { async { processBlock(it) } }
    jobs.awaitAll()
  }
}
