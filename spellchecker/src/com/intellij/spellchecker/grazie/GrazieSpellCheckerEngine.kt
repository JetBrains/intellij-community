// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.spellchecker.dictionary.Dictionary
import com.intellij.spellchecker.dictionary.EditableDictionary
import com.intellij.spellchecker.dictionary.Loader
import com.intellij.spellchecker.engine.SpellCheckerEngine
import com.intellij.spellchecker.engine.Transformation
import com.intellij.spellchecker.grazie.async.GrazieAsyncSpeller
import com.intellij.spellchecker.grazie.async.WordListLoader
import com.intellij.spellchecker.grazie.dictionary.ExtendedWordListWithFrequency
import com.intellij.spellchecker.grazie.dictionary.WordListAdapter
import com.intellij.util.containers.SLRUCache
import kotlinx.coroutines.runBlocking

internal class GrazieSpellCheckerEngine(project: Project) : SpellCheckerEngine {
  override fun getTransformation(): Transformation = Transformation()

  private val loader = WordListLoader(project)

  private val adapter = WordListAdapter()

  private val mySpeller: GrazieAsyncSpeller = GrazieAsyncSpeller(project) {
    GrazieSplittingSpeller(
      GrazieSpeller(createSpellerConfig()),
      GrazieSplittingSpeller.UserConfig()
    )
  }

  private fun createSpellerConfig(): GrazieSpeller.UserConfig {
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
        runBlocking { DictionaryResources.getReplacingRules("/rule/en", FromResourcesDataLoader) }
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

  private data class SuggestionsRequest(val word: String, val maxSuggestions: Int)
  private val suggestionsCache = SLRUCache.create<SuggestionsRequest, List<String>>(1024, 1024) { request ->
    mySpeller.suggest(request.word, request.maxSuggestions).take(request.maxSuggestions)
  }

  override fun isDictionaryLoad(name: String) = adapter.containsSource(name)

  override fun loadDictionary(loader: Loader) {
    this.loader.loadWordList(loader) { name, list ->
      adapter.addList(name, list)
    }
  }

  override fun addDictionary(dictionary: Dictionary) {
    adapter.addDictionary(dictionary)
  }

  override fun addModifiableDictionary(dictionary: EditableDictionary) {
    addDictionary(dictionary)
  }

  override fun isCorrect(word: String): Boolean {
    if (mySpeller.isAlien(word)) return true

    return mySpeller.isMisspelled(word, false).not()
  }

  override fun getSuggestions(word: String, maxSuggestions: Int, maxMetrics: Int): List<String> {
    if (mySpeller.isCreated) {
      return synchronized(mySpeller) {
        suggestionsCache.get(SuggestionsRequest(word, maxSuggestions))
      }
    }

    return emptyList()
  }

  override fun reset() {
    adapter.reset()
  }

  override fun removeDictionary(name: String) {
    adapter.removeSource(name)
  }

  override fun getVariants(prefix: String): List<String> = emptyList()

  override fun removeDictionariesRecursively(directory: String) {
    val toRemove: List<String> = adapter.names.filter { name: String ->
      FileUtil.isAncestor(directory, name, false) && isDictionaryLoad(name)
    }

    for (name in toRemove) {
      adapter.removeSource(name)
    }
  }
}
