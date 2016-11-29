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
package com.intellij.spellchecker.state;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.spellchecker.dictionary.EditableDictionary;
import com.intellij.spellchecker.dictionary.UserDictionary;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

@Tag("dictionary")
public class DictionaryState implements PersistentStateComponent<DictionaryState> {

  public static final String NAME_ATTRIBUTE = "name";

  @Tag("words") @AbstractCollection(surroundWithTag = false, elementTag = "w", elementValueAttribute = "")
  public Set<String> words = new HashSet<>();

  @Attribute(NAME_ATTRIBUTE)
  public String name;

  @Transient
  private EditableDictionary dictionary;

  public DictionaryState() {
  }

  public DictionaryState(@NotNull EditableDictionary dictionary) {
    setDictionary(dictionary);
  }

  @Transient
  public void setDictionary(@NotNull EditableDictionary dictionary) {
    this.dictionary = dictionary;
    this.name = dictionary.getName();
    synchronizeWords();
  }

  @Transient
  public EditableDictionary getDictionary() {
    return dictionary;
  }

  public DictionaryState getState() {
    synchronizeWords();
    return this;
  }

  private void synchronizeWords() {
    if (dictionary != null) {
      Set<String> words = new HashSet<>();
      words.addAll(dictionary.getWords());
      this.words = words;
    }
  }

  public void loadState(DictionaryState state) {
    if (state != null && state.name != null) {
      name = state.name;
      words = state.words;
    }
    retrieveDictionary();
  }

  private void retrieveDictionary() {
    assert name != null;
    dictionary = new UserDictionary(name);
    dictionary.addToDictionary(words);
  }

  @Override
  public String toString() {
    return "DictionaryState{" + "dictionary=" + dictionary + '}';
  }
}