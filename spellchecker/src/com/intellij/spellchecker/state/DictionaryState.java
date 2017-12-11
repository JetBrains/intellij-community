/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.spellchecker.state;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.spellchecker.dictionary.EditableDictionary;
import com.intellij.spellchecker.dictionary.UserDictionary;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

@Tag("dictionary")
public class DictionaryState implements PersistentStateComponent<DictionaryState> {
  public static final String NAME_ATTRIBUTE = "name";

  @XCollection(style = XCollection.Style.v2, elementName = "w", valueAttributeName = "")
  public Set<String> words = new THashSet<>();

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

  @Override
  public DictionaryState getState() {
    synchronizeWords();
    return this;
  }

  private void synchronizeWords() {
    if (dictionary != null) {
      this.words = new THashSet<>(dictionary.getWords());
    }
  }

  @Override
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