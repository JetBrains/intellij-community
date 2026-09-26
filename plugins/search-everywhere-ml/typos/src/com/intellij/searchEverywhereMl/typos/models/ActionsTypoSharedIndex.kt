package com.intellij.searchEverywhereMl.typos.models

import ai.grazie.nlp.similarity.Levenshtein
import ai.grazie.spell.lists.FrequencyMetadata
import ai.grazie.spell.lists.WordList
import com.intellij.grazie.utils.LinkedSet
import com.intellij.searchEverywhereMl.typos.SearchEverywhereStringToken
import com.intellij.searchEverywhereMl.typos.splitText
import java.util.ArrayList
import java.util.LinkedHashSet
import java.util.regex.Pattern

private val alphabeticPattern = Pattern.compile("^[a-zA-Z]+$")
private val acceptableDictionaryWordPattern = Pattern.compile("^.{3,45}$")

internal fun tokenizeTextForTypoLookup(text: CharSequence): List<String> {
  return splitText(text)
    .filterIsInstance<SearchEverywhereStringToken.Word>()
    .map { it.value.lowercase() }
    .filter { alphabeticPattern.matcher(it).matches() }
    .toList()
}

internal class ActionsTypoSharedIndex private constructor(
  private val tokenIdsByWord: HashMap<String, Int>,
  private val wordsByTokenId: Array<String>,
  private val sentenceTokenIds: Array<IntArray>,
  private val dictionaryFrequenciesByTokenId: IntArray,
) {
  private val dictionary: LanguageModelDictionary by lazy(LazyThreadSafetyMode.PUBLICATION) {
    SharedIndexLanguageModelDictionary(this)
  }

  fun asDictionary(): LanguageModelDictionary = dictionary

  fun createPrefixMatcher(): ActionsTypoPrefixMatcher = ActionsTypoPrefixMatcher.create(this)

  fun sentenceTexts(): Sequence<String> {
    return sentenceTokenIds.asSequence().map { tokenIds ->
      tokenIds.joinToString(separator = " ") { tokenId -> wordByTokenId(tokenId) }
    }
  }

  internal fun sentenceTokenIds(): Array<IntArray> = sentenceTokenIds

  internal fun wordByTokenId(tokenId: Int): String = wordsByTokenId[tokenId]

  internal fun tokenIdByWord(word: String): Int? = tokenIdsByWord[word]

  internal fun dictionaryFrequency(tokenId: Int): Int = dictionaryFrequenciesByTokenId[tokenId]

  internal fun totalDictionaryFrequency(): Int = dictionaryFrequenciesByTokenId.sum()

  internal fun maxDictionaryFrequency(): Int = dictionaryFrequenciesByTokenId.maxOrNull() ?: 0

  internal fun eligibleWords(): Set<String> {
    val result = LinkedHashSet<String>()
    for (tokenId in wordsByTokenId.indices) {
      if (dictionaryFrequenciesByTokenId[tokenId] > 0) {
        result.add(wordsByTokenId[tokenId])
      }
    }
    return result
  }

  internal fun decodedSentences(minLength: Int = 0): Sequence<List<String>> {
    return sentenceTokenIds.asSequence()
      .filter { it.size >= minLength }
      .map { tokenIds ->
        tokenIds.map { tokenId -> wordByTokenId(tokenId) }
      }
  }

  companion object {
    fun create(corpus: Iterable<List<String>>): ActionsTypoSharedIndex {
      val tokenIdsByWord = HashMap<String, Int>()
      val wordsByTokenId = ArrayList<String>()
      val dictionaryFrequenciesByTokenId = ArrayList<Int>()
      val encodedSentences = ArrayList<IntArray>()

      for (sentence in corpus) {
        if (sentence.isEmpty()) continue

        val encodedSentence = IntArray(sentence.size)
        for ((wordIndex, word) in sentence.withIndex()) {
          val tokenId = tokenIdsByWord.getOrPut(word) {
            val newTokenId = wordsByTokenId.size
            wordsByTokenId.add(word)
            dictionaryFrequenciesByTokenId.add(0)
            newTokenId
          }
          encodedSentence[wordIndex] = tokenId

          if (acceptableDictionaryWordPattern.matcher(word).matches()) {
            dictionaryFrequenciesByTokenId[tokenId] = dictionaryFrequenciesByTokenId[tokenId] + 1
          }
        }
        encodedSentences.add(encodedSentence)
      }

      return ActionsTypoSharedIndex(
        tokenIdsByWord = tokenIdsByWord,
        wordsByTokenId = wordsByTokenId.toTypedArray(),
        sentenceTokenIds = encodedSentences.toTypedArray(),
        dictionaryFrequenciesByTokenId = dictionaryFrequenciesByTokenId.toIntArray(),
      )
    }
  }
}

