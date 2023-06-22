// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.providers.slf4j

internal const val SIMPLE_LOGGER_PROPERTIES_CONFIG: String = "simplelogger.properties"

internal const val SIMPLE_LOGGER_LOG_PREFIX: String = "org.slf4j.simpleLogger.log."

// key names to default value
internal val SIMPLE_LOGGER_PROPERTIES: Map<String, String> = linkedMapOf(
  "org.slf4j.simpleLogger.logFile" to "System.err",
  "org.slf4j.simpleLogger.cacheOutputStream" to "",
  "org.slf4j.simpleLogger.defaultLogLevel" to "info",
  "org.slf4j.simpleLogger.showDateTime" to "false",
  "org.slf4j.simpleLogger.dateTimeFormat" to "",
  "org.slf4j.simpleLogger.showShortLogName" to "false",
  "org.slf4j.simpleLogger.showThreadName" to "true",
  "org.slf4j.simpleLogger.showLogName" to "true",
  "org.slf4j.simpleLogger.showShortLogName" to "false",
  "org.slf4j.simpleLogger.levelInBrackets" to "false",
  "org.slf4j.simpleLogger.warnLevelString" to "WARN"
)