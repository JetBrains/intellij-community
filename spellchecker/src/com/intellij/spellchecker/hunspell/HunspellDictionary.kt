// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.hunspell

import ai.grazie.spell.lists.hunspell.HunspellWordList
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VfsUtil.findFileByIoFile
import com.intellij.spellchecker.dictionary.Dictionary
import com.intellij.util.Consumer
import org.apache.lucene.analysis.hunspell.TimeoutPolicy
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStreamReader

internal data class HunspellBundle(val dic: File, val aff: File)

class HunspellDictionary(path: String, name: String? = null) : Dictionary {
  companion object {
    private fun loadHunspellBundle(path: String): HunspellBundle? {
      if (FileUtilRt.getExtension(path) != "dic") return null

      val pathWithoutExtension = FileUtilRt.getNameWithoutExtension(path)
      val dic = File("$pathWithoutExtension.dic")
      val aff = File("$pathWithoutExtension.aff")

      return if (dic.exists() && aff.exists()) HunspellBundle(dic, aff) else null
    }

    fun isHunspell(path: String): Boolean {
      return loadHunspellBundle(path) !== null
    }
  }

  private val name: String
  private val dict: HunspellWordList
  private val alphabet: HashSet<Int> = HashSet()

  init {
    this.name = name ?: path

    val bundle = loadHunspellBundle(path)
    if (bundle !== null) {
      bundle.dic.inputStream().use { dic ->
        bundle.aff.inputStream().use { aff ->
          this.dict = HunspellWordList(aff, dic, TimeoutPolicy.NO_TIMEOUT) { ProgressManager.checkCanceled() }
        }
      }

      val file = findFileByIoFile(bundle.dic, true)!!
      InputStreamReader(file.inputStream, file.charset).use { reader ->
        reader.forEachLine { line ->
          line.takeWhile { it != ' ' && it != '/' }.lowercase().chars().forEach { this.alphabet.add(it) }
        }
      }
    }
    else throw FileNotFoundException("File '$path' not found")
  }

  fun language(): String? = dict.language()

  override fun getName() = name

  override fun contains(word: String): Boolean? {
    if (dict.contains(word, false)) return true
    // mark a word as alien if it contains non-alphabetical characters
    if (word.lowercase().chars().anyMatch { it !in this.alphabet }) return null
    return false
  }

  override fun consumeSuggestions(word: String, consumer: Consumer<String>) {
    for (suggestion in dict.suggest(word)) {
      consumer.consume(suggestion)
    }
  }

  override fun getWords(): MutableSet<String> = throw UnsupportedOperationException()
}
