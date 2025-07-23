// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.hunspell

import ai.grazie.nlp.langs.Language
import ai.grazie.nlp.langs.LanguageISO
import ai.grazie.nlp.langs.alphabet.Alphabet
import ai.grazie.spell.lists.hunspell.HunspellWordList
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.spellchecker.dictionary.Dictionary
import com.intellij.util.Consumer
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStreamReader

internal data class HunspellBundle(val dic: File, val aff: File, val trigrams: File)

class HunspellDictionary : Dictionary {
  companion object {
    private fun loadHunspellBundle(dicPath: String): HunspellBundle? {
      if (FileUtilRt.getExtension(dicPath) != "dic") return null
      val (dic, aff, trigrams) = getHunspellPaths(dicPath)
      return if (dic.exists() && aff.exists()) HunspellBundle(dic, aff, trigrams) else null
    }

    fun isHunspell(path: String): Boolean {
      return loadHunspellBundle(path) != null
    }

    fun getHunspellPaths(dicPath: String): Triple<File, File, File> {
      val path = FileUtilRt.getNameWithoutExtension(dicPath)
      return Triple(
        File("$path.dic"),
        File("$path.aff"),
        File("$path.trigrams.txt")
      )
    }
  }

  @JvmOverloads
  constructor(path: String, name: String? = null, language: LanguageISO? = null) {
    val bundle = loadHunspellBundle(path)
    if (bundle == null) throw FileNotFoundException("File '$path' not found")

    this.name = name ?: path
    this.language = language
    this.alphabet = if (this.language == null) null else Language.entries.firstOrNull { it.iso == this.language }?.alphabet
    this.dict = HunspellWordList.create(
      bundle.aff.readText(),
      bundle.dic.readText(),
      if (bundle.trigrams.exists()) bundle.trigrams.readLines() else null
    ) { ProgressManager.checkCanceled() }

    if (this.alphabet == null) {
      InputStreamReader(bundle.dic.inputStream()).use { reader ->
        reader.forEachLine { line ->
          line.takeWhile { it != ' ' && it != '/' }.lowercase().chars().forEach { this.letters.add(it) }
        }
      }
    }
  }

  constructor(dic: String, aff: String, trigrams: List<String>?, name: String, language: LanguageISO) {
    check(dic.isNotEmpty()) { "Dictionary must not be empty string" }
    check(aff.isNotEmpty()) { "Affix must not be empty string" }

    this.name = name
    this.language = language
    this.alphabet = Language.entries.firstOrNull { it.iso == language }?.alphabet
    this.dict = HunspellWordList.create(
      aff,
      dic,
      trigrams
    ) { ProgressManager.checkCanceled() }

    if (this.alphabet == null) {
      dic.lines().forEach { line ->
        line.takeWhile { it != ' ' && it != '/' }.lowercase().chars().forEach { this.letters.add(it) }
      }
    }
  }

  private val name: String
  val dict: HunspellWordList
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

  private fun isAlien(inputWord: String): Boolean {
    //todo use grazie-platform's updated (282+) dictionaries in newer versions
    val word = if (inputWord.endsWith('-')) inputWord.substring(0, inputWord.length - 1) else inputWord
    // Mark a word as alien if it contains non-alphabetical characters
    if (this.alphabet != null) return !this.alphabet.matchEntire(word)
    return word.lowercase().chars().anyMatch { it !in this.letters }
  }
}
