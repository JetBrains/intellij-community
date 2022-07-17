package com.intellij.ide.starter.utils

private val OFFSET: Int = "AAAAAAA".hashCode()

fun convertToHashCodeWithOnlyLetters(hash: Int): String {
  val offsettedHash = (hash - OFFSET).toLong()
  var longHash: Long = offsettedHash and 0xFFFFFFFFL

  val generatedChars = CharArray(7)
  val aChar = 'A'

  generatedChars.indices.forEach { index ->
    generatedChars[6 - index] = (aChar + ((longHash % 31).toInt()))
    longHash /= 31
  }

  return generatedChars.filter { it.isLetterOrDigit() }.joinToString(separator = "")
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
  return originalMessage
    .generifyID()
    .generifyHash()
    .generifyHexCode()
    .generifyNumber()
}

/** text@3ba5aac, text => text<ID>, text */
fun String.generifyID(omitDollarSign: Boolean = false): String {
  val regexpStr = if (omitDollarSign) "[@#][A-Za-z\\d-_]+"
  else "[\$@#][A-Za-z\\d-_]+"

  return this.replace(regexpStr.toRegex(), "<ID>")
}

/** some-text.db451f59 => some-text.<HASH> */
fun String.generifyHash(): String = this
  .replace("[.]([A-Za-z]+\\d|\\d+[A-Za-z])[A-Za-z\\d]*".toRegex(), ".<HASH>")

/** 0x01 => <HEX> */
fun String.generifyHexCode(): String = this.replace("0x[\\da-fA-F]+".toRegex(), "<HEX>")

/** text1234text => text<NUM>text */
fun String.generifyNumber(): String = this.replace("\\d+".toRegex(), "<NUM>")
