package com.intellij.searchEverywhereMl.typos.models

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.platform.ml.impl.ngram.model.SimpleNGramModel

internal class NGramModelProvider(private val actionsLanguageModel: ActionsLanguageModel? = ActionsLanguageModel.getInstance()) {

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
    val nGramModels = actionsLanguageModel?.getReadyNGramModelsOrNull()
      ?: run {
        actionsLanguageModel?.ensureNGramModelsBuilding()
        return epsilon
      }

    val dictionary = nGramModels.dictionary
    val totalUnigramFrequency = dictionary.totalFrequency.toDouble().takeIf { it > 0 } ?: return epsilon

    return corrections.map { it.correction.lowercase() to it.confidence }
      .foldIndexed(1.0) { index, sentenceProbability, (currentWord, _) ->
        val unigramProb = calculateUnigramProbability(dictionary, currentWord, totalUnigramFrequency, epsilon)
        val bigramProb = calculateNGramProbability(index, 2, corrections, nGramModels, epsilon)
        val trigramProb = calculateNGramProbability(index, 3, corrections, nGramModels, epsilon)

        val wordProbability = combineProbabilities(unigramProb, bigramProb, trigramProb, trigramWeight, bigramWeight, unigramWeight, epsilon)
        sentenceProbability * wordProbability
      }
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
    nGramModels: ActionsTypoNGramModels,
    epsilon: Double
  ): Double {
    if (index < n - 1) return 0.0

    val lowercaseTokens = corrections.map { it.correction.lowercase() }

    // Extract the last N tokens for scoring from the model
    val tokensForScoring = lowercaseTokens.subList(index - n + 1, index + 1)

    val modelScore = when (n) {
      2 -> nGramModels.bigramModel.scoreToken(tokensForScoring, n - 1)
      3 -> nGramModels.trigramModel.scoreToken(tokensForScoring, n - 1)
      // We don't have an n-gram model for the given n
      else -> epsilon
    }
    return modelScore.takeUnless { it.isNaN() }?.coerceAtLeast(epsilon) ?: epsilon
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

internal class ActionsTypoNGramModels(
  val dictionary: LanguageModelDictionary,
  val bigramModel: SimpleNGramModel<String>,
  val trigramModel: SimpleNGramModel<String>,
) {
  companion object {
    fun create(sharedIndex: ActionsTypoSharedIndex): ActionsTypoNGramModels {
      val dictionary = sharedIndex.asDictionary()
      val bigramModel = SimpleNGramModel.train(sharedIndex.decodedSentences(minLength = 2).toList(), 2)
      val trigramModel = SimpleNGramModel.train(sharedIndex.decodedSentences(minLength = 3).toList(), 3)
      return ActionsTypoNGramModels(dictionary, bigramModel, trigramModel)
    }
  }
}
