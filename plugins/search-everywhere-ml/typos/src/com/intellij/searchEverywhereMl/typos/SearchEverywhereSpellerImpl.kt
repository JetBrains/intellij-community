// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.searchEverywhereMl.typos

import ai.grazie.nlp.langs.Language
import ai.grazie.nlp.langs.alphabet.Alphabet
import ai.grazie.spell.GrazieSpeller
import ai.grazie.spell.dictionary.RuleDictionary
import ai.grazie.spell.dictionary.rule.IgnoreRuleDictionary
import ai.grazie.spell.language.LanguageModel
import ai.grazie.spell.lists.hunspell.HunspellWordList
import ai.grazie.spell.suggestion.filter.SuggestionFilter
import ai.grazie.spell.utils.DictionaryResources
import ai.grazie.utils.mpp.DataLoader
import ai.grazie.utils.mpp.RootStreamDataLoader
import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellingCorrector
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellingCorrectorFactory
import com.intellij.openapi.components.service
import com.intellij.searchEverywhereMl.typos.models.ActionsLanguageModel
import com.intellij.searchEverywhereMl.typos.models.CorpusBuilder
import com.intellij.searchEverywhereMl.typos.models.LanguageModelDictionary
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import java.io.InputStream

class SearchEverywhereSpellerImpl : SearchEverywhereSpellingCorrector {

  private val deferredSpeller: Deferred<GrazieSpeller>? = ActionsLanguageModel.getInstance()
    ?.let { model ->
      model.coroutineScope.async {
        val dictionary = model.deferredDictionary.await()
        createSpeller(dictionary)
      }
    }

  /**
   * Creates a GrazieSpeller object from the provided LanguageModelDictionary.
   */
  private suspend fun createSpeller(dictionary: LanguageModelDictionary): GrazieSpeller {
    val configuration = createSpellerConfiguration(dictionary)
    return GrazieSpeller(config = configuration)
  }

  private suspend fun createSpellerConfiguration(dictionary: LanguageModelDictionary): GrazieSpeller.UserConfig {
    val updatedDictionaryText = buildDictionary(dictionary)

    val affFile = HunspellLoader.text("/dictionary/en.aff")
    val trigrams = HunspellLoader.text("/dictionary/en.trigrams.txt").lines()

    // Build the final word list.
    val wordList = HunspellWordList.create(
      affDict = affFile,
      dicDict = updatedDictionaryText,
      trigrams = trigrams,
      checkCanceled = { false }
    )

    // A no-op filter for suggestions (does nothing).
    val noOpFilter = object : SuggestionFilter {
      override fun filter(suggestions: Map<String, Double>): Map<String, Double> = suggestions
    }

    return GrazieSpeller.UserConfig(
      model = LanguageModel(
        Language.ENGLISH,
        wordList,
        rules = RuleDictionary.Aggregated(
          IgnoreRuleDictionary.standard(tooShortLength = 2),
          DictionaryResources.getReplacingRules("/rule/en", HunspellLoader)
        ),
        filter = noOpFilter,
        isAlien = { !Alphabet.ENGLISH.matchAny(it) }
      )
    )
  }

  /**
   * Returns all possible spelling corrections for the given query if the speller is ready.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  override fun getAllCorrections(query: String, maxCorrections: Int): List<SearchEverywhereSpellCheckResult.Correction> {
    // If the query is blank or the speller hasn't completed initialization, return an empty list.
    if (query.isBlank()) return emptyList()
    if (deferredSpeller == null || !deferredSpeller.isCompleted) return emptyList()

    val speller = deferredSpeller.getCompleted()
    val suggestionsGenerator = SpellerSuggestionsGenerator(speller)

    // Generate combined corrections from the speller.
    return suggestionsGenerator.combinedCorrections(query).take(maxCorrections)
  }

  /**
   * Checks the spelling for a single query string and returns the first correction if any exist.
   */
  override fun checkSpellingOf(query: String): SearchEverywhereSpellCheckResult {
    val corrections = getAllCorrections(query, 1)
    return if (corrections.isEmpty()) {
      SearchEverywhereSpellCheckResult.NoCorrection
    } else {
      corrections.first()
    }
  }

