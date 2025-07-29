// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.dictionary;


import com.intellij.spellchecker.inspection.SpellcheckerInspectionTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.spellchecker.dictionary.Dictionary.LookupStatus.Alien;
import static com.intellij.spellchecker.dictionary.Dictionary.LookupStatus.Present;
import static java.util.Arrays.asList;

public class AppDictionaryTest extends SpellcheckerInspectionTestCase {
  private static final String AAAA = "AAAA";
  private static final String BBBB = "BBBB";
  private static final Collection<String> APP_WORDS = asList(AAAA, BBBB);
  private static final EditableDictionary APP_DICTIONARY = createAppDictionary(APP_WORDS);

  private static EditableDictionary createAppDictionary(Collection<String> projectWords) {
    EditableDictionary editableDictionary = new UserDictionary("TestName");
    editableDictionary.addToDictionary(projectWords);
    return editableDictionary;
  }

  private static void doContainTest(String wordToCheck) {
    doContainTest(wordToCheck, Present);
  }

  private static void doContainTest(String wordToCheck, @NotNull Dictionary.LookupStatus lookupStatus) {
    assertEquals(lookupStatus, APP_DICTIONARY.lookup(wordToCheck));
  }

  public void testContainsProject() {
    doContainTest(BBBB);
  }

  public void testContainsNeg() {
    doContainTest("eeeee", Alien);
  }

  public void testWords() {
    Set<String> expected = ContainerUtil.newHashSet(AAAA, BBBB);
    assertEquals(expected, APP_DICTIONARY.getWords());
  }

  public void testEditableWords() {
    Set<String> expected = new HashSet<>(APP_WORDS);
    assertEquals(expected, APP_DICTIONARY.getEditableWords());
  }

  public void testRemoveProjectWord() {
    EditableDictionary appDictionary = createAppDictionary(APP_WORDS);
    assertEquals(Present, appDictionary.lookup(BBBB));
    appDictionary.removeFromDictionary(BBBB);
    assertEquals(Alien, appDictionary.lookup(BBBB));
  }

  public void testRemoveNotPresented() {
    EditableDictionary appDictionary = createAppDictionary(APP_WORDS);
    String eeee = "eeee";
    assertEquals(Alien, appDictionary.lookup(eeee));
    appDictionary.removeFromDictionary(eeee);
    assertEquals(Alien, appDictionary.lookup(eeee));
  }

  public void testClear() {
    EditableDictionary appDictionary = createAppDictionary(APP_WORDS);
    appDictionary.clear();

    // current behavior
    assert ContainerUtil.and(APP_WORDS, word -> appDictionary.lookup(word) == Alien);
  }

  public void testAdd() {
    EditableDictionary appDictionary = createAppDictionary(APP_WORDS);
    appDictionary.addToDictionary("EEEE");

    assert appDictionary.lookup("EEEE") == Present;
  }

  public void testAddCollection() {
    EditableDictionary appDictionary = createAppDictionary(APP_WORDS);
    List<String> words = asList("EEEE", "KKKK");
    appDictionary.addToDictionary(words);

    assert ContainerUtil.and(words, word -> appDictionary.lookup(word) == Present);
  }

  public void testReplace() {
    EditableDictionary appDictionary = createAppDictionary(APP_WORDS);
    List<String> wordsToReplace = asList("EEEE", "KKKK");
    appDictionary.replaceAll(wordsToReplace);

    assert ContainerUtil.and(wordsToReplace, word -> appDictionary.lookup(word) == Present);
    assert ContainerUtil.and(APP_WORDS, projectWord -> appDictionary.lookup(projectWord) == Alien);
  }

  public void testGetSuggestions() {
    List<String> suggestions = new ArrayList<>();
    APP_DICTIONARY.consumeSuggestions("AAAB", suggestions::add);
    assert suggestions.isEmpty(); // TODO: change current behavior
  }

  public void testNoSuggestions() {
    List<String> suggestions = new ArrayList<>();
    APP_DICTIONARY.consumeSuggestions("EEEE", suggestions::add);
    assert suggestions.isEmpty();
  }
}
