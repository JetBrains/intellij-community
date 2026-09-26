package com.intellij.searchEverywhereLucene.backend.providers.files.analysis.splitting

/** References a contiguous section of some source text, with its offsets relative to the source start. */
typealias Span = IntRange

/**
 * A rule that splits a span of [text] into sub-spans.
 * Implementations are pure functions with no Lucene dependency.
 */
abstract class WordSplittingRule(protected val text: String) {
  /** Returns sub-spans of [text]. Each span carries its own start/end (0-based, relative to [text]). */
  abstract fun split(span: Span = text.indices): Sequence<Span>
}

/**
 * Splits on any character that is not a letter/digit.
 *
 * Example: SymbolSplittingRule("foo/bar/baz").split() returns [0 until 3, 4 until 7, 8 until 11]
 */
open class SymbolSplittingRule(text: String, val isSymbol: (Char) -> Boolean) : WordSplittingRule(text) {
  override fun split(span: Span): Sequence<Span> = sequence {
    var start = -1
    for (i in span) {
      if (!isSymbol(text[i])) {
        if (start == -1) start = i
      }
      else {
        if (start != -1) {
          yield(start until i)
          start = -1
        }
      }
    }
    if (start != -1) yield(start until span.last + 1)
  }
}


class LetterAndDigitSplittingRule(text: String) : SymbolSplittingRule(text, { c -> !c.isLetterOrDigit() })
class PathSplittingRule(text: String) : SymbolSplittingRule(text, { it == '/' })


/**
 * Splits at letter/digit transitions: "File2Open" → [0 until 4, 4 until 5, 5 until 9].
 * Always yields at least the full input span (even when no transitions are found).
 */
class NumericTransitionSplittingRule(text: String) : WordSplittingRule(text) {
  override fun split(span: IntRange): Sequence<IntRange> = sequence {
    if (span.isEmpty()) return@sequence
    var start = span.first
    for (i in span.first + 1..span.last) {
      val prev = text[i - 1]
      val curr = text[i]
      if ((prev.isLetter() && curr.isDigit()) || (prev.isDigit() && curr.isLetter())) {
        yield(start until i)
        start = i
      }
    }

    yield(start..span.last)
  }
}

/**
 * Splits at camelCase boundaries:
 *  - lower-to-upper: "myFile" → [0 until 2, 2 until 6]
 *  - acronym end (UUl): "LSPtooling" → [0 until 3, 3 until 10]
 *
 * At acronym boundaries where the preceding uppercase span has ≤ 2 chars,
 * each letter is emitted as its own span (e.g., "ABManager" → A, B, Manager).
 * Longer acronyms are kept as a single span (e.g., "HTTPServer" → HTTP, Server).
 * Trailing all-uppercase sequences of ≤ 2 chars are also split individually when
 * preceded by other content (e.g., "fooAB" → foo, A, B; "fooHTTP" is unchanged since
 * 4 > 2). Standalone short uppercase sequences like "MF" are kept whole.
 */
class CamelCaseSplittingRule(text: String) : WordSplittingRule(text) {
  override fun split(span: IntRange): Sequence<IntRange> = sequence {
    if (span.isEmpty()) return@sequence
    var start = span.first
    var i = span.first + 1
    while (i <= span.last) {
      val prev = text[i - 1]
      val curr = text[i]
      val isAcronymBoundary = i < span.last &&
                              prev.isUpperCase() && curr.isUpperCase() && text[i + 1].isLowerCase()
      val isBoundary = (prev.isLowerCase() && curr.isUpperCase()) || isAcronymBoundary
      if (isBoundary) {
        val subSpan = start until i
        if (isAcronymBoundary && subSpan.count() <= 2) {
          // Short acronym (≤ 2 chars): emit each letter individually
          for (j in subSpan) yield(j..j)
        }
        else {
          yield(subSpan)
        }
        start = i
      }
      i++
    }
    val trailingSpan = start..span.last
    if (trailingSpan.count() <= 2 && trailingSpan.all { text[it].isUpperCase() } && start > span.first) {
      for (j in trailingSpan) yield(j..j)
    }
    else {
      yield(trailingSpan)
    }
  }
}
