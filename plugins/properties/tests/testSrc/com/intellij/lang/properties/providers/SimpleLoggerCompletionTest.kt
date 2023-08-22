package com.intellij.lang.properties.providers

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SimpleLoggerCompletionTest : BasePlatformTestCase() {

  fun testCompletion() {
    myFixture.configureByText("simplelogger.properties", """
       org.slf4j.<caret>
    """.trimIndent())

    myFixture.testCompletionVariants("simplelogger.properties",
                                     "org.slf4j.simpleLogger.cacheOutputStream",
                                     "org.slf4j.simpleLogger.dateTimeFormat",
                                     "org.slf4j.simpleLogger.defaultLogLevel",
                                     "org.slf4j.simpleLogger.levelInBrackets",
                                     "org.slf4j.simpleLogger.logFile",
                                     "org.slf4j.simpleLogger.showDateTime",
                                     "org.slf4j.simpleLogger.showLogName",
                                     "org.slf4j.simpleLogger.showShortLogName",
                                     "org.slf4j.simpleLogger.showThreadName",
                                     "org.slf4j.simpleLogger.warnLevelString")
  }
}