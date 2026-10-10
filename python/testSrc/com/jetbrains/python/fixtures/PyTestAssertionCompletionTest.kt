// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.fixtures

import com.intellij.idea.TestFor
import com.intellij.python.testAssertions.PyTestAssertionCompletion
import com.intellij.python.testAssertions.PyTestDirectiveKind
import com.intellij.python.testAssertions.PyTestDirectives
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@Subsystems.CodeInsight
@Layers.Functional
@TestFor(classes = [PyTestAssertionCompletion::class])
class PyTestAssertionCompletionTest {

  private val assertionNames = PyTestDirectives.all.filter { it.kind == PyTestDirectiveKind.ASSERTION }.map { it.name }

  @Test
  fun `no completion outside a comment`() {
    Assertions.assertNull(PyTestAssertionCompletion.complete("x = 1"))
  }

  @Test
  fun `markers after the hash of a comment line`() {
    assertCompletion("#", "", listOf("└", "\\"))
    assertCompletion("    #", "", listOf("└", "\\"))
  }

  @Test
  fun `corner marker after a hash and a space`() {
    assertCompletion("#   ", "", listOf("└"))
  }

  @Test
  fun `assertion directives after a marker`() {
    assertCompletion("#   └ ", "", assertionNames)
    assertCompletion("#   ^^^ TY", "TY", assertionNames)
    assertCompletion("#│  └TY", "TY", assertionNames)
    assertCompletion("#\\", "", assertionNames)
  }

  @Test
  fun `assertion directives in an inline comment`() {
    assertCompletion("x = 1 #", "", assertionNames)
    assertCompletion("x = 1 # WEA", "WEA", assertionNames)
  }

  @Test
  fun `assertion directives in an inline comment after a hash in a string`() {
    assertCompletion("s = \"#\" # TY", "TY", assertionNames)
  }

  @Test
  fun `modifiers after an assertion directive`() {
    assertCompletion("x = 1 # TYPE int ", "", listOf("FIXME"))
    assertCompletion("x = 1 # WARNING TO", "TO", listOf("TOOLTIP", "FIXME"))
    assertCompletion("x = 1 # WARNING Some text FI", "FI", listOf("FIXME"))
    assertCompletion("#   └ ERROR ", "", listOf("TOOLTIP", "FIXME"))
  }

  @Test
  fun `values after an assertion directive`() {
    assertCompletion("x = 1 # IS_BUILTIN ", "", listOf("FALSE", "FIXME"))
    assertCompletion("#   └ INFERRED_VARIANCE CO", "CO", PyTestDirectives.INFERRED_VARIANCE.values + "FIXME")
  }

  @Test
  fun `no completion in a plain comment`() {
    Assertions.assertNull(PyTestAssertionCompletion.complete("x = 1 # just a note "))
    Assertions.assertNull(PyTestAssertionCompletion.complete("# just a note "))
  }

  @Test
  fun `no completion in the trailing comment of an assertion`() {
    Assertions.assertNull(PyTestAssertionCompletion.complete("x = 1 # TYPE int # FI"))
  }

  private fun assertCompletion(linePrefix: String, expectedPrefix: String, expectedItems: List<String>) {
    val completion = PyTestAssertionCompletion.complete(linePrefix)
    Assertions.assertNotNull(completion, "No completion for '$linePrefix'")
    Assertions.assertEquals(expectedPrefix, completion!!.prefix, "Prefix for '$linePrefix'")
    Assertions.assertEquals(expectedItems, completion.items.map { it.text }, "Items for '$linePrefix'")
  }
}
