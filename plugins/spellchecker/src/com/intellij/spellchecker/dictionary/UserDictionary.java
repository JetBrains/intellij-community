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

import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 *
 * @author shkate@jetbrains.com
 */
@Tag("dictionary")
public class UserDictionary implements Dictionary {

  @Tag("words")
  @AbstractCollection(surroundWithTag = false,elementTag = "w",elementValueAttribute = "")
  public Set<String> words = new HashSet<String>();

  @Attribute(NAME_ATTRIBUTE)
  public String name = "new";
  private static final String NAME_ATTRIBUTE = "name";

  public UserDictionary() {
  }

  public UserDictionary(String name) {
    this.name = name;
  }

  @NotNull
  public String getName() {
    return name;
  }


  public Set<String> getWords() {
    return Collections.unmodifiableSet(words);
  }

  public void acceptWord(@NotNull String word) {
    words.add(word);
  }

  public void replaceAllWords(Set<String> newWords) {
    replaceAll(words, newWords);
  }

  private static void replaceAll(Set<String> words, Set<String> newWords) {
    words.clear();
    words.addAll(newWords);
  }

}
