/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.suggestionList;

import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Builds list of suggestions.
 * You may create suggestions from words, you may add new groups and do other useful things.
 * It works like chain pattern, so it returns itself.
 *
 * @author Ilya.Kazakevich
 */
public class SuggestionsBuilder {
  @NotNull
  private final List<List<Suggestion>> myList = new ArrayList<List<Suggestion>>();

  public SuggestionsBuilder() {
    myList.add(new ArrayList<Suggestion>());
  }

  /**
   * @param words list of words to add (to the first group)
   * @param sort  sort passed words
   */
  public SuggestionsBuilder(@NotNull List<String> words, final boolean sort) {
    this();
    if (sort) {
      // No guarantee passed argument is mutable
      //noinspection AssignmentToMethodParameter
      words = new ArrayList<String>(words);
      Collections.sort(words);
    }
    add(words);
  }

  /**
   * Creates next group and sets it as default
   */
  public SuggestionsBuilder nextGroup() {
    if (!getCurrentGroup().isEmpty()) {
      myList.add(new ArrayList<Suggestion>());
    }
    return this;
  }

  /**
   * @param words words to add to current group
   */
  public final SuggestionsBuilder add(@NotNull final String... words) {
    return add(Arrays.asList(words));
  }

  /**
   * @param words words to add to current group
   */
  public final SuggestionsBuilder add(@NotNull final Collection<String> words) {
    for (final String word : words) {
      add(word, false);
    }
    return this;
  }

  /**
   * @param text   text to add to current group
   * @param strong strong or not
   */
  public SuggestionsBuilder add(@NotNull final String text, final boolean strong) {
    getCurrentGroup().add(new Suggestion(text, strong));
    return this;
  }

  /**
   * @return elements from current group
   */
  @NotNull
  private List<Suggestion> getCurrentGroup() {
    return myList.get(myList.size() - 1);
  }

  /**
   * @return all suggestions in format [group1[sugg1, sugg2]]
   */
  @NotNull
  List<List<Suggestion>> getList() {
    return Collections.unmodifiableList(myList);
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SuggestionsBuilder)) return false;

    SuggestionsBuilder builder = (SuggestionsBuilder)o;

    if (!myList.equals(builder.myList)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myList.hashCode();
  }

  @Override
  public String toString() {
    return "SuggestionsBuilder{" +
           "myList=" + myList +
           '}';
  }
}
