package com.intellij.searchEverywhereMl.typos


/**
 * Splits text by non-alphanumeric characters
 *
 * See test cases in [com.intellij.searchEverywhereMl.typos.SearchEverywhereStringTokenizerTest] for more examples.
 */
internal fun splitText(text: CharSequence): Sequence<SearchEverywhereStringToken> {
  return sequence {
    val builder = StringBuilder()
    text.forEachIndexed { index, c ->
      if (c.isLetterOrDigit().not()) {
        yieldWordIfNotBlank(builder.toString())
        builder.clear()

        yieldDelimiter(c.toString())
        return@forEachIndexed
      }

      if (c.isUpperCase() && builder.isNotEmpty() && builder.last().isLowerCase()) {
        yieldWord(builder.toString())
        builder.clear()
      }

      builder.append(c)

      if (index == text.lastIndex) {
        yieldWordIfNotBlank(builder.toString())
      }
    }
  }
}

internal sealed interface SearchEverywhereStringToken {
  val value: String

  @JvmInline
  value class Word(override val value: String) : SearchEverywhereStringToken

  @JvmInline
  value class Delimiter(override val value: String) : SearchEverywhereStringToken
}

private suspend fun SequenceScope<SearchEverywhereStringToken>.yieldToken(token: SearchEverywhereStringToken) {
  yield(token)
}

private suspend fun SequenceScope<SearchEverywhereStringToken>.yieldWord(word: String) {
  yieldToken(SearchEverywhereStringToken.Word(word))
}

private suspend fun SequenceScope<SearchEverywhereStringToken>.yieldWordIfNotBlank(word: String) {
  if (word.isNotBlank()) {
    yieldWord(word)
  }
}

private suspend fun SequenceScope<SearchEverywhereStringToken>.yieldDelimiter(delimiter: String) {
  yieldToken(SearchEverywhereStringToken.Delimiter(delimiter))
}

private fun StringBuilder.clear() {
  this.delete(0, length)
}