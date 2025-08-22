// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.intellij.spellchecker.grazie

import ai.grazie.nlp.langs.Language
import ai.grazie.nlp.langs.LanguageISO
import ai.grazie.nlp.langs.alphabet.Alphabet
import ai.grazie.nlp.utils.normalization.StripAccentsNormalizer
import ai.grazie.spell.GrazieSpeller
import ai.grazie.spell.GrazieSplittingSpeller
import ai.grazie.spell.Speller
import ai.grazie.spell.dictionary.RuleDictionary
import ai.grazie.spell.dictionary.rule.IgnoreRuleDictionary
import ai.grazie.spell.language.LanguageModel
import ai.grazie.spell.suggestion.filter.feature.RadiusSuggestionFilter
import ai.grazie.utils.mpp.Resources
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

private const val MAX_WORD_LENGTH = 32

@Service(Service.Level.PROJECT)
class GrazieSpellCheckerEngine(
  project: Project,
  private val coroutineScope: CoroutineScope,
) : SpellCheckerEngine, Disposable {

  companion object {
    val enDictionary: HunspellDictionary by lazy {
      val dic = Resources.text("/dictionary/en.dic")
      val aff = Resources.text("/dictionary/en.aff")
      val trigrams = Resources.text("/dictionary/en.trigrams.txt").lines()
      HunspellDictionary(
        dic, aff, trigrams, "/dictionary/en.dic", LanguageISO.EN, Resources.text("/rule/en.dat")
      )
    }
  }

  override fun getTransformation(): Transformation = Transformation()

  private val loader = WordListLoader(project, coroutineScope)
  private val adapter = WordListAdapter()

  internal class SpellerLoadActivity : ProjectActivity {
    init {
      // Do not preload the speller in test mode, so it won't slow down tests not related to the spellchecker.
      // We will still load it in tests but only when it is actually necessary.
      if (ApplicationManager.getApplication().isUnitTestMode) {
        throw ExtensionNotApplicableException.create()
      }
    }

    override suspend fun execute(project: Project) {
      project.serviceAsync<GrazieSpellCheckerEngine>().initializeSpeller(project)
      project.serviceAsync<SpellCheckerManager>()

      // heavy classloading to avoid freezes from FJP thread starvation
      for (lifecycle in LIFECYCLE_EP_NAME.extensionList) {
        lifecycle.preload(project)
      }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private val suggestionCache = Caffeine.newBuilder().maximumSize(1024).build<SuggestionRequest, List<String>> { request ->
    val speller = speller!!
    synchronized(speller) {
      speller.suggest(request.word, request.maxSuggestions).take(request.maxSuggestions)
    }
  }

  private fun createSpellerConfig(): GrazieSpeller.UserConfig {
    val wordList = ExtendedWordListWithFrequency(enDictionary.dict, adapter)
    return GrazieSpeller.UserConfig(model = LanguageModel(
      language = Language.ENGLISH,
      words = ExtendedWordListWithFrequency(enDictionary.dict, adapter),
      rules = RuleDictionary.Aggregated(this.replacingRules),
      ranker = DiacriticSuggestionRanker(LanguageModel.getRanker(Language.ENGLISH, wordList)),
      filter = RadiusSuggestionFilter(0.05),
      normalizer = StripAccentsNormalizer(),
      isAlien = { !Alphabet.ENGLISH.matchAny(it) && adapter.isAlien(it) }
    ))
  }

  @Volatile
  private var speller: Speller? = null
  private val replacingRules: Set<RuleDictionary> = getReplacingRules()

  @ApiStatus.Internal
  fun initializeSpeller(project: Project) {
    val speller = GrazieSplittingSpeller(
      speller = GrazieSpeller(createSpellerConfig()),
      config = GrazieSplittingSpeller.UserConfig()
    )
    if (this.speller == null) {
      coroutineScope.launch {
        readAction {
          project.messageBus.syncPublisher(SpellCheckerEngineListener.TOPIC).onSpellerInitialized()
        }
      }
    }
    this.speller = speller
  }

  fun getSpeller(): Speller? = speller

  override fun dispose() {
    coroutineScope.cancel()
  }

  override fun isDictionaryLoad(name: String): Boolean = adapter.containsSource(name)

  override fun getDictionaryNames(): Set<String> = adapter.dictionaryNames

  override fun loadDictionary(loader: Loader) {
    this.loader.loadWordList(loader, adapter::addList)
  }

  override fun addDictionary(dictionary: Dictionary) {
    if (!isDictionaryLoad(dictionary.name)) adapter.addDictionary(dictionary)
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
    if (speller == null) {
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

  private fun getReplacingRules(): Set<RuleDictionary> {
    return object : AbstractSet<RuleDictionary>() {
      override val size: Int
        get() = throw UnsupportedOperationException()

      override fun iterator(): Iterator<RuleDictionary> {
        val replacingRules = mutableSetOf(IgnoreRuleDictionary.standard(tooShortLength = 2), enDictionary.ruleDictionary!!)
        val hunspellReplacingRules = dictionaryNames
          .map { adapter.getDictionary(it) }
          .mapNotNull { (it as? HunspellDictionary)?.ruleDictionary }
          .toSet()
        return (replacingRules + hunspellReplacingRules).iterator()
      }
    }
  }
}

private data class SuggestionRequest(@JvmField val word: String, @JvmField val maxSuggestions: Int)