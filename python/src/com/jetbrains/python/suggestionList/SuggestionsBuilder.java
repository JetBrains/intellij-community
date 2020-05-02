// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.suggestionList;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Builds list of suggestions.
 * <p/>
 * You may create suggestions from words, you may add new groups and do other useful things.
 * It works like chain pattern, so it returns itself.
 * <p/>
 * Builder consists of 2 groups: prefix (first one) and main (second one) group.
 * Each may be empty.
 * Use {@link #changeGroup(boolean)} to switch between them
 *
 * @author Ilya.Kazakevich
 */
public class SuggestionsBuilder {
  private static final MySuggestionComparator SUGGESTION_COMPARATOR = new MySuggestionComparator();
  @NotNull
  private final List<Suggestion> myPrefixGroup = new ArrayList<>();
  @NotNull
  private final List<Suggestion> myMainGroup = new ArrayList<>();
  @NotNull
  private List<Suggestion> myCurrentGroup = myMainGroup;

  public SuggestionsBuilder() {

  }

  /**
   * @param words list of words to add (to the first group)
   */
  public SuggestionsBuilder(@NotNull final List<String> words) {
    add(words);
  }

  /**
   * Switches to the next group and sets it as default
   *
   * @param main use main group if true, prefix otherwise
   */
  public SuggestionsBuilder changeGroup(final boolean main) {
    myCurrentGroup = (main ? myMainGroup : myPrefixGroup);
    return this;
  }

  /**
   * @param words words to add to current group
   */
  public final SuggestionsBuilder add(final String @NotNull ... words) {
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
    myCurrentGroup.add(new Suggestion(text, strong));
    myCurrentGroup.sort(SUGGESTION_COMPARATOR);
    return this;
  }


  /**
   * @return all suggestions in format [group1[sugg1, sugg2]]
   */
  @NotNull
  List<List<Suggestion>> getList() {
    return ContainerUtil.immutableList(myPrefixGroup, myMainGroup);
  }


  @Override
  public String toString() {
    return "SuggestionsBuilder{" +
           "myPrefixGroup=" + myPrefixGroup +
           ", myMainGroup=" + myMainGroup +
           ", myCurrentGroup=" + myCurrentGroup +
           '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SuggestionsBuilder)) return false;

    SuggestionsBuilder builder = (SuggestionsBuilder)o;

    if (!myPrefixGroup.equals(builder.myPrefixGroup)) return false;
    if (!myMainGroup.equals(builder.myMainGroup)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myPrefixGroup.hashCode();
    result = 31 * result + myMainGroup.hashCode();
    return result;
  }

  private static class MySuggestionComparator implements Comparator<Suggestion> {
    @Override
    public int compare(final Suggestion o1, final Suggestion o2) {
      return o1.getText().compareTo(o2.getText());
    }
  }
}
