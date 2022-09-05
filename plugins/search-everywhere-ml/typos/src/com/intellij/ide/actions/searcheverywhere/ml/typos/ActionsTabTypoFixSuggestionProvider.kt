package com.intellij.ide.actions.searcheverywhere.ml.typos

import ai.grazie.spell.lists.FrequencyMetadata
import ai.grazie.spell.suggestion.ranker.FrequencySuggestionRanker
import ai.grazie.spell.suggestion.ranker.JaroWinklerSuggestionRanker
import ai.grazie.spell.suggestion.ranker.LinearAggregatingSuggestionRanker
import ai.grazie.spell.suggestion.ranker.SuggestionRanker
import com.intellij.codeInspection.ui.actions.LOG
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

  fun suggestFixFor(query: String): String? {
    var wasCorrected = false
    val corrected = query.split(" ")
      .filter { it.isNotBlank() }
      .joinToString(" ") {
        if (!spellChecker.hasProblem(it)) return@joinToString it

        val bestSuggestion = try {
          getBestSuggestion(it, spellChecker.getSuggestions(it).toLinkedSet())
        }
        catch (e: NullPointerException) {
          return@joinToString it
        }

        if (bestSuggestion == null) {
          return@joinToString it
        }
        else {
          wasCorrected = true
          return@joinToString bestSuggestion
        }
      }

    return if (wasCorrected) corrected else null
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

  private fun getBestSuggestion(word: String, suggestions: LinkedSet<String>): String? {
    return suggestionRanker.let {
      if (suggestionRanker == null) suggestionRanker = tryCreateSuggestionRanker()

      suggestionRanker
    }?.rank(word, suggestions)
      ?.first()
      ?.also {
        val score = suggestionRanker!!.score(word, suggestions)
        LOG.info("Fixing $word to $it - probability $score")
      }
  }
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
