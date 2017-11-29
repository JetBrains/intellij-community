/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import java.util.Set;

public class UserDictionary implements EditableDictionary {
  private final String name;

  @NotNull
  private final Set<String> words = new THashSet<>();

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
    if(contains) return true;
    return null;
  }

  @Override
  public int size() {
    return words.size();
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
  public boolean isEmpty() {
    return words.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    UserDictionary that = (UserDictionary)o;

    return name.equals(that.name);

  }

  @Override
  public void traverse(@NotNull final Consumer<String> consumer) {
    for (String word : words) {
      consumer.consume(word);
    }
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
