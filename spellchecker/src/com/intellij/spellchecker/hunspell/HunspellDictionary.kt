// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.hunspell

import ai.grazie.spell.lists.hunspell.HunspellWordList
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.spellchecker.dictionary.Dictionary
import com.intellij.util.Consumer
import java.io.File
import java.io.FileNotFoundException

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

  init {
    this.name = name ?: path

    val bundle = loadHunspellBundle(path)
    if (bundle !== null) {
      bundle.dic.inputStream().use { dic ->
        bundle.aff.inputStream().use { aff ->
          this.dict = HunspellWordList(aff, dic)
        }
      }
    }
    else throw FileNotFoundException("File '$path' not found")
  }

  fun language(): String? = dict.language()

  override fun getName() = name

  override fun contains(word: String): Boolean {
    return dict.contains(word, false)
  }

  override fun consumeSuggestions(word: String, consumer: Consumer<String>) {
    for (suggestion in dict.suggest(word)) {
      consumer.consume(suggestion)
    }
  }

  override fun getWords(): MutableSet<String> = throw UnsupportedOperationException()
}
