package org.jetbrains.plugins.textmate.language.preferences

import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import org.jetbrains.plugins.textmate.Constants
import org.jetbrains.plugins.textmate.language.TextMateScopeComparator
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope

class PreferencesRegistryImpl : PreferencesRegistry {
  private val myPreferences: MutableSet<Preferences> = hashSetOf()
  private val myLeftHighlightingBraces: IntSet = IntOpenHashSet()
  private val myRightHighlightingBraces: IntSet = IntOpenHashSet()
  private val myLeftSmartTypingBraces: IntSet = IntOpenHashSet()
  private val myRightSmartTypingBraces: IntSet = IntOpenHashSet()

  init {
    fillHighlightingBraces(Constants.DEFAULT_HIGHLIGHTING_BRACE_PAIRS)
    fillSmartTypingBraces(Constants.DEFAULT_SMART_TYPING_BRACE_PAIRS)
  }

  @Synchronized
  fun addPreferences(preferences: Preferences) {
    fillHighlightingBraces(preferences.highlightingPairs)
    fillSmartTypingBraces(preferences.smartTypingPairs)
    myPreferences.add(preferences)
  }

  @Synchronized
  private fun fillHighlightingBraces(highlightingPairs: Collection<TextMateBracePair>?) {
    if (highlightingPairs != null) {
      for (pair in highlightingPairs) {
        if (!pair.left.isEmpty()) {
          myLeftHighlightingBraces.add(pair.left[0].code)
        }
        if (!pair.right.isEmpty()) {
          myRightHighlightingBraces.add(pair.right[pair.right.length - 1].code)
        }
      }
    }
  }

  private fun fillSmartTypingBraces(smartTypingPairs: Collection<TextMateAutoClosingPair>?) {
    if (smartTypingPairs != null) {
      for (pair in smartTypingPairs) {
        if (!pair.left.isEmpty()) {
          myLeftSmartTypingBraces.add(pair.left[pair.left.length - 1].code)
        }
        if (!pair.right.isEmpty()) {
          myRightSmartTypingBraces.add(pair.right[pair.right.length - 1].code)
        }
      }
    }
  }

  @Synchronized
  override fun isPossibleLeftHighlightingBrace(firstLeftBraceChar: Char): Boolean {
    return myLeftHighlightingBraces.contains(firstLeftBraceChar.code) ||
           (firstLeftBraceChar != ' ' && myLeftSmartTypingBraces.contains(firstLeftBraceChar.code))
  }

  @Synchronized
  override fun isPossibleRightHighlightingBrace(lastRightBraceChar: Char): Boolean {
    return myRightHighlightingBraces.contains(lastRightBraceChar.code) ||
           (lastRightBraceChar != ' ' && myRightSmartTypingBraces.contains(lastRightBraceChar.code))
  }

  @Synchronized
  override fun isPossibleLeftSmartTypingBrace(lastLeftBraceChar: Char): Boolean {
    return myLeftSmartTypingBraces.contains(lastLeftBraceChar.code)
  }

  @Synchronized
  override fun isPossibleRightSmartTypingBrace(lastRightBraceChar: Char): Boolean {
    return myRightSmartTypingBraces.contains(lastRightBraceChar.code)
  }

  /**
   * Returns preferences by scope selector.
   *
   * @param scope selector of current context.
   * @return preferences from table for given scope sorted by descending weight
   * of rule selector relative to scope selector.
   */
  @Synchronized
  override fun getPreferences(scope: TextMateScope): List<Preferences> {
    return TextMateScopeComparator<Preferences>(scope, Preferences::getScopeSelector)
      .sortAndFilter(myPreferences)
  }

  @Synchronized
  fun clear() {
    myPreferences.clear()

    myLeftHighlightingBraces.clear()
    myRightHighlightingBraces.clear()
    fillHighlightingBraces(Constants.DEFAULT_HIGHLIGHTING_BRACE_PAIRS)

    myLeftSmartTypingBraces.clear()
    myRightSmartTypingBraces.clear()
    fillSmartTypingBraces(Constants.DEFAULT_SMART_TYPING_BRACE_PAIRS)
  }
}
