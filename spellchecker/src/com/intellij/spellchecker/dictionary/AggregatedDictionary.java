// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.dictionary;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.spellchecker.state.CachedDictionaryState;
import com.intellij.spellchecker.state.ProjectDictionaryState;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;
/**
 * @deprecated will be removed in 2018.X, use separate
 * {@link ProjectDictionaryState#getProjectDictionary() and {@link CachedDictionaryState#getDictionary()}} instead
 */
@Deprecated
public class AggregatedDictionary implements EditableDictionary {
  @NonNls private static final String DICTIONARY_NAME = "common";
  private final EditableDictionary cachedDictionary;
  private final ProjectDictionary projectDictionary;

  @NotNull
  @Override
  public String getName() {
    return DICTIONARY_NAME;
  }

  public AggregatedDictionary(@NotNull ProjectDictionary projectDictionary, @NotNull EditableDictionary cachedDictionary) {
    this.projectDictionary = projectDictionary;
    this.cachedDictionary = cachedDictionary;
    this.cachedDictionary.addToDictionary(projectDictionary.getWords());
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @NonNls
  @Override
  public String toString() {
    return "AggregatedDictionary{" +
           "cachedDictionary=" + cachedDictionary +
           ", projectDictionary=" + projectDictionary +
           '}';
  }

  @Override
  @Nullable
  public Boolean contains(@NotNull String word) {
    return cachedDictionary.contains(word);
  }

  @Override
  public void addToDictionary(String word) {
    getProjectDictionary().addToDictionary(word);
    getCachedDictionary().addToDictionary(word);
  }

  @Override
  public void removeFromDictionary(String word) {
    getProjectDictionary().removeFromDictionary(word);
    getCachedDictionary().removeFromDictionary(word);
  }

  @Override
  public void replaceAll(@Nullable Collection<String> words) {
    Set<String> oldWords = getProjectDictionary().getWords();
    getProjectDictionary().replaceAll(words);
    getCachedDictionary().addToDictionary(words);
    for (String word : oldWords) {
      if (words == null || !words.contains(word)) {
        getCachedDictionary().removeFromDictionary(word);
      }
    }
  }

  @Override
  public void clear() {
    getProjectDictionary().clear();
  }

  @Override
  public void traverse(@NotNull final Consumer<String> consumer) {
    cachedDictionary.traverse(consumer);
  }

  @Override
  public void getSuggestions(@NotNull String word, @NotNull Consumer<String> consumer) {
    traverse(s -> {
      if (!StringUtil.isEmpty(s) && s.charAt(0) == word.charAt(0) && s.length() >= 0 && s.length() <= Integer.MAX_VALUE) {
        consumer.consume(s);
      }
    });
  }

  @Override
  @NotNull
  public Set<String> getWords() {
    return cachedDictionary.getWords();
  }

  @Override
  public int size() {
    return cachedDictionary.size();
  }

  @Override
  @NotNull
  public Set<String> getEditableWords() {
    return getProjectDictionary().getEditableWords();
  }

  @Override
  public void addToDictionary(@Nullable Collection<String> words) {
    getProjectDictionary().addToDictionary(words);
    getCachedDictionary().addToDictionary(words);
  }

  public EditableDictionary getCachedDictionary() {
    return cachedDictionary;
  }

  public ProjectDictionary getProjectDictionary() {
    return projectDictionary;
  }
}
