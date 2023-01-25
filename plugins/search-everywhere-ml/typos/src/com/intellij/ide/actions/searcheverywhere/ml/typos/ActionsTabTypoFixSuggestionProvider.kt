package com.intellij.ide.actions.searcheverywhere.ml.typos

import ai.grazie.spell.lists.FrequencyMetadata
import ai.grazie.spell.suggestion.ranker.FrequencySuggestionRanker
import ai.grazie.spell.suggestion.ranker.JaroWinklerSuggestionRanker
import ai.grazie.spell.suggestion.ranker.LinearAggregatingSuggestionRanker
import ai.grazie.spell.suggestion.ranker.SuggestionRanker
import com.intellij.grazie.utils.LinkedSet
import com.intellij.grazie.utils.toLinkedSet
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.progress.withBackgroundProgress
import com.intellij.openapi.progress.withRawProgressReporter
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.spellchecker.SpellCheckerManager
import kotlinx.coroutines.*
import kotlin.coroutines.EmptyCoroutineContext

internal class ActionsTabTypoFixSuggestionProvider(private val project: Project) {
  private val spellChecker = SpellCheckerManager.getInstance(project)
  private var suggestionRanker: SuggestionRanker? = null

  fun suggestFixFor(query: String): CorrectionResult? {
    var wasCorrected = false
    val correction = query.split(" ")
      .filter { it.isNotBlank() }
      .map {
        if (!spellChecker.hasProblem(it)) return@map NoCorrection(it)

        val bestSuggestion = try {
          getBestSuggestion(it, spellChecker.getSuggestions(it).toLinkedSet())
        }
        catch (e: NullPointerException) {
          return@map NoCorrection(it)
        }

        if (bestSuggestion == null) {
          return@map NoCorrection(it)
        }
        else {
          wasCorrected = true
          return@map bestSuggestion
        }
      }
      .let { corrections ->
        val correctedQuery = corrections.joinToString(" ") { it.suggestion }
        val confidence = corrections.filterIsInstance<CorrectionSuggestion>().map { it.confidence }.average()

        CorrectionSuggestion(correctedQuery, confidence)
      }

    return if (wasCorrected) correction else null
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun tryCreateSuggestionRanker(): SuggestionRanker? {
    val languageModelService = project.service<ActionsLanguageModel>()
    if (!languageModelService.languageModel.isCompleted) return null

    return LinearAggregatingSuggestionRanker(
      JaroWinklerSuggestionRanker() to 0.5,
      FrequencySuggestionRanker(languageModelService.languageModel.getCompleted()) to 0.5,
    )
  }

  private fun getBestSuggestion(word: String, suggestions: LinkedSet<String>): CorrectionSuggestion? {
    return suggestionRanker.let {
      if (suggestionRanker == null) suggestionRanker = tryCreateSuggestionRanker()

      suggestionRanker
    }?.score(word, suggestions)
      ?.toList()
      ?.maxBy { it.second }
      ?.let { CorrectionSuggestion(it.first, it.second) }
  }

  sealed class CorrectionResult(val suggestion: String, val confidence: Double)
  private class CorrectionSuggestion(suggestion: String, confidence: Double) : CorrectionResult(suggestion, confidence)
  private class NoCorrection(original: String): CorrectionResult(original, 1.0)
}

@Service(Service.Level.PROJECT)
internal class ActionsLanguageModel(private val project: Project) : Disposable {
  private val coroutineScope = CoroutineScope(EmptyCoroutineContext)

  val languageModel = computeLanguageModel()

  private fun getAllAvailableActions() = (ActionManager.getInstance() as ActionManagerImpl).let { actionManager ->
    actionManager.actionIds
      .asSequence()
      .mapNotNull { actionManager.getAction(it) }
      .filterNot { it is ActionGroup && !it.isSearchable }
  }

  private fun computeLanguageModel() = coroutineScope.async {
    withBackgroundProgress(project, TyposBundle.getMessage("progress.title.computing.language.model"), true) {
      withRawProgressReporter {
        (getWordsFromActions() + getWordsFromSettings())
          .filterNot { it.isEmpty() || it.isSingleCharacter() }
          .map { it.lowercase() }
          .map { it.filter { c -> c.isLetterOrDigit() } }
          .let { allWords ->
            allWords.distinct()
              .associateWith { word -> allWords.count { it == word } }
              .let { ActionWordsFrequencyMetadata(it) }
          }
      }
    }
  }

  private fun getWordsFromActions() = getAllAvailableActions()
    .mapNotNull { it.templateText }
    .flatMap { it.split(" ") }


  private fun getWordsFromSettings() = ShowSettingsUtilImpl.getConfigurables(project, true,true)
    .asSequence()
    .filterIsInstance<SearchableConfigurable>()
    .flatMap { it.displayName.split(" ") }

  override fun dispose() {
    coroutineScope.cancel()
  }

  private class ModelStartupActivity : StartupActivity.Background {
    override fun runActivity(project: Project) {
      project.service<ActionsLanguageModel>()
    }
  }
}

internal class ActionWordsFrequencyMetadata(private val model: Map<String, Int>) : FrequencyMetadata {
  override val defaultFrequency: Int = 0
  override val maxFrequency: Int = model.values.max()

  override fun getFrequency(word: String): Int? = model[word]
}

private fun String.isSingleCharacter(): Boolean = this.length == 1
