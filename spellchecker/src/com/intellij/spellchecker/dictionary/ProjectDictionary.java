/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.spellchecker.dictionary;

import com.intellij.util.Consumer;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class ProjectDictionary implements EditableDictionary {
  @NonNls private static final String DEFAULT_CURRENT_USER_NAME = "default.user";
  private static final String DEFAULT_PROJECT_DICTIONARY_NAME = "project";
  private String activeName;
  private Set<EditableDictionary> dictionaries;

  public ProjectDictionary() {
  }

  public ProjectDictionary(@NotNull Set<EditableDictionary> dictionaries) {
    this.dictionaries = dictionaries;
  }

  @Override
  public boolean isEmpty() {
    return false;
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
      return false;
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
    if (errors==dictionaries.size()) return null;//("WORD_OF_ENTIRELY_UNKNOWN_LETTERS_FOR_ALL");
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
        dictionaries = new THashSet<>();
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
    Set<String> words = new THashSet<>();
    for (Dictionary dictionary : dictionaries) {
      Set<String> otherWords = dictionary.getWords();
      words.addAll(otherWords);
    }
    return words;
  }

  @Override
  public int size(){
    int result = 0;
    for (Dictionary dictionary : dictionaries) {
      result+=dictionary.size();
    }
    return result;
  }

  @Override
  public void traverse(@NotNull final Consumer<String> consumer) {
    if (dictionaries == null) {
      return;
    }

    for (EditableDictionary dictionary : dictionaries) {
      dictionary.traverse(consumer);
    }
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

    if (activeName != null ? !activeName.equals(that.activeName) : that.activeName != null) return false;
    if (dictionaries != null ? !dictionaries.equals(that.dictionaries) : that.dictionaries != null) return false;

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