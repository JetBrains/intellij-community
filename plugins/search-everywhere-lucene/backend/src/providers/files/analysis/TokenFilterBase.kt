package com.intellij.searchEverywhereLucene.backend.providers.files.analysis

import org.apache.lucene.analysis.TokenFilter
import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute

abstract class TokenFilterBase(input: TokenStream) : TokenFilter(input) {

  protected val termAttr: CharTermAttribute = addAttribute(CharTermAttribute::class.java)
  protected val multiTypeAttr: MultiTypeAttribute = addAttribute(MultiTypeAttribute::class.java)
  protected val offsetAttr: OffsetAttribute = addAttribute(OffsetAttribute::class.java)

  protected data class BufferedToken(
    val term: String,
    val types: Set<FileTokenType>,
    val startOffset: Int,
    val endOffset: Int,
  )

  protected val pending: ArrayDeque<BufferedToken> = ArrayDeque()
  private val bufferedParts = mutableListOf<BufferedToken>()

  protected fun emit(token: BufferedToken) {
    termAttr.setEmpty().append(token.term)
    multiTypeAttr.clearTypes().setTypes(token.types)
    offsetAttr.setOffset(token.startOffset, token.endOffset)
  }

  /**
   * Reusable grouping-and-flush loop for filters that accumulate source tokens
   * and emit derived tokens on flush (e.g. AbbreviationTokenFilter).
   * Call this as the body of incrementToken().
   *
   * [transformPassthrough] is applied to the term string of every passthrough token
   * (both source tokens re-emitted after flush and non-source tokens that arrive
   * while buffered parts are pending). Defaults to identity (no transformation).
   */
  protected fun incrementWithGrouping(
    sourceTypes: Set<FileTokenType>,
    passThrough: PassthroughOptions,
    processBuffered: (List<BufferedToken>) -> List<BufferedToken>,
    transformPassthrough: (String) -> String = { it },
  ): Boolean {
    while (true) {
      if (pending.isNotEmpty()) {
        emit(pending.removeFirst())
        return true
      }

      if (!input.incrementToken()) {
        if (bufferedParts.isNotEmpty()) {
          flushBuffered(passThrough, processBuffered, transformPassthrough)
          continue
        }
        return false
      }

      val activeTypes = multiTypeAttr.activeTypes().toSet()

      if (activeTypes.intersect(sourceTypes).isNotEmpty()) {
        bufferedParts.add(BufferedToken(termAttr.toString(), activeTypes, offsetAttr.startOffset(), offsetAttr.endOffset()))
        continue
      }

      val incoming = BufferedToken(termAttr.toString(), activeTypes, offsetAttr.startOffset(), offsetAttr.endOffset())
      if (bufferedParts.isNotEmpty()) {
        flushBuffered(passThrough, processBuffered, transformPassthrough)
        pending.add(incoming.copy(term = transformPassthrough(incoming.term)))
        continue
      }

      // Forward unchanged
      return true
    }
  }

  private fun flushBuffered(
    passThrough: PassthroughOptions,
    processBuffered: (List<BufferedToken>) -> List<BufferedToken>,
    transformPassthrough: (String) -> String,
  ) {
    val derived = processBuffered(bufferedParts)
    when (passThrough) {
      PassthroughOptions.PassthroughLast -> {
        pending.addAll(derived)
        bufferedParts.mapTo(pending) { it.copy(term = transformPassthrough(it.term)) }
      }
      PassthroughOptions.PassthroughFirst -> {
        bufferedParts.mapTo(pending) { it.copy(term = transformPassthrough(it.term)) }
        pending.addAll(derived)
      }
      PassthroughOptions.NoPassthrough -> {
        pending.addAll(derived)
      }
    }

    bufferedParts.clear()
  }

  override fun reset() {
    super.reset()
    bufferedParts.clear()
    pending.clear()
  }

}