  override fun isAvailableInTab(tabId: String): Boolean =
    tabId == ActionSearchEverywhereContributor::class.java.simpleName && isTypoFixingEnabled

  /**
   * Builds a new dictionary by merging the base Hunspell file with custom words and their frequencies.
   */
  private suspend fun buildDictionary(dictionary: LanguageModelDictionary): String {
    val existingDictionaryText = HunspellLoader.text("/dictionary/en.dic")
    val existingLines = existingDictionaryText.lineSequence().toList()

    // The first line is word count, Remaining lines are the actual words
    val baseDictionaryLines = existingLines.drop(1).toList()

    // Store (root word → (morphologicalWord, frequency)).
    // Example: "AIDS" → ("AIDS/M", 75).
    val baseWordsMap = mutableMapOf<String, Pair<String, Int>>()

    for (line in baseDictionaryLines) {
      val parts = line.split(" ")
      when (parts.size) {
        1 -> {
          // If there's only one part, store the morphological word with frequency = 0.
          val morphologicalWord = parts[0]
          val rootWord = morphologicalWord.substringBefore('/')
          baseWordsMap[rootWord] = morphologicalWord to 0
        }
        2 -> {
          // Standard case: morphological word plus "fr:XXX".
          val morphologicalWord = parts[0]
          val freqString = parts[1]
          val frequencyValue = freqString.substringAfter("fr:", "0").toIntOrNull() ?: 0
          val rootWord = morphologicalWord.substringBefore('/')
          baseWordsMap[rootWord] = morphologicalWord to frequencyValue
        }
        else -> {
          // Skip lines that do not match the expected formats.
        }
      }
    }

    // For each word in the custom dictionary, merge or add frequencies.
    for (customWord in dictionary.allWords) {
      val customFreq = dictionary.getFrequency(customWord) ?: 0
      if (customFreq <= 0) continue

      if (baseWordsMap.containsKey(customWord)) {
        // If present in the base map, combine frequencies.
        val (existingMorph, existingFreq) = baseWordsMap[customWord]!!
        baseWordsMap[customWord] = existingMorph to (existingFreq + customFreq)
      } else {
        baseWordsMap[customWord] = customWord to customFreq
      }
    }

    // Build final dictionary lines, omitting "fr:0" if the frequency is 0.
    val dictionaryLines = baseWordsMap.values
      .map { (mWord, freq) -> if (freq > 0) "$mWord fr:$freq" else mWord }
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .sorted()

    // Build the final dictionary text
    val totalWordCount = dictionaryLines.size

    return buildString {
      appendLine(totalWordCount)
      append(dictionaryLines.joinToString("\n"))
    }
  }

  init {
    service<CorpusBuilder>()
    service<ActionsLanguageModel>()
  }
}

private class GrazieSpellingCorrectorFactoryImpl : SearchEverywhereSpellingCorrectorFactory {
  override fun isAvailable(): Boolean {
    // Some logic to verify if typo-fixing is enabled.
    return isTypoFixingEnabled
  }

  override fun create(): SearchEverywhereSpellingCorrector = SearchEverywhereSpellerImpl()
}

object HunspellLoader : RootStreamDataLoader {
  override fun stream(path: DataLoader.Path): InputStream {
    return checkNotNull(this::class.java.getResourceAsStream(path.toAbsolutePath())) {
      "Resource not found: ${path.toAbsolutePath()}"
    }
  }

  override suspend fun bytes(path: DataLoader.Path): ByteArray {
    return stream(path).readBytes()
  }

  override suspend fun text(path: DataLoader.Path): String {
    return bytes(path).decodeToString()
  }
}