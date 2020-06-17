// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.grazie

import com.intellij.grazie.speller.GrazieSpeller
import com.intellij.grazie.speller.GrazieSplittingSpeller
import com.intellij.grazie.speller.Speller
import com.intellij.grazie.speller.dictionary.Dictionary.Aggregated
import com.intellij.grazie.speller.dictionary.transformation.TransformedDictionary
import com.intellij.grazie.speller.dictionary.transformation.TransformingDictionary
import com.intellij.grazie.speller.dictionary.transformation.WordTransformation
import com.intellij.grazie.speller.language.English
import com.intellij.grazie.speller.suggestion.filter.ChainSuggestionFilter
import com.intellij.grazie.speller.suggestion.filter.feature.CasingSuggestionFilter
import com.intellij.grazie.speller.suggestion.filter.feature.ListSuggestionFilter
import com.intellij.grazie.speller.utils.DictionaryResources
import com.intellij.grazie.speller.utils.spitter.CamelCaseSplitter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.spellchecker.dictionary.Dictionary
import com.intellij.spellchecker.dictionary.EditableDictionary
import com.intellij.spellchecker.dictionary.Loader
import com.intellij.spellchecker.engine.SpellCheckerEngine
import com.intellij.spellchecker.engine.Transformation
import com.intellij.spellchecker.grazie.async.GrazieAsyncSpeller
import com.intellij.spellchecker.grazie.async.WordListLoader
import com.intellij.spellchecker.grazie.dictionary.WordListAdapter
import java.util.*

internal class GrazieSpellCheckerEngine(project: Project) : SpellCheckerEngine {
  override fun getTransformation(): Transformation = Transformation()

  private val loader = WordListLoader(project)

  private val adapter = WordListAdapter()

  private val transformation = WordTransformation.LowerCase(Locale.ENGLISH)

  private val mySpeller: Speller = GrazieAsyncSpeller(project) {
    GrazieSplittingSpeller(
      GrazieSpeller(
        GrazieSpeller.UserConfig(
          dictionaries = GrazieSpeller.UserConfig.Dictionaries(
            transformation = transformation,
            suggested = Aggregated(
              //transform main dictionary -- add lower-cased versions for misspelled check
              TransformedDictionary(English.Lists.suggested, transformation),
              //add splits to support camel-cased words
              DictionaryResources.getSplitsDictionary(English.Lists.suggested, transformation, CamelCaseSplitter),
              //Should not be transformed, since it is already in lower case
              TransformingDictionary(adapter, transformation)
            ),
            splitter = CamelCaseSplitter
          ),
          model = GrazieSpeller.UserConfig.Model(
            filter = ChainSuggestionFilter(
              ListSuggestionFilter(English.Lists.excluded),
              CasingSuggestionFilter(Locale.ENGLISH, 1)
            )
          )
        )
      ),
      GrazieSplittingSpeller.UserConfig(splitter = CamelCaseSplitter)
    )
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

    return mySpeller.isMisspelled(word).not()
  }

  override fun getSuggestions(word: String, maxSuggestions: Int, maxMetrics: Int): List<String> {
    return mySpeller.suggest(word).take(maxSuggestions).toMutableList()
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
