// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.intellij.spellchecker.grazie

import ai.grazie.annotation.TestOnly
import ai.grazie.nlp.langs.Language
import ai.grazie.nlp.langs.LanguageISO
import ai.grazie.nlp.langs.alphabet.Alphabet
import ai.grazie.nlp.phonetics.metaphone.DoubleMetaphone
import ai.grazie.nlp.utils.normalization.StripAccentsNormalizer
import ai.grazie.spell.GrazieSpeller
import ai.grazie.spell.GrazieSplittingSpeller
import ai.grazie.spell.dictionary.RuleDictionary
import ai.grazie.spell.dictionary.rule.IgnoreRuleDictionary
import ai.grazie.spell.language.LanguageModel
import ai.grazie.spell.lists.WordListWithFrequency
import ai.grazie.spell.suggestion.filter.feature.RadiusSuggestionFilter
import ai.grazie.spell.suggestion.ranker.*
import ai.grazie.spell.utils.DictionaryResources
import ai.grazie.utils.mpp.FromResourcesDataLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionNotApplicableException
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
import com.intellij.spellchecker.grazie.ranker.DiacriticSuggestionRanker
import com.intellij.spellchecker.hunspell.HunspellDictionary
import kotlinx.coroutines.*

private const val MAX_WORD_LENGTH = 32

@Service(Service.Level.PROJECT)
class GrazieSpellCheckerEngine(
  project: Project,
  private val coroutineScope: CoroutineScope
): SpellCheckerEngine, Disposable {

  companion object {
    val enDictionary: HunspellDictionary by lazy {
      val classLoader = HunspellDictionary::class.java.classLoader
      val dicContent = classLoader.getResourceAsStream("dictionary/en.dic")!!.bufferedReader().readText()
      val affContent = classLoader.getResourceAsStream("dictionary/en.aff")!!.bufferedReader().readText()
      val trigramsContent = classLoader.getResourceAsStream("dictionary/en.trigrams.txt")?.bufferedReader()?.readLines()

      HunspellDictionary(
        dicContent, affContent, trigramsContent, "dictionary/en.dic", LanguageISO.EN
      )
    }
  }

  override fun getTransformation(): Transformation = Transformation()

  private val loader = WordListLoader(project, coroutineScope)
  private val adapter = WordListAdapter()

  internal class SpellerLoadActivity : ProjectActivity {
    init {
      // Do not preload speller in test mode, so it won't slow down tests not related to the spellchecker.
      // We will still load it in tests but only when it is actually needed.
      if (ApplicationManager.getApplication().isUnitTestMode) {
        throw ExtensionNotApplicableException.create()
      }
    }

    override suspend fun execute(project: Project) {
      project.serviceAsync<GrazieSpellCheckerEngine>().waitForSpeller()
      project.serviceAsync<SpellCheckerManager>()

      // heavy classloading to avoid freezes from FJP thread starvation
      for (lifecycle in LIFECYCLE_EP_NAME.extensionList) {
        lifecycle.preload(project)
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

  suspend fun waitForSpeller() {
    deferredSpeller.join()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private val suggestionCache = Caffeine.newBuilder().maximumSize(1024).build<SuggestionRequest, List<String>> { request ->
    val speller = deferredSpeller.getCompleted()
    synchronized(speller) {
      speller.suggest(request.word, request.maxSuggestions).take(request.maxSuggestions)
    }
  }

  val speller: GrazieSplittingSpeller?
    get() = if (deferredSpeller.isCompleted) deferredSpeller.getCompleted() else null

  override fun dispose() {
    coroutineScope.cancel()
  }

  private suspend fun createSpellerConfig(): GrazieSpeller.UserConfig {
    val wordList = ExtendedWordListWithFrequency(enDictionary.dict, adapter)
    return GrazieSpeller.UserConfig(model = buildModel(Language.ENGLISH, wordList))
  }

  private suspend fun buildModel(language: Language, wordList: WordListWithFrequency): LanguageModel {
    return LanguageModel(
      language = language,
      words = wordList,
      rules = RuleDictionary.Aggregated(
        IgnoreRuleDictionary.standard(tooShortLength = 2),
        DictionaryResources.getReplacingRules("/rule/en", FromResourcesDataLoader)
      ),
      ranker = DiacriticSuggestionRanker(
        LinearAggregatingSuggestionRanker(
          JaroWinklerSuggestionRanker() to 0.43,
          LevenshteinSuggestionRanker() to 0.20,
          PhoneticSuggestionRanker(DoubleMetaphone()) to 0.11,
          FrequencySuggestionRanker(wordList) to 0.23
        )
      ),
      filter = RadiusSuggestionFilter(0.05),
      normalizer = StripAccentsNormalizer(),
      isAlien = { !Alphabet.ENGLISH.matchAny(it) && adapter.isAlien(it) }
    )
  }

  override fun isDictionaryLoad(name: String): Boolean = adapter.containsSource(name)

  override fun getDictionaryNames(): Set<String> = adapter.names

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
    if (word.length > MAX_WORD_LENGTH) {
      return true
    }
    if (speller.isAlien(word)) {
      return true
    }
    return !speller.isMisspelled(word = word, caseSensitive = false)
  }

  override fun getSuggestions(word: String, maxSuggestions: Int, maxMetrics: Int): List<String> {
    if (!deferredSpeller.isCompleted) {
      return emptyList()
    }
    if (word.length > MAX_WORD_LENGTH) {
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

  @TestOnly
  fun dropSuggestionCache() {
    suggestionCache.invalidateAll()
  }
}

private data class SuggestionRequest(@JvmField val word: String, @JvmField val maxSuggestions: Int)