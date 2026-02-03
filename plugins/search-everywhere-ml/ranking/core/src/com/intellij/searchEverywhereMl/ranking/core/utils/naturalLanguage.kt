package com.intellij.searchEverywhereMl.ranking.core.utils

// Equivalent to splitting by the following regexp: "(?<=[A-Z])(?=[A-Z][a-z])|(?<=[^A-Z])(?=[A-Z])|(?<=[A-Za-z])(?=[^A-Za-z])"
fun splitIdentifierIntoTokens(identifier: String, lowercase: Boolean = true): String {
  val result = buildString(capacity = identifier.length + 10) {
    var isPrevUppercase = false
    var isPrevLetter = false
    for (index in identifier.indices) {
      if (
        lastOrNull() != ' ' &&
        ((index < identifier.length - 1 && isPrevUppercase
          && identifier[index].isUpperCase() && (identifier[index + 1].isLetter() && !identifier[index + 1].isUpperCase()))
         || (index > 0 && !isPrevUppercase && identifier[index].isUpperCase())
         || (isPrevLetter && !identifier[index].isLetter()))
      ) {
        append(" ")
      }
      isPrevUppercase = identifier[index].isUpperCase()
      isPrevLetter = identifier[index].isLetter()
      if (identifier[index].isLetterOrDigit() ||
          (identifier[index] == ' ' && lastOrNull() != null && lastOrNull() != ' ')) {
        append(identifier[index])
      }
    }
    if (lastOrNull() == ' ') deleteAt(length - 1)
  }
  return if (lowercase) result.lowercase() else result
}

fun convertNameToNaturalLanguage(pattern: String): String {
  val meaningfulName = if (pattern.contains(".")) {
    pattern.split(".").dropLast(1).joinToString(".")
  }
  else pattern
  return splitIdentifierIntoTokens(meaningfulName)
}