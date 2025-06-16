package org.jetbrains.plugins.textmate.language.preferences

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.jetbrains.plugins.textmate.Constants
import org.jetbrains.plugins.textmate.language.TextMateScopeComparatorCore
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigher
import org.jetbrains.plugins.textmate.update
import kotlin.concurrent.atomics.AtomicReference

class PreferencesRegistryBuilder(private val weigher: TextMateSelectorWeigher) {
  private val preferences = AtomicReference(persistentListOf<Preferences>())
  private val leftHighlightingBraces = AtomicReference(persistentSetOf<Int>())
  private val rightHighlightingBraces = AtomicReference(persistentSetOf<Int>())
  private val leftSmartTypingBraces = AtomicReference(persistentSetOf<Int>())
  private val rightSmartTypingBraces = AtomicReference(persistentSetOf<Int>())

  fun add(preferences: Preferences) {
    fillHighlightingBraces(preferences.highlightingPairs)
    fillSmartTypingBraces(preferences.smartTypingPairs)
    this.preferences.update {
      it.add(preferences)
    }
  }

  private fun fillHighlightingBraces(highlightingPairs: Collection<TextMateBracePair>?) {
    if (highlightingPairs != null) {
      for (pair in highlightingPairs) {
        if (!pair.left.isEmpty()) {
          leftHighlightingBraces.update { it.add(pair.left[0].code) }
        }
        if (!pair.right.isEmpty()) {
          rightHighlightingBraces.update { it.add(pair.right[pair.right.length - 1].code) }
        }
      }
    }
  }

  private fun fillSmartTypingBraces(smartTypingPairs: Collection<TextMateAutoClosingPair>?) {
    if (smartTypingPairs != null) {
      for (pair in smartTypingPairs) {
        if (!pair.left.isEmpty()) {
          leftSmartTypingBraces.update { it.add(pair.left[pair.left.length - 1].code) }
        }
        if (!pair.right.isEmpty()) {
          rightSmartTypingBraces.update { it.add(pair.right[pair.right.length - 1].code) }
        }
      }
    }
  }

  fun build(): PreferencesRegistry {
    fillHighlightingBraces(Constants.DEFAULT_HIGHLIGHTING_BRACE_PAIRS)
    fillSmartTypingBraces(Constants.DEFAULT_SMART_TYPING_BRACE_PAIRS)
    return PreferencesRegistryImpl(weigher = weigher,
                                   preferences = preferences.load(),
                                   leftHighlightingBraces = leftHighlightingBraces.load(),
                                   rightHighlightingBraces = rightHighlightingBraces.load(),
                                   leftSmartTypingBraces = leftSmartTypingBraces.load(),
                                   rightSmartTypingBraces = rightSmartTypingBraces.load())
  }
}

class PreferencesRegistryImpl(
  private val weigher: TextMateSelectorWeigher,
  private val preferences: Collection<Preferences> = emptyList(),
  private val leftHighlightingBraces: Set<Int> = emptySet(),
  private val rightHighlightingBraces: Set<Int> = emptySet(),
  private val leftSmartTypingBraces: Set<Int> = emptySet(),
  private val rightSmartTypingBraces: Set<Int> = emptySet()
) : PreferencesRegistry {
  override fun isPossibleLeftHighlightingBrace(firstLeftBraceChar: Char): Boolean {
    return leftHighlightingBraces.contains(firstLeftBraceChar.code) ||
           (firstLeftBraceChar != ' ' && leftSmartTypingBraces.contains(firstLeftBraceChar.code))
  }

  override fun isPossibleRightHighlightingBrace(lastRightBraceChar: Char): Boolean {
    return rightHighlightingBraces.contains(lastRightBraceChar.code) ||
           (lastRightBraceChar != ' ' && rightSmartTypingBraces.contains(lastRightBraceChar.code))
  }

  override fun isPossibleLeftSmartTypingBrace(lastLeftBraceChar: Char): Boolean {
    return leftSmartTypingBraces.contains(lastLeftBraceChar.code)
  }

  override fun isPossibleRightSmartTypingBrace(lastRightBraceChar: Char): Boolean {
    return rightSmartTypingBraces.contains(lastRightBraceChar.code)
  }

  /**
   * Returns preferences by scope selector.
   *
   * @param scope selector of current context.
   * @return preferences from table for given scope sorted by descending weight
   * of rule selector relative to scope selector.
   */
  override fun getPreferences(scope: TextMateScope): List<Preferences> {
    return TextMateScopeComparatorCore(weigher, scope, Preferences::scopeSelector).sortAndFilter(preferences)
  }
}
