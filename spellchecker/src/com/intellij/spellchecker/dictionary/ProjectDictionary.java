// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.dictionary;

import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public final class ProjectDictionary implements EditableDictionary {
  @NonNls private static final String DEFAULT_CURRENT_USER_NAME = "default.user";
  private static final String DEFAULT_PROJECT_DICTIONARY_NAME = "project";
  private String activeName;
  private Set<EditableDictionary> dictionaries;

  public ProjectDictionary() {
  }

  public ProjectDictionary(@NotNull Set<EditableDictionary> dictionaries) {
    this.dictionaries = dictionaries;
  }

  @NotNull
  @Override
  public String getName() {
    return DEFAULT_PROJECT_DICTIONARY_NAME;
  }

  public void setActiveName(String name) {
    activeName = name;
  }

  @Override
  @Nullable
  public Boolean contains(@NotNull String word) {
    if (dictionaries == null) {
      return null; // still ("WORD_OF_ENTIRELY_UNKNOWN_LETTERS_FOR_ALL");
    }
    int errors = 0;
    for (Dictionary dictionary : dictionaries) {
      Boolean contains = dictionary.contains(word);
      if (contains == null) {
        errors++;
      }
      else if (contains) {
        return true;
      }
    }
    if (errors == dictionaries.size()) return null;//("WORD_OF_ENTIRELY_UNKNOWN_LETTERS_FOR_ALL");
    return false;
  }

  @Override
  public void addToDictionary(String word) {
    getActiveDictionary().addToDictionary(word);
  }

  @Override
  public void removeFromDictionary(String word) {
    getActiveDictionary().removeFromDictionary(word);
  }

  @NotNull
  private EditableDictionary getActiveDictionary() {
    return ensureCurrentUserDictionary();
  }

  @NotNull
  private EditableDictionary ensureCurrentUserDictionary() {
    if (activeName == null) {
      activeName = DEFAULT_CURRENT_USER_NAME;
    }
    EditableDictionary result = getDictionaryByName(activeName);
    if (result == null) {
      result = new UserDictionary(activeName);
      if (dictionaries == null) {
        dictionaries = CollectionFactory.createSmallMemoryFootprintSet();
      }
      dictionaries.add(result);
    }
    return result;
  }

  @Nullable
  private EditableDictionary getDictionaryByName(@NotNull String name) {
    if (dictionaries == null) {
      return null;
    }
    EditableDictionary result = null;
    for (EditableDictionary dictionary : dictionaries) {
      if (dictionary.getName().equals(name)) {
        result = dictionary;
        break;
      }
    }
    return result;
  }

  @Override
  public void replaceAll(@Nullable Collection<String> words) {
    getActiveDictionary().replaceAll(words);
  }

  @Override
  public void clear() {
    getActiveDictionary().clear();
  }


  @Override
  @NotNull
  public Set<String> getWords() {
    if (dictionaries == null) {
      return Collections.emptySet();
    }
    Set<String> words = CollectionFactory.createSmallMemoryFootprintSet();
    for (Dictionary dictionary : dictionaries) {
      words.addAll(dictionary.getWords());
    }
    return words;
  }

  @Override
  @NotNull
  public Set<String> getEditableWords() {
    return getActiveDictionary().getWords();
  }


  @Override
  public void addToDictionary(@Nullable Collection<String> words) {
    getActiveDictionary().addToDictionary(words);
  }

  public Set<EditableDictionary> getDictionaries() {
    return dictionaries;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ProjectDictionary that = (ProjectDictionary)o;

    if (!Objects.equals(activeName, that.activeName)) return false;
    if (!Objects.equals(dictionaries, that.dictionaries)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = activeName != null ? activeName.hashCode() : 0;
    result = 31 * result + (dictionaries != null ? dictionaries.hashCode() : 0);
    return result;
  }

  @NonNls
  @Override
  public String toString() {
    return "ProjectDictionary{" + "activeName='" + activeName + '\'' + ", dictionaries=" + dictionaries + '}';
  }
}