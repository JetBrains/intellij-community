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
