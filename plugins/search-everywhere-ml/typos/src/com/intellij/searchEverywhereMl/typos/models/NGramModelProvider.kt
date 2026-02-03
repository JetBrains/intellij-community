package com.intellij.searchEverywhereMl.typos.models

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.platform.ml.impl.ngram.model.SimpleNGramModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

class NGramModelProvider {

  private var bigramModel: SimpleNGramModel<String>? = null
  private var trigramModel: SimpleNGramModel<String>? = null
  private var loadedDictionary: LanguageModelDictionary? = null

  private val deferredModels: Deferred<Unit>? = ActionsLanguageModel.getInstance()?.let { model ->
    model.coroutineScope.async {
      val corpus = CorpusBuilder.getInstance()?.deferredCorpus?.await() ?: emptySet()
      loadedDictionary = model.deferredDictionary.await()
      bigramModel = trainModel(corpus, 2)
      trigramModel = trainModel(corpus, 3)
    }
  }

  /**
   * Calculate the sentence probability based on unigram, bigram, and trigram models.
   */
  fun calculateSentenceProbability(
    corrections: List<SearchEverywhereSpellCheckResult.Correction>,
    trigramWeight: Double = 0.4,
    bigramWeight: Double = 0.4,
    unigramWeight: Double = 0.2,
    epsilon: Double = 1e-8 // ensures that all probabilities remain non-zero
  ): Double {
    if (deferredModels == null || !deferredModels.isCompleted) return epsilon

    val dictionary = loadedDictionary ?: return epsilon
    val totalUnigramFrequency = dictionary.totalFrequency.toDouble().takeIf { it > 0 } ?: return epsilon

    return corrections.map { it.correction.lowercase() to it.confidence }
      .foldIndexed(1.0) { index, sentenceProbability, (currentWord, _) ->
        val unigramProb = calculateUnigramProbability(dictionary, currentWord, totalUnigramFrequency, epsilon)
        val bigramProb = calculateNGramProbability(index, 2, corrections, epsilon)
        val trigramProb = calculateNGramProbability(index, 3, corrections, epsilon)

        val wordProbability = combineProbabilities(unigramProb, bigramProb, trigramProb, trigramWeight, bigramWeight, unigramWeight, epsilon)
        sentenceProbability * wordProbability
      }
  }

  private fun trainModel(corpus: Set<List<String>>, n: Int): SimpleNGramModel<String> {
    return SimpleNGramModel
      .train(corpus.filter { it.size >= n }, n)
  }

  private fun calculateUnigramProbability(
    dictionary: LanguageModelDictionary,
    word: String,
    totalFrequency: Double,
    epsilon: Double
  ): Double {
    val frequency = dictionary.getFrequency(word) ?: 0
    return if (frequency > 0) frequency.toDouble() / totalFrequency else 1.0 / (totalFrequency + 1)
      .coerceAtLeast(epsilon)
  }

  private fun calculateNGramProbability(
    index: Int,
    n: Int,
    corrections: List<SearchEverywhereSpellCheckResult.Correction>,
    epsilon: Double
  ): Double {
    if (index < n - 1) return 0.0

    val lowercaseTokens = corrections.map { it.correction.lowercase() }

    // Extract the last N tokens for scoring from the model
    val tokensForScoring = lowercaseTokens.subList(index - n + 1, index + 1)

    val modelScore = when (n) {
      2 -> bigramModel?.scoreToken(tokensForScoring, n - 1)
      3 -> trigramModel?.scoreToken(tokensForScoring, n - 1)
      // We don't have an n-gram model for the given n
      else -> epsilon
    }
    return modelScore?.takeUnless { it.isNaN() }?.coerceAtLeast(epsilon) ?: epsilon
  }

  // Interpolation
  private fun combineProbabilities(
    unigramProbability: Double,
    bigramProbability: Double,
    trigramProbability: Double,
    trigramWeight: Double,
    bigramWeight: Double,
    unigramWeight: Double,
    epsilon: Double
  ): Double {

    val combinedProbability = unigramWeight * unigramProbability + bigramWeight * bigramProbability + trigramWeight * trigramProbability

    return combinedProbability.coerceAtLeast(epsilon)
  }
}