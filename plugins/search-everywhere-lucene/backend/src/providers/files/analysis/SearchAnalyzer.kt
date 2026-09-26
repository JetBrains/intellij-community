package com.intellij.searchEverywhereLucene.backend.providers.files.analysis

import com.intellij.searchEverywhereLucene.backend.providers.files.analysis.splitting.PathSplittingRule
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.TokenFilter
import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.core.WhitespaceTokenizer
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute
import org.apache.lucene.util.Attribute
import org.apache.lucene.util.AttributeImpl
import org.apache.lucene.util.AttributeReflector


class FileSearchAnalyzer : Analyzer() {
  override fun createComponents(fieldName: String): TokenStreamComponents {
    val tokenizer = WhitespaceTokenizer()
    var stream: TokenStream = WordIndexFilter(tokenizer)
    stream = SearchPathTypeFilter(stream)
    stream =
      WordSplittingTokenFilter(stream, setOf(FileTokenType.FILENAME), FileTokenType.FILENAME_PART, PassthroughOptions.PassthroughLast)
    stream = AbbreviationTokenFilter(
      stream,
      sourceTypes = setOf(FileTokenType.FILENAME_PART),
      outputType = FileTokenType.FILENAME_ABBREVIATION,
      passThrough = PassthroughOptions.PassthroughLast,
    )
    stream = FilenameNgramFilter(stream)
    stream = TokenMergingFilter(stream)
    return TokenStreamComponents(tokenizer, stream)
  }
}

/**
 * Converts each whitespace token into typed sub-tokens representing path components, filename, and extension.
 *
 * For each input token, emits (in order):
 *   - PATH: full token, original case
 *   - PATH_SEGMENT: each /-split component except the last (original case)
 *   - PATH_SEGMENT: last component (original case) — only for multi-component paths or extension-less single-component
 *   - FILENAME: last-component stem, original case (lowercasing deferred to AbbreviationTokenFilter)
 *   - FILETYPE: extension or hidden-file body, lowercase
 *
 * Hidden files (leading dot, e.g. ".gitignore"): the whole token is FILENAME; body after dot is FILETYPE.
 * Extension-less single-component: additionally emits PATH_SEGMENT and FILETYPE (lowercased whole term).
 * Both '/' and '\' are treated as path separators.
 */
class SearchPathTypeFilter(input: TokenStream) : TokenFilterBase(input) {
  override fun incrementToken(): Boolean {
    if (pending.isNotEmpty()) {
      emit(pending.removeFirst())
      return true
    }

    if (!input.incrementToken()) return false

    val segment = termAttr.toString()
    val segmentStart = offsetAttr.startOffset()
    val segmentEnd = offsetAttr.endOffset()

    pending.add(BufferedToken(segment, setOf(FileTokenType.PATH), segmentStart, segmentEnd))

    // Normalize '\' to '/' so PathSplittingRule (which only splits on '/') handles both separators.
    // Character positions are unchanged since both are single chars.
    val normalizedSegment = segment.replace('\\', '/')
    val pathSpans = PathSplittingRule(normalizedSegment).split().toList()

    for (i in 0 until pathSpans.size - 1) {
      val span = pathSpans[i]
      pending.add(BufferedToken(
        segment.substring(span.first, span.last + 1),
        setOf(FileTokenType.PATH_SEGMENT),
        segmentStart + span.first,
        segmentStart + span.last + 1,
      ))
    }

    if (pathSpans.isEmpty()) {
      emit(pending.removeFirst())
      return true
    }

    val lastSpan = pathSpans.last()
    val lastPart = segment.substring(lastSpan.first, lastSpan.last + 1)
    val partStart = segmentStart + lastSpan.first
    val partEnd = segmentStart + lastSpan.last + 1

    if (pathSpans.size > 1) {
      pending.add(BufferedToken(lastPart, setOf(FileTokenType.PATH_SEGMENT), partStart, partEnd))
    }

    val dotIndex = lastPart.lastIndexOf('.')
    when {
      dotIndex < 0 -> {
        // Extension-less file
        pending.add(BufferedToken(lastPart, setOf(FileTokenType.FILENAME), partStart, partEnd))
        if (lastPart.isNotEmpty()) {
          pending.add(BufferedToken(lastPart.lowercase(), setOf(FileTokenType.FILETYPE), partStart, partEnd))
        }
        if (pathSpans.size == 1 && lastPart.isNotEmpty()) {
          pending.add(BufferedToken(lastPart, setOf(FileTokenType.PATH_SEGMENT), partStart, partEnd))
        }
      }
      dotIndex == 0 -> {
        // Hidden file, e.g. ".gitignore": the whole token is FILENAME, body after dot is FILETYPE
        pending.add(BufferedToken(lastPart, setOf(FileTokenType.FILENAME), partStart, partEnd))
        val filetype = lastPart.substring(1)
        if (filetype.isNotEmpty()) {
          pending.add(BufferedToken(filetype.lowercase(), setOf(FileTokenType.FILETYPE), partStart + 1, partEnd))
        }
      }
      else -> {
        val filename = lastPart.substring(0, dotIndex)
        val filetype = lastPart.substring(dotIndex + 1)
        pending.add(BufferedToken(filename, setOf(FileTokenType.FILENAME), partStart, partStart + dotIndex))
        if (filetype.isNotEmpty()) {
          pending.add(BufferedToken(filetype.lowercase(), setOf(FileTokenType.FILETYPE), partStart + dotIndex + 1, partEnd))
        }
      }
    }

    emit(pending.removeFirst())
    return true
  }
}


