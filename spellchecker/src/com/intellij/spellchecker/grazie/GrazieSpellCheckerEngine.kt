// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.grazie

import ai.grazie.spell.GrazieSpeller
import ai.grazie.spell.GrazieSplittingSpeller
import ai.grazie.spell.language.English
import ai.grazie.spell.utils.DictionaryResources
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
import org.apache.lucene.analysis.hunspell.TimeoutPolicy

internal class GrazieSpellCheckerEngine(project: Project) : SpellCheckerEngine {
  override fun getTransformation(): Transformation = Transformation()

  private val loader = WordListLoader(project)

  private val adapter = WordListAdapter()

  private val mySpeller: GrazieAsyncSpeller = GrazieAsyncSpeller(project) {
    GrazieSplittingSpeller(
      GrazieSpeller(
        GrazieSpeller.UserConfig(
          GrazieSpeller.UserConfig.Dictionary(
            dictionary = ExtendedWordListWithFrequency(
              DictionaryResources.getHunspellDict("/dictionary/en", TimeoutPolicy.NO_TIMEOUT) { ProgressManager.checkCanceled() },
              adapter),
            isAlien = { word -> English.isAlien(word) && adapter.isAlien(word) }
          )
        )
      ),
      GrazieSplittingSpeller.UserConfig()
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
