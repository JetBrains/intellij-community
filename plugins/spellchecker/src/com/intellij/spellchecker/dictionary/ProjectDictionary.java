/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.spellchecker.trie.Action;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ProjectDictionary implements Dictionary {

  private static final String DEFAULT_CURRENT_USER_NAME = "default.user";
  private static final String DEFAULT_PROJECT_DICTIONARY_NAME = "project";
  private String activeName;
  private Set<Dictionary> dictionaries;


  public ProjectDictionary() {
  }

  public ProjectDictionary(Set<Dictionary> dictionaries) {
    this.dictionaries = dictionaries;
  }

  public boolean isEmpty() {
    return false;
  }

  public String getName() {
    return DEFAULT_PROJECT_DICTIONARY_NAME;
  }

  public String getActiveName() {
    return activeName;
  }

  public void setActiveName(String name) {
    this.activeName = name;
  }

  public boolean contains(String word) {
    if (word == null || dictionaries == null) {
      return false;
    }
    for (Dictionary dictionary : dictionaries) {
      if (dictionary.contains(word)) {
        return true;
      }
    }
    return false;
  }

  public void addToDictionary(String word) {
    getActiveDictionary().addToDictionary(word);
  }

  public void removeFromDictionary(String word) {
    getActiveDictionary().removeFromDictionary(word);
  }

  @NotNull
  private Dictionary getActiveDictionary() {
    return ensureCurrentUserDictionary();
  }

  @NotNull
  private Dictionary ensureCurrentUserDictionary() {
    if (activeName == null) {
      activeName = DEFAULT_CURRENT_USER_NAME;
    }
    Dictionary result = getDictionaryByName(activeName);
    if (result == null) {
      result = new UserDictionary(this.activeName);
      if (dictionaries == null) {
        dictionaries = new HashSet<Dictionary>();
      }
      dictionaries.add(result);
    }
    return result;
  }

  @Nullable
  private Dictionary getDictionaryByName(@NotNull String name) {
    if (dictionaries == null) {
      return null;
    }
    Dictionary result = null;
    for (Dictionary dictionary : dictionaries) {
      if (dictionary.getName().equals(name)) {
        result = dictionary;
        break;
      }
    }
    return result;
  }


  public void replaceAll(@Nullable Collection<String> words) {
    getActiveDictionary().replaceAll(words);
  }

  public void clear() {
    getActiveDictionary().clear();
  }


  @Nullable
  public Set<String> getWords() {
    if (dictionaries == null) {
      return null;
    }
    Set<String> words = new HashSet<String>();
    for (Dictionary dictionary : dictionaries) {
      words.addAll(dictionary.getWords());
    }
    return words;
  }

  public void traverse(Action action) {
    if (dictionaries == null) {
      return;
    }

    for (Dictionary dictionary : dictionaries) {
      dictionary.traverse(action);
    }

  }

  @Nullable
  public Set<String> getEditableWords() {
    return getActiveDictionary().getWords();
  }

  @Nullable
  public Set<String> getNotEditableWords() {
    Set<String> words = getWords();
    Set<String> editable = getEditableWords();
    if (words != null && editable != null) {
      words.removeAll(editable);
    }
    return words;
  }

  public void addToDictionary(@Nullable Collection<String> words) {
    getActiveDictionary().addToDictionary(words);
  }

  public Set<Dictionary> getDictionaries() {
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

  @Override
  public String toString() {
    return "ProjectDictionary{" + "activeName='" + activeName + '\'' + ", dictionaries=" + dictionaries + '}';
  }
}