/**
 * Generates overlapping 2-grams and 3-grams from [FileTokenType.FILENAME_PART] tokens of length ≥ 4.
 *
 * For each such token, all contiguous 2-character and 3-character substrings are queued as
 * {[FileTokenType.FILENAME_PART], [FileTokenType.PATH_SEGMENT_PREFIX]} tokens, allowing short
 * user-typed fragments (e.g. `"sea"`) to match camelCase word parts (e.g. `"search"`) via prefix queries.
 */
class FilenameNgramFilter(input: TokenStream) : TokenFilterBase(input) {
  override fun incrementToken(): Boolean {
    if (pending.isNotEmpty()) {
      emit(pending.removeFirst())
      return true
    }

    if (!input.incrementToken()) return false

    val activeTypes = multiTypeAttr.activeTypes().toSet()
    if (activeTypes.contains(FileTokenType.FILENAME_PART)) {
      val term = termAttr.toString()
      if (term.length >= 2) {
        val start = offsetAttr.startOffset()
        val ngramTypes = setOf(FileTokenType.FILENAME_PART, FileTokenType.PATH_SEGMENT_PREFIX, FileTokenType.FILENAME_ABBREVIATION)
        for (i in 0..term.length - 2) {
          pending.add(BufferedToken(term.substring(i, i + 2), ngramTypes, start + i, start + i + 2))
        }
        for (i in 0..term.length - 3) {
          pending.add(BufferedToken(term.substring(i, i + 3), ngramTypes, start + i, start + i + 3))
        }
      }
    }

    return true
  }

}


/**
 * Assigns [WordAttribute.wordIndex] by incrementing a counter each time
 * [PositionIncrementAttribute.positionIncrement] > 0 (set by the preceding
 * [PositionIncrementFromOffsetFilter]). The counter starts at -1, so the first token
 * (which always has posIncr = 1) gets the wordIndex = 0.
 */
class WordIndexFilter(input: TokenStream) : TokenFilter(input) {
  private val posIncrAttr = addAttribute(PositionIncrementAttribute::class.java)
  private val wordAttr = addAttribute(WordAttribute::class.java)

  private var wordIndex = -1

  override fun incrementToken(): Boolean {
    if (!input.incrementToken()) return false
    if (posIncrAttr.positionIncrement > 0) wordIndex++
    wordAttr.wordIndex = wordIndex
    return true
  }

  override fun reset() {
    super.reset()
    wordIndex = -1
  }
}


interface WordAttribute : Attribute {
  var wordIndex: Int
}

@Suppress("unused")
class WordAttributeImpl : AttributeImpl(), WordAttribute {
  override var wordIndex: Int = 0
  override fun clear() {
    wordIndex = 0
  }

  override fun copyTo(target: AttributeImpl) {
    (target as WordAttribute).wordIndex = wordIndex
  }

  override fun reflectWith(reflector: AttributeReflector) {
    reflector.reflect(WordAttribute::class.java, "wordIndex", wordIndex)
  }
}
