package org.jetbrains.plugins.textmate.language.preferences

import org.jetbrains.plugins.textmate.TestUtil
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigherImpl
import org.jetbrains.plugins.textmate.plist.XmlPlistReaderForTests
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class PreferencesTest {
  @Test
  fun retrievePreferencesBySelector_1() {
    val preferencesRegistry = loadPreferences(TestUtil.PREFERENCES_TEST_BUNDLE)
    val preferences = preferencesRegistry.getPreferences(TestUtil.scopeFromString("text.html.basic"))
    assertEquals(1, preferences.size.toLong())
    assertEquals(setOf(TextMateAutoClosingPair("\"", "\"", 0)),
                 preferences[0].smartTypingPairs)
    assertEquals(setOf(TextMateBracePair("`", "`")), preferences[0].highlightingPairs)
  }

  @Test
  fun retrievePreferencesBySelector_2() {
    val preferencesRegistry = loadPreferences(TestUtil.PREFERENCES_TEST_BUNDLE)
    val preferences = preferencesRegistry.getPreferences(TestUtil.scopeFromString("source.php string"))
    assertEquals(1, preferences.size.toLong())
    assertEquals(setOf(TextMateAutoClosingPair("(", ")", 0)),
                 preferences[0].smartTypingPairs)
    assertEquals(setOf(TextMateBracePair("[", "]")), preferences[0].highlightingPairs)
  }

  @Test
  fun retrievePreferencesBySelectorCorrespondingToSelectorWeight() {
    val preferencesRegistry = loadPreferences(TestUtil.PREFERENCES_TEST_BUNDLE)
    val preferences = preferencesRegistry.getPreferences(TestUtil.scopeFromString("text.html source.php string.quoted.double.php"))
    assertEquals(2, preferences.size.toLong())
    assertEquals(setOf(TextMateAutoClosingPair("(", ")", 0)),
                 preferences[0].smartTypingPairs)
    assertEquals(setOf(TextMateBracePair("[", "]")), preferences[0].highlightingPairs)

    assertEquals(setOf(TextMateAutoClosingPair("\"", "\"", 0)),
                 preferences[1].smartTypingPairs)
    assertEquals(setOf(TextMateBracePair("`", "`")), preferences[1].highlightingPairs)
  }

  @Test
  fun loadingWithTheSameScope() {
    val preferencesRegistry = loadPreferences(TestUtil.PREFERENCES_TEST_BUNDLE)
    val preferences: Preferences = mergeAll(preferencesRegistry.getPreferences(TestUtil.scopeFromString("same.scope")))
    assertEquals(setOf(TextMateAutoClosingPair("[", "]", 0),
                       TextMateAutoClosingPair("(", ")", 0)), preferences.smartTypingPairs)
  }

  @Test
  fun loadHighlightingPairs() {
    val preferencesRegistry = loadPreferences(TestUtil.MARKDOWN_TEXTMATE)
    val preferences: Preferences = mergeAll(
      preferencesRegistry.getPreferences(TestUtil.scopeFromString("text.html.markdown markup.raw")))
    assertEquals(setOf(TextMateBracePair("[", "]"), TextMateBracePair("`", "`")),
                 preferences.highlightingPairs)
  }

  @Test
  fun loadSmartTypingPairs() {
    val preferencesRegistry = loadPreferences(TestUtil.MARKDOWN_TEXTMATE)
    val preferences: Preferences = mergeAll(
      preferencesRegistry.getPreferences(TestUtil.scopeFromString("text.html.markdown markup.raw")))
    assertEquals(setOf(
      TextMateAutoClosingPair("{", "}", 0),
      TextMateAutoClosingPair("(", ")", 0),
      TextMateAutoClosingPair("\"", "\"", 0)
    ), preferences.smartTypingPairs)
  }

  @Test
  fun loadDisabledPairs() {
    val preferencesRegistry = loadPreferences(TestUtil.LATEX)
    val scope = TestUtil.scopeFromString("text.tex constant.character.escape.tex")
    val preferences = preferencesRegistry.getPreferences(scope).iterator().next()
    val smartTypingPairs = preferences.smartTypingPairs
    assertNotNull(smartTypingPairs)
    assertEquals(0, smartTypingPairs.size)
  }

  @Test
  fun loadIndentationRules() {
    val preferencesRegistry = loadPreferences(TestUtil.PHP_VSC)
    val preferences: Preferences = mergeAll(preferencesRegistry.getPreferences(TestUtil.scopeFromString("text.html.php")))
    assertFalse(preferences.indentationRules.isEmpty)
    assertNotNull(preferences.indentationRules.increaseIndentPattern)
  }

  @Test
  fun loadOnEnterRules() {
    val preferencesRegistry = loadPreferences(TestUtil.RESTRUCTURED_TEXT)
    val preferences: Preferences = mergeAll(preferencesRegistry.getPreferences(TestUtil.scopeFromString("source.rst")))
    assertNotNull(preferences.onEnterRules)
    assertFalse(preferences.onEnterRules.isEmpty())
  }

  private fun loadPreferences(bundleName: String): PreferencesRegistry {
    val preferences = TestUtil.readBundle(bundleName, XmlPlistReaderForTests()).readPreferences().iterator()
    assertNotNull(preferences)
    val preferencesRegistryBuilder = PreferencesRegistryBuilder(TextMateSelectorWeigherImpl())
    while (preferences.hasNext()) {
      val next = preferences.next()
      preferencesRegistryBuilder.add(Preferences(next.scopeName,
                                                 next.highlightingPairs,
                                                 next.smartTypingPairs,
                                                 setOf(),
                                                 null,
                                                 next.indentationRules,
                                                 next.onEnterRules))
    }
    return preferencesRegistryBuilder.build()
  }

  private fun mergeAll(preferences: List<Preferences>): Preferences {
    val highlightingPairs = mutableSetOf<TextMateBracePair>()
    val smartTypingPairs = mutableSetOf<TextMateAutoClosingPair>()
    val surroundingPairs = mutableSetOf<TextMateBracePair>()
    val autoCloseBefore = mutableSetOf<Char>()
    var indentationRules = IndentationRules.empty()
    val onEnterRules = mutableSetOf<OnEnterRule>()

    for (preference in preferences) {
      val localHighlightingPairs = preference.highlightingPairs
      val localSmartTypingPairs = preference.smartTypingPairs
      val localSurroundingPairs = preference.surroundingPairs
      val localAutoCloseBefore = preference.autoCloseBefore
      indentationRules = indentationRules.updateWith(preference.indentationRules)
      if (localHighlightingPairs != null) {
        highlightingPairs.addAll(localHighlightingPairs)
      }
      if (localSmartTypingPairs != null) {
        smartTypingPairs.addAll(localSmartTypingPairs)
      }
      if (localSurroundingPairs != null) {
        surroundingPairs.addAll(localSurroundingPairs)
      }
      if (localAutoCloseBefore != null) {
        for (c in localAutoCloseBefore.toCharArray()) {
          autoCloseBefore.add(c)
        }
      }
      if (preference.onEnterRules != null) {
        onEnterRules.addAll(preference.onEnterRules)
      }
    }
    return Preferences("",
                       highlightingPairs,
                       smartTypingPairs,
                       surroundingPairs,
                       autoCloseBefore.joinToString().ifEmpty { null },
                       indentationRules,
                       onEnterRules)
  }
}