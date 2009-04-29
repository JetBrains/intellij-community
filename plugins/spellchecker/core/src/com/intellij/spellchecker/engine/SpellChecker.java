package com.intellij.spellchecker.engine;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

/**
 * Spell checker.
 */
public interface SpellChecker {
  void addDictionary(@NotNull InputStream is, @NonNls String encoding, @NotNull Locale locale) throws IOException;

  void addToDictionary(@NotNull String word);

  void ignoreAll(@NotNull String word);

  boolean isIgnored(@NotNull String word);

  boolean isCorrect(@NotNull String word);

  @NotNull
  List<String> getSuggestions(@NotNull String word, int threshold);

      @NotNull
  List<String> getSuggestionsExt(@NotNull String word, int threshold);

  @NotNull
  List<String> getVariants(@NotNull String prefix);

  /**
   * This method must clean up user dictionary words and ignored words.
   */
  void reset();
}
