// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.block.session.scraper

import org.jetbrains.plugins.terminal.block.session.scraper.CommandEndMarkerListeningStringCollector.Companion.indexOfSuffix
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CommandEndMarkerListeningStringCollectorTest {

  @Test
  fun indexOfSuffixTest() {
    // Positive tests
    assertEquals("012345".length, indexOfSuffix("0123456789", "679", ignoredCharacters = { it == '8' }))
    assertEquals("\nhello\n\n".length, indexOfSuffix("\nhello\n\nwor\nld\n\n", "world", ignoredCharacters = { it == '\n' }))
    assertEquals("lett".length, indexOfSuffix("letters12345678", "ers", ignoredCharacters = { it.isDigit() }))
    assertEquals(0, indexOfSuffix("exactmatch", "exactmatch", ignoredCharacters = { false }))

    // Negative tests
    assertEquals(-1, indexOfSuffix("hello world", "notpresent", ignoredCharacters = { false }))
    assertEquals(-1, indexOfSuffix("hello world", "hello", ignoredCharacters = { false }))
    assertEquals(-1, indexOfSuffix("hello worl\nd", "world", ignoredCharacters = { it == 'h' }))
    assertEquals(-1, indexOfSuffix("hello world", "hello hello world", ignoredCharacters = { false }))
    assertEquals(-1, indexOfSuffix("", "notpresent", ignoredCharacters = { false }))
    assertEquals(-1, indexOfSuffix("test substring not found", "substring", ignoredCharacters = { false }))
  }
}
