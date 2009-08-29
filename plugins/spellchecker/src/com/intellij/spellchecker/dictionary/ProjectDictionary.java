package com.intellij.spellchecker.dictionary;

import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 *
 * @author shkate@jetbrains.com
 */
public class ProjectDictionary extends UserDictionary {

  @NotNull
  public List<Dictionary> dictionaries = new ArrayList<Dictionary>();

  public ProjectDictionary() {
  }

  public ProjectDictionary(String name) {
    super(name);
  }

  @Override
  public Set<String> getWords() {
    Set<String> words = new HashSet<String>();
    for (Dictionary dictionary : dictionaries) {
      words.addAll(dictionary.getWords());
    }
    return words;
  }

  @Override
  public void acceptWord(@NotNull String word) {
    getUserDictionary().acceptWord(word);
  }

  @Override
  public void replaceAllWords(Set<String> newWords) {
    getUserDictionary().replaceAllWords(newWords);
  }

  public void setDictionaries(@NotNull List<Dictionary> dictionaries) {
    this.dictionaries = dictionaries;
  }

  @NotNull
  public List<Dictionary> getDictionaries(){
    return dictionaries;
  }

  public Dictionary getUserDictionary() {
    final String name = getCurrentUserName();
    Dictionary userDictionary = null;
    for (Dictionary dictionary : dictionaries) {
      if (dictionary.getName().equals(name)){
        userDictionary = dictionary;
        break;
      }
    }
    if (userDictionary==null){
      userDictionary = new UserDictionary(name);
      dictionaries.add((UserDictionary)userDictionary);
    }
    return userDictionary;
  }

  public static String getCurrentUserName() {
    return System.getProperty("user.name");
  }
}
