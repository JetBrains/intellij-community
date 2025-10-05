// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.dictionary;

import com.intellij.spellchecker.util.Strings;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

import static com.intellij.util.containers.CollectionFactory.createSmallMemoryFootprintSet;

public final class UserDictionary implements EditableDictionary {
  private final String name;

  private final @NotNull Set<String> words;
  private final @NotNull Set<String> camelCaseWords;

  public UserDictionary(@NotNull String name) {
    this.name = name;
    this.words = createSmallMemoryFootprintSet();
    this.camelCaseWords = createSmallMemoryFootprintSet();
  }

  public UserDictionary(@NotNull String name, @Nullable Collection<String> words) {
    this.name = name;
    this.words = words == null ? createSmallMemoryFootprintSet() : createSmallMemoryFootprintSet(words.size());
    this.camelCaseWords = createSmallMemoryFootprintSet();
    addToDictionary(words);
  }

  @Override
  public @NotNull String getName() {
    return name;
  }

  @Override
  public @Nullable Boolean contains(@NotNull String word) {
    return words.contains(word) ? true : null;
  }

  @Override
  public @NotNull Set<String> getWords() {
    return words;
  }

  @Override
  public @NotNull Set<String> getEditableWords() {
    return words;
  }

  @Override
  public @NotNull Set<String> getCamelCaseWords() {
    return camelCaseWords;
  }

  @Override
  public void clear() {
    words.clear();
    camelCaseWords.clear();
  }

  @Override
  public void addToDictionary(String word) {
    if (word == null) {
      return;
    }
    words.add(word);
    if (Strings.isMixedCase(word)) {
      camelCaseWords.add(word);
    }
  }

  @Override
  public void removeFromDictionary(String word) {
    if (word == null) {
      return;
    }
    words.remove(word);
    camelCaseWords.remove(word);
  }

  @Override
  public void replaceAll(@Nullable Collection<String> words) {
    clear();
    addToDictionary(words);
  }

  @Override
  public void addToDictionary(@Nullable Collection<String> words) {
    if (words == null || words.isEmpty()) {
      return;
    }
    for (String word : words) {
      addToDictionary(word);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    UserDictionary that = (UserDictionary)o;

    return name.equals(that.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public @NonNls String toString() {
    return "UserDictionary{" + "name='" + name + '\'' + ", words.count=" + words.size() + '}';
  }
}
