package com.intellij.spellchecker.dictionary;

import org.jetbrains.annotations.NotNull;

import java.util.Set;


public interface Dictionary {
  
  Set<String> getWords();

  void acceptWord(@NotNull String word);

  void replaceAllWords(Set<String> newWords);

  String getName();
}
