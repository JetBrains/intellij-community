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
