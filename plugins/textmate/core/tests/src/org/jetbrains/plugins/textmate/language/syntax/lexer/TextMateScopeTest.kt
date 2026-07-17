package org.jetbrains.plugins.textmate.language.syntax.lexer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TextMateScopeTest {
  @Test
  fun equalScopeChainsAreEqual() {
    val scope1 = TextMateScope.EMPTY.add("source.test").add("string.quoted")
    val scope2 = TextMateScope.EMPTY.add("source.test").add("string.quoted")
    assertEquals(scope1, scope2)
    assertEquals(scope1.hashCode(), scope2.hashCode())
  }

  @Test
  fun scopesWithCollidingParentHashesAreNotEqual() {
    // "Aa" and "BB" have equal hash codes, so the two chains have equal hashes but differ in content;
    // equality must compare the parent chains, not only their hashes
    assertEquals("Aa".hashCode(), "BB".hashCode())
    val scope1 = TextMateScope.EMPTY.add("Aa").add("scope.test")
    val scope2 = TextMateScope.EMPTY.add("BB").add("scope.test")
    assertNotEquals(scope1, scope2)
  }

  @Test
  fun scopesWithDifferentDepthAreNotEqual() {
    val scope1 = TextMateScope.EMPTY.add("source.test")
    val scope2 = TextMateScope.EMPTY.add("source.test").add("source.test")
    assertNotEquals(scope1, scope2)
  }
}
