// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.utils

import java.time.temporal.ChronoUnit

/**
 * Helper will assist you to parse time and timeunit for yours test command
 * Supported units - 'ms' for millis, 's' for second, 'm' for minutes
 */
class TimeArgumentParserUtil {

  companion object {
    private val ARGS_PATTERN = Regex("^([0-9]*)(ms|s|m)\$")
    private val POSSIBLE_VALUES = mapOf(
      Pair("ms", ChronoUnit.MILLIS),
      Pair("s", ChronoUnit.SECONDS),
      Pair("m", ChronoUnit.MINUTES)
    )

    /**
     * @input - string which contains time and timeunit '4s'
     */
    fun parse(input: String): Pair<Long, ChronoUnit> {
      val (_, time, timeUnit) = ARGS_PATTERN.find(input)?.groupValues
                                ?: throw RuntimeException("Can't parse input $input\n Correct format is '4ms'(or s or m)")
      return Pair(time.toLong(), POSSIBLE_VALUES[timeUnit]
                                 ?: throw RuntimeException("Timeunit '$timeUnit' isn't supported. Must be one of $POSSIBLE_VALUES"))
    }
  }

}