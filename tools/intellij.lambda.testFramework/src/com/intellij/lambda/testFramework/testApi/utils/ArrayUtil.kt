package com.intellij.lambda.testFramework.testApi.utils

import com.intellij.lambda.testFramework.frameworkLogger

/**
 * Find median value in array.
 *
 * @param array array to process
 * @param amount number of elements to take from the middle of array. Take average over those values.
 *
 * @return [Int] Median value
 */
fun findMedianValue(array: Array<Int>, amount: Int = 1): Int {

  frameworkLogger.info("Pings array: [${array.joinToString()}], take <$amount> middle values")
  array.sort()

  if (amount >= array.size) {
    return array.average().toInt()
  }

  val middleIndex = array.size / 2
  val halfAmount = amount / 2
  val (leftAmount, rightAmount) =
    if (amount % 2 == 0) {
      halfAmount to halfAmount
    }
    else {
      halfAmount to amount - halfAmount
    }

  val leftIndexStartRaw = middleIndex - leftAmount
  val leftIndexStart = if (leftIndexStartRaw > 0) leftIndexStartRaw else 0
  val leftPart = array.sliceArray(leftIndexStart..<middleIndex)

  // Subtract 1 to exclude left value
  val rightIndexEndRaw = middleIndex + rightAmount - 1
  val rightIndexEnd = if (rightIndexEndRaw <= array.size - 1) rightIndexEndRaw else array.size - 1
  val rightPart = array.sliceArray(middleIndex..rightIndexEnd)

  val arrayToProcess = leftPart + rightPart
  frameworkLogger.info("<$amount> middle values to process: [${arrayToProcess.joinToString()}]")
  return arrayToProcess.average().toInt()
}
