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

import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class AggregatedDictionary implements EditableDictionary {

  private static final String DICTIONARY_NAME = "common";
  private EditableDictionary cachedDictionary;
  private ProjectDictionary projectDictionary;

  public String getName() {
    return DICTIONARY_NAME;
  }

  public AggregatedDictionary(@NotNull ProjectDictionary projectDictionary, @NotNull EditableDictionary cachedDictionary) {
    this.projectDictionary = projectDictionary;
    this.cachedDictionary = cachedDictionary;
    this.cachedDictionary.addToDictionary(projectDictionary.getWords());
  }

  public boolean isEmpty() {
    return false;
  }

  public boolean contains(String word) {
    if (word == null) {
      return false;
    }
    return cachedDictionary.contains(word);
  }

  public void addToDictionary(String word) {
    getProjectDictionary().addToDictionary(word);
    getCachedDictionary().addToDictionary(word);
  }

  public void removeFromDictionary(String word) {
    getProjectDictionary().removeFromDictionary(word);
    getCachedDictionary().removeFromDictionary(word);
  }


  public void replaceAll(@Nullable Collection<String> words) {
    Set<String> oldWords = getProjectDictionary().getWords();
    getProjectDictionary().replaceAll(words);
    if (oldWords != null) {
      for (String word : oldWords) {
        if (words == null || !words.contains(word)) {
          getCachedDictionary().removeFromDictionary(word);
        }
      }
    }
  }

  public void clear() {
    getProjectDictionary().clear();
  }

  public void traverse(@NotNull final Consumer<String> consumer) {
    cachedDictionary.traverse(consumer);
  }


  public Set<String> getWords() {
    return cachedDictionary!=null?cachedDictionary.getWords():null;
  }

  public int size() {
    return (cachedDictionary!=null?cachedDictionary.size():0);
  }

  @Nullable
  public Set<String> getEditableWords() {
    return getProjectDictionary().getEditableWords();
  }


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
