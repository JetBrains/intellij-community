package com.intellij.lambda.testFramework.testApi.utils

import com.intellij.lambda.testFramework.frameworkLogger
import org.assertj.core.api.Assertions
import kotlin.math.min

fun formatDifference(difference: Difference): String {
  return "(first ${difference.index} non-blank lines are similar)\ne: '${difference.first}'" +
         "\na: '${difference.second}'"
}

fun findFirstDifference(firstText: String, secondText: String, excludes: List<String> = emptyList()): Difference? {
  val firstLines = firstText.lines().map { it.trimEnd() }.filter { it.isNotBlank() }.filterNot { excludes.any { e -> it.contains(e) } }
  val secondLines = secondText.lines().map { it.trimEnd() }.filter { it.isNotBlank() }.filterNot { excludes.any { e -> it.contains(e) } }
  for (i in 0 until min(firstLines.size, secondLines.size)) {
    if (firstLines[i] != secondLines[i]) {
      return Difference(i, firstLines[i], secondLines[i])
    }
  }
  if (firstLines.size > secondLines.size) {
    return Difference(secondLines.size, firstLines[secondLines.size], "")
  }
  if (secondLines.size > firstLines.size) {
    return Difference(firstLines.size, "", secondLines[firstLines.size])
  }
  return null
}

data class Difference(val index: Int, val first: String, val second: String)

fun assertNoDifference(title: String, actual: String, expected: String, excludes: List<String> = emptyList()) {
  frameworkLogger.info("Check that '${title}'")
  val difference = findFirstDifference(actual, expected, excludes)
  if (difference != null) {
    Assertions.fail<String>(
      "expected:\n" + "'$expected'" +
      "\ncurrent:\n" + "'$actual'" +
      "\ndifference: " + formatDifference(difference)
    )
  }
}
