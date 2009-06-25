package com.intellij.spellchecker.dictionary;

import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.HashSet;
import java.util.Collections;

public class UserWordList {

  public Set<String> words = new HashSet<String>();

  public Set<String> getWords() {
    return Collections.unmodifiableSet(words);
  }

  public void acceptWord(@NotNull String word) {
    words.add(word);
  }

  public void replaceAllDictionaryWords(Set<String> newWords) {
    replaceAll(words, newWords);
  }

  private static void replaceAll(Set<String> words, Set<String> newWords) {
    words.clear();
    words.addAll(newWords);
 /*   if (newWords != null) {
      System.out.println("New words:");
      for (String s : newWords) {
        System.out.println(s);
      }
    }*/
  }
}
