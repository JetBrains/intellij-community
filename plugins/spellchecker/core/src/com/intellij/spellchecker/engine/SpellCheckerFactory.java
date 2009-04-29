package com.intellij.spellchecker.engine;

/**
 * Spell checker factory.
 */
public final class SpellCheckerFactory {
  private SpellCheckerFactory() {
  }

  public static SpellChecker create() {
    return new JazzySpellChecker();
  }
}
