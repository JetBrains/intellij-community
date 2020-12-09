// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.dictionary;

import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

public final class UserDictionary implements EditableDictionary {
  private final String name;

  @NotNull
  private final Set<String> words = CollectionFactory.createSmallMemoryFootprintSet();

  public UserDictionary(@NotNull String name) {
    this.name = name;
  }

  @NotNull
  @Override
  public String getName() {
    return name;
  }

  @Override
  @Nullable
  public Boolean contains(@NotNull String word) {
    boolean contains = words.contains(word);
    if (contains) return true;
    return null;
  }

  @NotNull
  @Override
  public Set<String> getWords() {
    return words;
  }

  @Override
  @NotNull
  public Set<String> getEditableWords() {
    return words;
  }

  @Override
  public void clear() {
    words.clear();
  }

  @Override
  public void addToDictionary(String word) {
    if (word == null) {
      return;
    }
    words.add(word);
  }

  @Override
  public void removeFromDictionary(String word) {
    if (word == null) {
      return;
    }
    words.remove(word);
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

  @NonNls
  @Override
  public String toString() {
    return "UserDictionary{" + "name='" + name + '\'' + ", words.count=" + words.size() + '}';
  }
}
