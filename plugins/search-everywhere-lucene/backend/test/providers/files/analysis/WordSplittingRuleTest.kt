package com.intellij.searchEverywhereLucene.backend.providers.files.analysis

import com.intellij.searchEverywhereLucene.backend.providers.files.analysis.splitting.LetterAndDigitSplittingRule
import com.intellij.searchEverywhereLucene.backend.providers.files.analysis.splitting.NumericTransitionSplittingRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WordSplittingRuleTest {

  // ---- SymbolSplittingRule ----

  @Test
  fun `LetterAndDigitSplittingRule splits on single delimiter`() {
    assertEquals(LetterAndDigitSplittingRule("").split().toList(), emptyList<IntRange>())
    assertEquals(LetterAndDigitSplittingRule("hello").split().toList(), listOf(0 until 5))
    assertEquals(LetterAndDigitSplittingRule("foo/bar/baz").split().toList(), listOf(0 until 3, 4 until 7, 8 until 11))
    assertEquals(LetterAndDigitSplittingRule("foo//bar").split().toList(), listOf(0 until 3, 5 until 8))
  }

  // ---- split() - case-style coverage ----
  // Each test checks that split() produces the expected word parts for a given naming convention.
  // Flat case and upper flat case are excluded: they have no word boundaries so the whole string
  // is returned as a single token unchanged.

  private fun words(text: String): Set<String> =
    split(text).map { text.substring(it.first, it.last + 1) }.toSet()

  @Test
  fun `split - camelCase and PascalCase`() {
    assertEquals(setOf("naming", "Identifier"), words("namingIdentifier"))
    assertEquals(setOf("Naming", "Identifier"), words("NamingIdentifier"))
  }

  @Test
  fun `split - snake_case variants`() {
    assertEquals(setOf("naming", "identifier"), words("naming_identifier"))
    assertEquals(setOf("Naming", "Identifier"), words("Naming_Identifier"))
    assertEquals(setOf("NAMING", "IDENTIFIER"), words("NAMING_IDENTIFIER"))
  }

  @Test
  fun `split - kebab-case variants`() {
    assertEquals(setOf("naming", "identifier"), words("naming-identifier"))
    assertEquals(setOf("NAMING", "IDENTIFIER"), words("NAMING-IDENTIFIER"))
    assertEquals(setOf("Naming", "Identifier"), words("Naming-Identifier"))
  }

  @Test
  fun `split - dot and tilde notation`() {
    assertEquals(setOf("naming", "identifier"), words("naming.identifier"))
    assertEquals(setOf("NAMING", "Identifier"), words("NAMING.Identifier"))
    assertEquals(setOf("naming", "identifier"), words("naming~identifier"))
  }

  @Test
  fun `split - symbol-prefixed and symbol-wrapped identifiers`() {
    assertEquals(setOf("naming", "Identifier"), words("_namingIdentifier"))
    assertEquals(setOf("naming", "Identifier"), words("__namingIdentifier"))
    assertEquals(setOf("naming", "Identifier"), words("namingIdentifier!"))
    assertEquals(setOf("NAMING", "IDENTIFIER"), words("__NAMING_IDENTIFIER__"))
  }

  @Test
  fun `split - camelCase with numeric suffix`() {
    // The letter-to-digit transition at 'r'→'1' generates an extra numericRule pass over the
    // pre-transition span, so camelCase subparts are also produced without the digit suffix.
    assertEquals(setOf("naming", "Identifier123", "Identifier", "123"), words("namingIdentifier123"))
  }

  @Test
  fun `split - snake_case with numeric suffix`() {
    assertEquals(setOf("naming", "identifier", "123"), words("naming_identifier_123"))
  }

  @Test
  fun `split - Hungarian notation (lowercase type prefix)`() {
    assertEquals(setOf("datatype", "Naming", "Identifier"), words("datatypeNamingIdentifier"))
  }

  // ---- NumericTransitionSplittingRule ----

  @Test
  fun `NumericTransitionSplittingRule yields trailing letter segment after last digit-letter transition`() {
    // "File2Open": File(0..3), 2(4..4), Open(5..8)
    assertEquals(
      NumericTransitionSplittingRule("File2Open").split().toList(),
      listOf(0 until 4, 4..4, 5..8),
    )
    // "Point3D": Point(0..4), 3(5..5), D(6..6)
    assertEquals(
      NumericTransitionSplittingRule("Point3D").split().toList(),
      listOf(0 until 5, 5..5, 6..6),
    )
    // "Http2Server": Http(0..3), 2(4..4), Server(5..10)
    assertEquals(
      NumericTransitionSplittingRule("Http2Server").split().toList(),
      listOf(0 until 4, 4..4, 5..10),
    )
  }

  @Test
  fun `NumericTransitionSplittingRule yields trailing digit segment after last letter-digit transition`() {
    // "File123": File(0..3), 123(4..6)
    assertEquals(
      NumericTransitionSplittingRule("File123").split().toList(),
      listOf(0 until 4, 4..6),
    )
  }

  @Test
  fun `NumericTransitionSplittingRule returns whole span when no transition`() {
    assertEquals(NumericTransitionSplittingRule("hello").split().toList(), listOf(0..4))
    assertEquals(NumericTransitionSplittingRule("123").split().toList(), listOf(0..2))
    assertEquals(NumericTransitionSplittingRule("").split().toList(), emptyList<IntRange>())
  }

  // ---- split() integration ----

  @Test
  fun `split includes letter segment after digit-letter boundary`() {
    assertEquals(setOf("File2Open", "File", "2", "Open"), words("File2Open"))
  }

  @Test
  fun `split includes single trailing letter after digit`() {
    assertEquals(setOf("Point3D", "Point", "3", "D"), words("Point3D"))
  }

  @Test
  fun `split - trailing short uppercase acronym`() {
    // Trailing ≤ 2-char uppercase sequences are split individually when preceded by other content
    assertEquals(setOf("foo", "A", "B"), words("fooAB"))
    assertEquals(setOf("Foo", "A", "B"), words("FooAB"))
    // 3+ char trailing uppercase stays as one word
    assertEquals(setOf("foo", "ABC"), words("fooABC"))
    // Long trailing acronyms are unchanged
    assertEquals(setOf("HTTP", "Server"), words("HTTPServer"))
    // Standalone short uppercase sequences are kept whole (not split into individual letters)
    assertEquals(setOf("MF"), words("MF"))
    assertEquals(setOf("RM"), words("RM"))
  }

}
