package com.intellij.ide.starter.utils

import org.apache.commons.lang3.CharUtils

private val OFFSET: Int = "AAAAAAA".hashCode();

fun convertToHashCodeWithOnlyLetters(hash: Int): String {
  val offsettedHash = (hash - OFFSET).toLong()
  var longHash: Long = offsettedHash and 0xFFFFFFFFL

  val generatedChars = CharArray(7)
  val aChar: Char = 'A'

  generatedChars.indices.forEach { index ->
    generatedChars[6 - index] = (aChar + ((longHash % 31).toInt()))
    longHash /= 31;
  }

  return generatedChars.filter { CharUtils.isAsciiAlphanumeric(it) }.joinToString(separator = "")
}

/**
 * Simplifies test grouping by replacing numbers, hashes, hex numbers with predefined constant values <ID>, <HASH>, <HEX>
 *  Eg:
 *  text@3ba5aac, text => text<ID>, text
 *  some-text.db451f59 => some-text.<HASH>
 *  0x01 => <HEX>
 *  text1234text => text<NUM>text
 **/
fun generifyErrorMessage(originalMessage: String): String {
  return originalMessage // text@3ba5aac, text => text<ID>, text
    .replace("[\$@#][A-Za-z0-9-_]+".toRegex(), "<ID>") // some-text.db451f59 => some-text.<HASH>
    .replace("[.]([A-Za-z]+[0-9]|[0-9]+[A-Za-z])[A-Za-z0-9]*".toRegex(), ".<HASH>") // 0x01 => <HEX>
    .replace("0x[0-9a-fA-F]+".toRegex(), "<HEX>") // text1234text => text<NUM>text
    .replace("[0-9]+".toRegex(), "<NUM>")
}