package com.intellij.spellchecker.dataset

object Datasets {
  data class WordWithMisspellings(val word: String, val misspellings: LinkedHashSet<String>)

  private fun getLines(file: String) = javaClass.getResourceAsStream(file).use { it.reader().readLines().filter { line -> line.isNotBlank() } }

  private fun parseWordsFormat(lines: List<String>) = lines.map { line ->
    val (word, misspellings) = line.split(":")
    WordWithMisspellings(word, LinkedHashSet(misspellings.split(" ").filter { it.isNotBlank() }))
  }

  val missp: List<WordWithMisspellings> by lazy { parseWordsFormat(getLines("/data/missp.dat")) }
  val words: List<WordWithMisspellings> by lazy { parseWordsFormat(getLines("/data/words.dat")) }
  val wordsCamelCase: List<WordWithMisspellings> by lazy { parseWordsFormat(getLines("/data/words_camel_case.dat")) }
}
