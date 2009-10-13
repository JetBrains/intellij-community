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
import com.intellij.spellchecker.trie.CharSequenceKeyAnalyzer;
import com.intellij.spellchecker.trie.PatriciaTrie;
import com.intellij.spellchecker.trie.Trie;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class UserDictionary implements Dictionary {

  private String name;
  private Trie<String, String> trie = new PatriciaTrie<String, String>(new CharSequenceKeyAnalyzer());

  public UserDictionary(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public boolean contains(String word) {
    return word != null && trie.containsKey(word);
  }

  @Nullable
  public Set<String> getWords() {
    return trie.keySet();
  }

  @Nullable
  public Set<String> getEditableWords() {
    return trie.keySet();
  }

  @Nullable
  public Set<String> getNotEditableWords() {
    return null;
  }

  public void clear() {
    trie.clear();
  }


  public void addToDictionary(String word) {
    if (word == null) {
      return;
    }
    trie.put(word, word);
  }

  public void removeFromDictionary(String word) {
    if (word == null) {
      return;
    }
    trie.remove(word);
  }

  public void replaceAll(@Nullable Collection<String> words) {
    clear();
    addToDictionary(words);
  }

  public void addToDictionary(@Nullable Collection<String> words) {
    if (words == null || words.isEmpty()) {
      return;
    }
    for (String word : words) {
      addToDictionary(word);
    }
  }

  public boolean isEmpty() {
    return (trie == null || trie.size() == 0);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    UserDictionary that = (UserDictionary)o;

    return !(name != null ? !name.equals(that.name) : that.name != null);

  }

  public void traverse(final Action action){
    trie.traverse(new PatriciaTrie.Cursor<String, String>() {
          public SelectStatus select(Map.Entry<? extends String, ? extends String> entry) {
            action.run(entry);
            return SelectStatus.CONTINUE;
          }
        });
  }

  @Override
  public int hashCode() {
    return name != null ? name.hashCode() : 0;
  }

  @Override
  public String toString() {
    return "UserDictionary{" + "name='" + name + '\'' + ", words.count=" + trie.size() + '}';
  }
}
