// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.intellij.spellchecker.grazie

import ai.grazie.nlp.langs.alphabet.Alphabet
import ai.grazie.nlp.phonetics.metaphone.DoubleMetaphone
import ai.grazie.spell.GrazieSpeller
import ai.grazie.spell.GrazieSplittingSpeller
import ai.grazie.spell.dictionary.RuleDictionary
import ai.grazie.spell.dictionary.rule.IgnoreRuleDictionary
import ai.grazie.spell.lists.hunspell.HunspellWordList
import ai.grazie.spell.suggestion.filter.feature.RadiusSuggestionFilter
import ai.grazie.spell.suggestion.ranker.*
import ai.grazie.spell.utils.DictionaryResources
import ai.grazie.utils.mpp.FromResourcesDataLoader
import ai.grazie.utils.mpp.Resources
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.io.FileUtil
import com.intellij.spellchecker.SpellCheckerManager
import com.intellij.spellchecker.dictionary.Dictionary
import com.intellij.spellchecker.dictionary.EditableDictionary
import com.intellij.spellchecker.dictionary.Loader
import com.intellij.spellchecker.engine.SpellCheckerEngine
import com.intellij.spellchecker.engine.SpellCheckerEngineListener
import com.intellij.spellchecker.engine.Transformation
import com.intellij.spellchecker.grazie.async.WordListLoader
import com.intellij.spellchecker.grazie.dictionary.ExtendedWordListWithFrequency
import com.intellij.spellchecker.grazie.dictionary.WordListAdapter
import kotlinx.coroutines.*

@Service(Service.Level.PROJECT)
internal class GrazieSpellCheckerEngine(project: Project, private val coroutineScope: CoroutineScope) : SpellCheckerEngine, Disposable {
  override fun getTransformation(): Transformation = Transformation()

  private val loader = WordListLoader(project, coroutineScope)
  private val adapter = WordListAdapter()

  internal class SpellerLoadActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
      // what for do we join?
      // 1. to make sure that in unit test mode speller will be fully loaded before use
      // 2. SpellCheckerManager uses it
      project.service<GrazieSpellCheckerEngine>().deferredSpeller.join()
      // preload
      if (!ApplicationManager.getApplication().isUnitTestMode) {
        SpellCheckerManager.getInstance(project)
      }
    }
  }

  private val deferredSpeller: Deferred<GrazieSplittingSpeller> = coroutineScope.async {
    val speller = GrazieSplittingSpeller(speller = GrazieSpeller(createSpellerConfig()), config = GrazieSplittingSpeller.UserConfig())
    coroutineScope.launch {
      readAction {
        project.messageBus.syncPublisher(SpellCheckerEngineListener.TOPIC).onSpellerInitialized()
      }
    }
    speller
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private val suggestionCache = Caffeine.newBuilder().maximumSize(1024).build<SuggestionRequest, List<String>> { request ->
    val speller = deferredSpeller.getCompleted()
    synchronized(speller) {
      speller.suggest(request.word, request.maxSuggestions).take(request.maxSuggestions)
    }
  }

  private val speller: GrazieSplittingSpeller?
    get() = if (deferredSpeller.isCompleted) deferredSpeller.getCompleted() else null

  override fun dispose() {
    coroutineScope.cancel()
  }

  private suspend fun createSpellerConfig(): GrazieSpeller.UserConfig {
    val path = "/dictionary/en"
    val wordList = ExtendedWordListWithFrequency(
      HunspellWordList.create(
        Resources.text("$path.aff"),
        Resources.text("$path.dic"),
        checkCanceled = { ProgressManager.checkCanceled() }
      ),
      adapter
    )
    val dictionary = GrazieSpeller.UserConfig.Dictionary(
      dictionary = wordList,
      rules = RuleDictionary.Aggregated(
        IgnoreRuleDictionary.standard(tooShortLength = 2),
        DictionaryResources.getReplacingRules("/rule/en", FromResourcesDataLoader)
      ),
      isAlien = { !Alphabet.ENGLISH.matchAny(it) && adapter.isAlien(it) }
    )
    return GrazieSpeller.UserConfig(
      dictionary,
      model = GrazieSpeller.UserConfig.Model(
        filter = RadiusSuggestionFilter(0.05),
        ranker = LinearAggregatingSuggestionRanker(
          JaroWinklerSuggestionRanker() to 0.43,
          LevenshteinSuggestionRanker() to 0.20,
          PhoneticSuggestionRanker(DoubleMetaphone()) to 0.11,
          FrequencySuggestionRanker(wordList) to 0.23
        )
      )
    )
  }

  override fun isDictionaryLoad(name: String) = adapter.containsSource(name)

  override fun loadDictionary(loader: Loader) {
    this.loader.loadWordList(loader, adapter::addList)
  }

  override fun addDictionary(dictionary: Dictionary) {
    adapter.addDictionary(dictionary)
  }

  override fun addModifiableDictionary(dictionary: EditableDictionary) {
    addDictionary(dictionary)
  }

  override fun isCorrect(word: String): Boolean {
    val speller = speller ?: return true
    if (speller.isAlien(word)) {
      return true
    }
    return !speller.isMisspelled(word = word, caseSensitive = false)
  }

  override fun getSuggestions(word: String, maxSuggestions: Int, maxMetrics: Int): List<String> {
    if (!deferredSpeller.isCompleted) {
      return emptyList()
    }
    return suggestionCache.get(SuggestionRequest(word, maxSuggestions))
  }

  override fun reset() {
    adapter.reset()
  }

  override fun removeDictionary(name: String) {
    adapter.removeSource(name)
  }

  override fun getVariants(prefix: String): List<String> = emptyList()

  override fun removeDictionariesRecursively(directory: String) {
    val toRemove = adapter.names.filter { name ->
      FileUtil.isAncestor(directory, name, false) && isDictionaryLoad(name)
    }

    for (name in toRemove) {
      adapter.removeSource(name)
    }
  }
}

private data class SuggestionRequest(@JvmField val word: String, @JvmField val maxSuggestions: Int)