internal class ActionsTypoPrefixMatcher private constructor(
  private val sharedIndex: ActionsTypoSharedIndex,
  private val suffixRefsByFirstTokenId: HashMap<Int, LongArray>,
) {
  fun hasPrefix(query: CharSequence): Boolean {
    val queryTokens = tokenizeTextForTypoLookup(query)
    if (queryTokens.isEmpty()) return false

    if (queryTokens.size == 1) {
      val queryToken = queryTokens.single()
      return suffixRefsByFirstTokenId.keys.any { tokenId ->
        sharedIndex.wordByTokenId(tokenId).startsWith(queryToken)
      }
    }

    val exactQueryTokenIds = IntArray(queryTokens.lastIndex)
    for (index in 0 until queryTokens.lastIndex) {
      val tokenId = sharedIndex.tokenIdByWord(queryTokens[index]) ?: return false
      exactQueryTokenIds[index] = tokenId
    }

    val suffixRefs = suffixRefsByFirstTokenId[exactQueryTokenIds[0]] ?: return false
    val queryLastToken = queryTokens.last()

    for (suffixRef in suffixRefs) {
      val sentence = sharedIndex.sentenceTokenIds()[sentenceIndex(suffixRef)]
      val startIndex = startIndex(suffixRef)
      if (sentence.size - startIndex < queryTokens.size) continue

      var matches = true
      for (index in 0 until queryTokens.lastIndex) {
        if (sentence[startIndex + index] != exactQueryTokenIds[index]) {
          matches = false
          break
        }
      }

      if (matches && sharedIndex.wordByTokenId(sentence[startIndex + queryTokens.lastIndex]).startsWith(queryLastToken)) {
        return true
      }
    }

    return false
  }

  companion object {
    fun create(sharedIndex: ActionsTypoSharedIndex): ActionsTypoPrefixMatcher {
      val suffixRefsByFirstTokenId = HashMap<Int, ArrayList<Long>>()

      for ((sentenceIndex, sentenceTokenIds) in sharedIndex.sentenceTokenIds().withIndex()) {
        for (startIndex in sentenceTokenIds.indices) {
          val firstTokenId = sentenceTokenIds[startIndex]
          suffixRefsByFirstTokenId.getOrPut(firstTokenId) { ArrayList() }.add(packSuffixRef(sentenceIndex, startIndex))
        }
      }

      val compactSuffixRefs = HashMap<Int, LongArray>()
      for ((firstTokenId, suffixRefs) in suffixRefsByFirstTokenId) {
        compactSuffixRefs[firstTokenId] = suffixRefs.toLongArray()
      }

      return ActionsTypoPrefixMatcher(sharedIndex, compactSuffixRefs)
    }

    private fun packSuffixRef(sentenceIndex: Int, startIndex: Int): Long {
      return (sentenceIndex.toLong() shl 32) or (startIndex.toLong() and 0xffffffffL)
    }

    private fun sentenceIndex(suffixRef: Long): Int = (suffixRef ushr 32).toInt()

    private fun startIndex(suffixRef: Long): Int = suffixRef.toInt()
  }
}

private class SharedIndexLanguageModelDictionary(
  private val sharedIndex: ActionsTypoSharedIndex,
) : LanguageModelDictionary {
  private val eligibleWords: Set<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
    sharedIndex.eligibleWords()
  }

  override val totalFrequency: Int by lazy(LazyThreadSafetyMode.PUBLICATION) {
    sharedIndex.totalDictionaryFrequency()
  }

  override val allWords: Set<String>
    get() = eligibleWords

  override val defaultFrequency: Int
    get() = 0

  override val maxFrequency: Int by lazy(LazyThreadSafetyMode.PUBLICATION) {
    sharedIndex.maxDictionaryFrequency()
  }

  override fun getFrequency(word: String): Int? {
    val tokenId = sharedIndex.tokenIdByWord(word) ?: return null
    return sharedIndex.dictionaryFrequency(tokenId).takeIf { it > 0 }
  }

  override fun contains(word: String, caseSensitive: Boolean): Boolean =
    allWords.any { it.startsWith(word, false) }

  override fun suggest(word: String): LinkedSet<String> = allWords
    .filterTo(LinkedHashSet()) {
      Levenshtein.distance(it, word, LanguageModelDictionary.Companion.MAX_LEVENSHTEIN_DISTANCE + 1) <= LanguageModelDictionary.Companion.MAX_LEVENSHTEIN_DISTANCE
    }
}
