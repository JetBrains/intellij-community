// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.dictionary;


import com.intellij.spellchecker.inspection.SpellcheckerInspectionTestCase;

import java.util.*;

import static java.lang.Boolean.TRUE;
import static java.util.Arrays.asList;

public class AppDictionaryTest extends SpellcheckerInspectionTestCase {
  private static final String AAAA = "AAAA";
  private static final String BBBB = "BBBB";
  private static final Collection<String> APP_WORDS = asList(AAAA, BBBB);
  private static final EditableDictionary APP_DICTIONARY = createAppDictionary(APP_WORDS);

  private static EditableDictionary createAppDictionary(Collection<String> projectWords) {
    final EditableDictionary editableDictionary = new UserDictionary("TestName");
    editableDictionary.addToDictionary(APP_WORDS);
    return editableDictionary;
  }

  private static void doContainTest(String wordToCheck) {
    doContainTest(wordToCheck, TRUE);
  }

  private static void doContainTest(String wordToCheck, Boolean expected) {
    assertEquals(expected, APP_DICTIONARY.contains(wordToCheck));
  }

  public void testContainsProject() {
    doContainTest(BBBB);
  }

  public void testContainsNeg() {
    doContainTest("eeeee", null);
  }

  public void testSize() {
    assertEquals(2, APP_DICTIONARY.size());
  }

  public void testWords() {
    final Set<String> expected = new HashSet<>();
    expected.addAll(asList(AAAA, BBBB));
    assertEquals(expected, APP_DICTIONARY.getWords());
  }

  public void testEditableWords() {
    final Set<String> expected = new HashSet<>();
    expected.addAll(APP_WORDS);
    assertEquals(expected, APP_DICTIONARY.getEditableWords());
  }

  public void testRemoveProjectWord() {
    final EditableDictionary appDictionary = createAppDictionary(APP_WORDS);
    assertEquals(TRUE, appDictionary.contains(BBBB));
    appDictionary.removeFromDictionary(BBBB);
    assertEquals(null, appDictionary.contains(BBBB));
  }

  public void testRemoveNotPresented() {
    final EditableDictionary appDictionary = createAppDictionary(APP_WORDS);
    final String eeee = "eeee";
    assertEquals(null, appDictionary.contains(eeee));
    appDictionary.removeFromDictionary(eeee);
    assertEquals(null, appDictionary.contains(eeee));
  }

  public void testClear() {
    final EditableDictionary appDictionary = createAppDictionary(APP_WORDS);
    appDictionary.clear();

    // current behavior
    assert APP_WORDS.stream().allMatch(w -> appDictionary.contains(w) == null);
  }

  public void testEmpty() {
    final EditableDictionary appDictionary = createAppDictionary(asList());
    assertFalse(appDictionary.isEmpty()); // current behavior
  }

  public void testNotEmpty() {
    assertFalse(APP_DICTIONARY.isEmpty());
  }

  public void testAdd() {
    final EditableDictionary appDictionary = createAppDictionary(APP_WORDS);
    appDictionary.addToDictionary("EEEE");

    assert appDictionary.contains("EEEE");
  }

  public void testAddCollection() {
    final EditableDictionary appDictionary = createAppDictionary(APP_WORDS);
    final List<String> wordsToAdd = asList("EEEE", "KKKK");
    appDictionary.addToDictionary(wordsToAdd);

    assert wordsToAdd.stream().allMatch(appDictionary::contains);
  }

  public void testReplace() {
    final EditableDictionary appDictionary = createAppDictionary(APP_WORDS);
    final List<String> wordsToReplace = asList("EEEE", "KKKK");
    appDictionary.replaceAll(wordsToReplace);

    assert wordsToReplace.stream().allMatch(appDictionary::contains);
    assert APP_WORDS.stream().allMatch(projectWord -> appDictionary.contains(projectWord) == null);
  }

  public void testTraverse() {
    final Set<String> traversedWords = new HashSet<>();
    APP_DICTIONARY.traverse(traversedWords::add);

    assertEquals(traversedWords, APP_DICTIONARY.getWords());
  }

  public void testGetSuggestions(){
    final List<String> suggestions = new ArrayList<>();
    APP_DICTIONARY.getSuggestions("AAAB", suggestions::add);
    assert suggestions.isEmpty(); // TODO: change current behavior
  }

  public void testNoSuggestions(){
    final List<String> suggestions = new ArrayList<>();
    APP_DICTIONARY.getSuggestions("EEEE", suggestions::add);
    assert suggestions.isEmpty();
  }
}
