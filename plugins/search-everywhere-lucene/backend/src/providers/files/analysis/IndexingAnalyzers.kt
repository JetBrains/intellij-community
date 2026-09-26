package com.intellij.searchEverywhereLucene.backend.providers.files.analysis

import com.intellij.searchEverywhereLucene.backend.providers.files.analysis.splitting.PathSplittingRule
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.LowerCaseFilter
import org.apache.lucene.analysis.TokenFilter
import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.core.KeywordTokenizer

/**
 * Detects the PATH, FILENAME (stem, original case), and FILETYPE (extension, lowercase) of a raw
 * keyword token and emits them as separate typed tokens.
 *
 * The FILENAME term is kept in original case so that [AbbreviationTokenFilter] can correctly
 * derive camelCase abbreviations. Lowercasing of FILENAME is deferred to that filter.
 */
internal class PathAndFilenameTypeFilter(input: TokenStream) : TokenFilterBase(input) {
  override fun incrementToken(): Boolean {
    if (pending.isNotEmpty()) {
      emit(pending.removeFirst())
      return true
    }

    if (!input.incrementToken()) return false

    val fullText = termAttr.toString()

    // PATH: full text, case-preserved
    pending.addLast(BufferedToken(fullText, setOf(FileTokenType.PATH), 0, fullText.length))

    val dotIndex = fullText.lastIndexOf('.')
    val nameStem: String
    val ext: String?
    val extStart: Int
    when {
      dotIndex < 0 -> {
        nameStem = fullText; ext = null; extStart = -1
      }
      dotIndex == 0 -> {
        nameStem = fullText; ext = fullText.substring(1); extStart = 1
      }
      else -> {
        nameStem = fullText.substring(0, dotIndex); ext = fullText.substring(dotIndex + 1); extStart = dotIndex + 1
      }
    }

    // FILENAME: stem, original case (lowercasing deferred to AbbreviationTokenFilter)
    pending.addLast(BufferedToken(nameStem, setOf(FileTokenType.FILENAME), 0, nameStem.length))

    // FILETYPE: extension, lowercase, highest offset
    if (!ext.isNullOrEmpty()) {
      pending.addLast(BufferedToken(ext.lowercase(), setOf(FileTokenType.FILETYPE), extStart, extStart + ext.length))
    }

    emit(pending.removeFirst())
    return true
  }
}


class FileNameAnalyzer : Analyzer() {
  override fun createComponents(fieldName: String): TokenStreamComponents {
    val tokenizer = KeywordTokenizer()
    var stream: TokenStream = PathAndFilenameTypeFilter(tokenizer)
    stream = WordSplittingTokenFilter(stream,
                                      inputTypes = setOf(FileTokenType.FILENAME),
                                      outputType = FileTokenType.FILENAME_PART,
                                      passThrough = PassthroughOptions.PassthroughLast)
    stream = AbbreviationTokenFilter(stream,
                                     sourceTypes = setOf(FileTokenType.FILENAME_PART),
                                     outputType = FileTokenType.FILENAME_ABBREVIATION,
                                     allowedSkip = 1,
                                     passThrough = PassthroughOptions.PassthroughLast,
                                     skipOutputType = FileTokenType.FILENAME_ABBREVIATION_WITH_SKIPS)
    stream = TokenMergingFilter(stream)
    stream = PositionIncrementFromOffsetFilter(stream)
    return TokenStreamComponents(tokenizer, stream)
  }

  override fun normalize(fieldName: String, inStream: TokenStream): TokenStream =
    LowerCaseFilter(inStream)
}


class FilePathAnalyzer : Analyzer() {
  override fun createComponents(fieldName: String): TokenStreamComponents {
    val tokenizer = KeywordTokenizer()
    var stream: TokenStream = TypeSettingTokenFilter(tokenizer, FileTokenType.PATH)
    stream = PathSegmentSplittingFilter(stream)
    stream = PositionIncrementFromOffsetFilter(stream)
    return TokenStreamComponents(tokenizer, stream)
  }
}

/** Splits PATH tokens on '/' into PATH_SEGMENT sub-tokens, keeping the original PATH as passthrough. */
private class PathSegmentSplittingFilter(input: TokenStream) : TokenFilterBase(input) {
  override fun incrementToken(): Boolean {
    if (pending.isNotEmpty()) {
      emit(pending.removeFirst())
      return true
    }

    if (!input.incrementToken()) return false

    val path = termAttr.toString()
    val pathStart = offsetAttr.startOffset()

    // Passthrough first: emit the original PATH token
    pending.addLast(BufferedToken(path, setOf(FileTokenType.PATH), pathStart, pathStart + path.length))
    // Then emit each segment
    for (span in PathSplittingRule(path).split()) {
      pending.addLast(BufferedToken(
        path.substring(span.first, span.last + 1),
        setOf(FileTokenType.PATH_SEGMENT),
        pathStart + span.first,
        pathStart + span.last + 1,
      ))
    }

    emit(pending.removeFirst())
    return true
  }
}


class FileTypeAnalyzer : Analyzer() {
  override fun createComponents(fieldName: String): TokenStreamComponents {
    val tokenizer = KeywordTokenizer()
    return TokenStreamComponents(tokenizer, TypeSettingTokenFilter(LowerCaseFilter(tokenizer), FileTokenType.FILETYPE))
  }
}

private class TypeSettingTokenFilter(input: TokenStream, private val type: FileTokenType) : TokenFilter(input) {
  private val multiTypeAttr = addAttribute(MultiTypeAttribute::class.java)

  override fun incrementToken(): Boolean {
    if (!input.incrementToken()) return false
    multiTypeAttr.clearTypes()
    multiTypeAttr.setTypes(setOf(type))

    return true
  }
}
