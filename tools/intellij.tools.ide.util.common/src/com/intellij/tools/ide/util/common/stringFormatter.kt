package com.intellij.tools.ide.util.common

/** Leave only numbers and characters */
fun String.replaceSpecialCharacters(newValue: String, vararg ignoreSymbols: String): String {
  val regex = Regex("[^a-zA-Z0-9${ignoreSymbols.joinToString(separator = "")}]")

  return this
    .replace(regex, newValue)
}

/** Leave only numbers and characters.
 * Replace everything else with hyphens.
 */
fun String.replaceSpecialCharactersWithHyphens(ignoreSymbols: List<String> = listOf(".", "/", """\\""")): String {
  return this
    .replaceSpecialCharacters(newValue = "-", *ignoreSymbols.toTypedArray())
    .replace("[-]+".toRegex(), "-")
    .removePrefix("-")
    .removeSuffix("-")
}
