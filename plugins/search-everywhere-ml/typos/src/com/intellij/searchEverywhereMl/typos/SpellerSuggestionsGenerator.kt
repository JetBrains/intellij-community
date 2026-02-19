package com.intellij.searchEverywhereMl.typos

import ai.grazie.spell.GrazieSpeller
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.searchEverywhereMl.typos.models.NGramModelProvider
import org.jetbrains.annotations.ApiStatus

/**
 * Generates possible spelling corrections for a sentence.
 * Each full-sentence combination is then scored and sorted by probability.
 */
@ApiStatus.Internal
class SpellerSuggestionsGenerator(
  private val speller: GrazieSpeller,
  private val suggestionLimit: Int = 5
) {

  private val nGramModelProvider: NGramModelProvider = NGramModelProvider()

  /**
   * Holds a list of corrected sentence tokens plus the overall probability for that sentence.
   */
  private data class SentenceCorrection(
    val correctedTokens: List<SearchEverywhereSpellCheckResult.Correction>,
    val probability: Double
  )

  /**
   * Returns all possible correction combinations for the given sentence
   * sorted in descending order by their calculated probability.
   */
  fun combinedCorrections(sentence: String): List<SearchEverywhereSpellCheckResult.Correction> {
    val tokens = splitText(sentence)
    // skip full processing if too many word tokens
    if (tokens.count { it is SearchEverywhereStringToken.Word } > 10) {
      return listOf(SearchEverywhereSpellCheckResult.Correction(sentence, 1.0))
    }

    // For each word, either collect the top corrections or assume it's correct
    val suggestionsPerWord = tokens
      .filterIsInstance<SearchEverywhereStringToken.Word>()
      .map { token ->
        val corrections = collectWordCorrections(token.value)
        corrections.ifEmpty {
          // If no corrections, treat the word as already correct with max confidence
          listOf(SearchEverywhereSpellCheckResult.Correction(token.value, 1.0))
        }
      }

    // Generate all possible combinations of corrections from these suggestions
    val allCombinations = cartesianProduct(suggestionsPerWord.toList())

    // Calculate overall probabilities for each combination
    val combinationProbabilities = allCombinations.map { combination ->
      SentenceCorrection(
        correctedTokens = combination,
        probability = nGramModelProvider.calculateSentenceProbability(combination)
      )
    }

    // Normalize probabilities
    val normalizedCombinations = normalizeProbabilities(combinationProbabilities)

    // Weight normalized probabilities by word confidence
    val weightedCombinations = normalizedCombinations.map { sentenceCorrection ->
      SentenceCorrection(
        correctedTokens = sentenceCorrection.correctedTokens,
        probability = sentenceCorrection.probability * sentenceCorrection.correctedTokens.fold(1.0) { acc, correction ->
          acc * correction.confidence // Multiply by word confidence
        }
      )
    }.let { sentenceCorrections ->
      // Normalize final confidences if necessary
      val maxConfidence = sentenceCorrections
                            .flatMap { it.correctedTokens }
                            .maxOfOrNull { correction -> correction.confidence } ?: 1.0

      if (maxConfidence > 1.0) {
        normalizeProbabilities(sentenceCorrections)
      } else {
        sentenceCorrections
      }
    }.sortedByDescending { it.probability }

    return buildCorrections(tokens, weightedCombinations, sentence)

  }

  /**
   * Reconstruct each combination into a full string, preserving delimiters, and skip any
   * that match the original sentence to avoid duplicates.
   */
  private fun buildCorrections(
    tokens: Sequence<SearchEverywhereStringToken>,
    scoredCombinations: List<SentenceCorrection>,
    originalSentence: String
  ): List<SearchEverywhereSpellCheckResult.Correction> {
    return scoredCombinations.mapNotNull { combination ->
      var wordIndex = 0
      val joined = tokens.joinToString("") { token ->
        when (token) {
          // Replace the original word with the corrected version in the same index
          is SearchEverywhereStringToken.Word -> combination.correctedTokens[wordIndex++].correction
          // Preserve non-alphanumeric delimiters exactly as they appear
          is SearchEverywhereStringToken.Delimiter -> token.value
        }
      }
      // Exclude joined strings that are the same as the original sentence
      if (joined != originalSentence) {
        SearchEverywhereSpellCheckResult.Correction(correction = joined, confidence = combination.probability)
      } else {
        null
      }
    }
  }

  /**
   * Returns the sorted corrections for a single query word.
   */
  private fun collectWordCorrections(query: String): List<SearchEverywhereSpellCheckResult.Correction> {
    val rawSuggestions = speller.suggestAndRank(query, max = suggestionLimit)
    if (rawSuggestions.isEmpty()) return emptyList()

    return rawSuggestions.entries
      .sortedByDescending { it.value }
      .map { (word, confidence) ->
        SearchEverywhereSpellCheckResult.Correction(word, confidence)
      }
  }

  /**
   * Normalizes a list of SentenceCorrections by dividing each probability by the maximum probability.
   */
  private fun normalizeProbabilities(
    sentenceCorrections: List<SentenceCorrection>
  ): List<SentenceCorrection> {
    val maxProbability = sentenceCorrections.maxOfOrNull { it.probability } ?: 1.0
    return sentenceCorrections.map { sentenceCorrection ->
      SentenceCorrection(
        correctedTokens = sentenceCorrection.correctedTokens, // Keep tokens as they are
        probability = sentenceCorrection.probability / maxProbability // Normalize probability
      )
    }
  }

  /**
   * Given multiple sets of word-corrections, returns
   * every possible sentence-combination of those corrections.
   */
  private fun cartesianProduct(lists: List<List<SearchEverywhereSpellCheckResult.Correction>>): List<List<SearchEverywhereSpellCheckResult.Correction>> {
    if (lists.isEmpty()) return emptyList()

    val result = mutableListOf<List<SearchEverywhereSpellCheckResult.Correction>>()
    fun recurse(current: List<SearchEverywhereSpellCheckResult.Correction>, depth: Int) {
      if (depth == lists.size) {
        result.add(current)
        return
      }
      for (correction in lists[depth]) {
        recurse(current + correction, depth + 1)
      }
    }
    recurse(emptyList(), 0)
    return result
  }
}