// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.hunspell

import ai.grazie.nlp.langs.Language
import ai.grazie.nlp.langs.LanguageISO
import ai.grazie.nlp.langs.alphabet.Alphabet
import ai.grazie.spell.dictionary.RuleDictionary
import ai.grazie.spell.dictionary.rule.ReplacingRuleDictionary
import ai.grazie.spell.lists.hunspell.HunspellWordList
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.spellchecker.dictionary.Dictionary
import com.intellij.util.Consumer
import java.io.File
import kotlin.io.path.Path

data class HunspellBundle(val dic: File, val aff: File, val trigrams: File, val replacingRules: File?) {
  fun dictionaryExists(): Boolean = dic.exists() && aff.exists()
}

class HunspellDictionary : Dictionary {
  companion object {
    fun getHunspellBundle(dicPath: String): HunspellBundle {
      val path = FileUtilRt.getNameWithoutExtension(dicPath)
      val filename = Path(path).fileName.toString()
      return HunspellBundle(
        File("$path.dic"),
        File("$path.aff"),
        File("$path.trigrams.txt"),
        Path(path).parent.resolve("../rule/$filename.dat").toFile()
      )
    }

    fun isHunspell(path: String): Boolean {
      return getHunspellBundle(path).dictionaryExists()
    }
  }

  @JvmOverloads
  constructor(path: String, name: String? = null, language: LanguageISO? = null) {
    val paths = getHunspellBundle(path)
    check(paths.dictionaryExists()) { "File '$path' not found" }

    val (dic, aff, trigrams, replacingRules) = paths

    this.name = name ?: path
    this.language = language
    this.alphabet = if (this.language == null) null else Language.entries.firstOrNull { it.iso == this.language }?.alphabet
    this.dict = HunspellWordList.create(
      aff.readText(),
      dic.readText(),
      if (trigrams.exists()) trigrams.readLines() else null
    ) { ProgressManager.checkCanceled() }
    this.ruleDictionary = if (replacingRules?.exists() == true) parseReplacingRules(replacingRules.readText()) else null
    buildAlphabet(dic.readText())
  }

  constructor(dic: String, aff: String, trigrams: List<String>?, name: String, language: LanguageISO, replacingRules: String?) {
    check(dic.isNotEmpty()) { "Dictionary must not be empty string" }
    check(aff.isNotEmpty()) { "Affix must not be empty string" }

    this.name = name
    this.language = language
    this.alphabet = Language.entries.firstOrNull { it.iso == language }?.alphabet
    this.dict = HunspellWordList.create(aff, dic, trigrams) { ProgressManager.checkCanceled() }
    this.ruleDictionary = if (replacingRules != null) parseReplacingRules(replacingRules) else null
    buildAlphabet(dic)
  }

  private val name: String
  val dict: HunspellWordList
  val ruleDictionary: RuleDictionary?
  private val letters: HashSet<Int> = HashSet()
  private val alphabet: Alphabet?
  private val language: LanguageISO?

  fun language(): String? = language?.name ?: dict.language()

  override fun getName() = name

  override fun contains(word: String): Boolean? {
    if (isAlien(word)) return null
    if (dict.contains(word, false)) return true
    return false
  }

  override fun consumeSuggestions(word: String, consumer: Consumer<String>) {
    for (suggestion in dict.suggest(word)) {
      consumer.consume(suggestion)
    }
  }

  override fun getWords(): MutableSet<String> = throw UnsupportedOperationException()

  private fun isAlien(word: String): Boolean {
    // Mark a word as alien if it contains non-alphabetical characters
    if (this.alphabet != null) return !this.alphabet.matchAny(word)
    return word.lowercase().chars().anyMatch { it !in this.letters }
  }

  private fun buildAlphabet(dic: String) {
    if (this.alphabet == null) {
      dic.lines().forEach { line ->
        line.takeWhile { it != ' ' && it != '/' }.lowercase().chars().forEach { this.letters.add(it) }
      }
    }
  }

  // todo: Use DictionaryResources#parseReplacingRules when grazie-platform is updated to 0.8.25+
  fun parseReplacingRules(datRule: String): RuleDictionary {
    val rules = HashSet<ReplacingRuleDictionary.Descriptor>()

    for (line in datRule.lines().filter { it.isNotBlank() }) {
      val (incorrect, correct) = line.splitByDelimiter("->")
      rules.add(ReplacingRuleDictionary.Descriptor(incorrect.splitByDelimiter(), correct.splitByDelimiter()))
    }

    return ReplacingRuleDictionary(rules)
  }

  private fun String.splitByDelimiter(delimiter: String = ",") = this.split(delimiter).map { it.trim() }.toTypedArray()
}
