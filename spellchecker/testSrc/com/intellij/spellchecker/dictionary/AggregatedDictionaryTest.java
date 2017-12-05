// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.dictionary;


import com.intellij.spellchecker.inspection.SpellcheckerInspectionTestCase;
import com.intellij.util.containers.HashSet;

import java.util.*;

import static java.lang.Boolean.TRUE;
import static java.util.Arrays.asList;

public class AggregatedDictionaryTest extends SpellcheckerInspectionTestCase {
  private static final String AAAA = "AAAA";
  private static final String BBBB = "BBBB";
  private static final Collection<String> PROJECT_WORDS = asList(AAAA, BBBB);
  private static final String CCCC = "CCCC";
  private static final String DDDD = "DDDD";
  private static final Collection<String> APP_WORDS = asList(CCCC, DDDD);
  private static final AggregatedDictionary myAggregatedDictionary = createAggregatedDictionary(PROJECT_WORDS, APP_WORDS);

  private static AggregatedDictionary createAggregatedDictionary(Collection<String> projectWords, Collection<String> appWords) {
    String currentUser = System.getProperty("user.name");
    final ProjectDictionary projectDictionary = new ProjectDictionary(Collections.singleton(new UserDictionary(currentUser)));
    projectDictionary.setActiveName(currentUser);
    projectDictionary.addToDictionary(projectWords);

    final EditableDictionary editableDictionary = new UserDictionary("TestName");
    editableDictionary.addToDictionary(appWords);
    return new AggregatedDictionary(projectDictionary, editableDictionary);
  }

  private static void doContainTest(String wordToCheck) {
    doContainTest(wordToCheck, TRUE);
  }

  private static void doContainTest(String wordToCheck, Boolean expected) {
    assertEquals(expected, myAggregatedDictionary.contains(wordToCheck));
  }

  public void testContainsAppWord() {
    doContainTest(CCCC);
  }

  public void testContainsProject() {
    doContainTest(BBBB);
  }

  public void testContainsNeg() {
    doContainTest("eeeee", null);
  }

  public void testSize() {
    assertEquals(4, myAggregatedDictionary.size());
  }

  public void testWords() {
    final Set<String> expected = new HashSet<>();
    expected.addAll(asList(AAAA, BBBB, CCCC, DDDD));
    assertEquals(expected, myAggregatedDictionary.getWords());
  }

  public void testEditableWords() {
    final Set<String> expected = new HashSet<>();
    expected.addAll(PROJECT_WORDS); // current behavior
    assertEquals(expected, myAggregatedDictionary.getEditableWords());
  }

  public void testRemoveProjectWord() {
    final AggregatedDictionary aggregatedDictionary = createAggregatedDictionary(PROJECT_WORDS, APP_WORDS);
    assertEquals(TRUE, aggregatedDictionary.contains(BBBB));
    aggregatedDictionary.removeFromDictionary(BBBB);
    assertEquals(null, aggregatedDictionary.contains(BBBB));
  }

  public void testRemoveAppWord() {
    final AggregatedDictionary aggregatedDictionary = createAggregatedDictionary(PROJECT_WORDS, APP_WORDS);
    assertEquals(TRUE, aggregatedDictionary.contains(CCCC));
    aggregatedDictionary.removeFromDictionary(CCCC);
    assertEquals(null, aggregatedDictionary.contains(CCCC));
  }

  public void testRemoveNotPresented() {
    final AggregatedDictionary aggregatedDictionary = createAggregatedDictionary(PROJECT_WORDS, APP_WORDS);
    final String eeee = "eeee";

    assertEquals(null, aggregatedDictionary.contains(eeee));
    aggregatedDictionary.removeFromDictionary(eeee);
    assertEquals(null, aggregatedDictionary.contains(eeee));
  }

  public void testClear() {
    final AggregatedDictionary aggregatedDictionary = createAggregatedDictionary(PROJECT_WORDS, APP_WORDS);
    aggregatedDictionary.clear();

    // current behavior
    assert APP_WORDS.stream().allMatch(aggregatedDictionary::contains);
    assert PROJECT_WORDS.stream().allMatch(aggregatedDictionary::contains);
  }

  public void testEmpty() {
    final AggregatedDictionary aggregatedDictionary = createAggregatedDictionary(asList(), asList());
    assertFalse(aggregatedDictionary.isEmpty()); // current behavior
  }

  public void testNotEmpty() {
    assertFalse(myAggregatedDictionary.isEmpty());
  }

  public void testAdd() {
    final AggregatedDictionary aggregatedDictionary = createAggregatedDictionary(PROJECT_WORDS, APP_WORDS);
    aggregatedDictionary.addToDictionary("EEEE");

    assert aggregatedDictionary.contains("EEEE");
  }

  public void testAddCollection() {
    final AggregatedDictionary aggregatedDictionary = createAggregatedDictionary(PROJECT_WORDS, APP_WORDS);
    final List<String> wordsToAdd = asList("EEEE", "KKKK");
    aggregatedDictionary.addToDictionary(wordsToAdd);

    assert wordsToAdd.stream().allMatch(aggregatedDictionary::contains);
  }

  public void testReplace() {
    final AggregatedDictionary aggregatedDictionary = createAggregatedDictionary(PROJECT_WORDS, APP_WORDS);
    final List<String> wordsToReplace = asList("EEEE", "KKKK");
    aggregatedDictionary.replaceAll(wordsToReplace);

    assert wordsToReplace.stream().allMatch(aggregatedDictionary::contains);
    assert PROJECT_WORDS.stream().allMatch(projectWord -> aggregatedDictionary.contains(projectWord) == null);
    assert APP_WORDS.stream().allMatch(aggregatedDictionary::contains);
  }

  public void testTraverse() {
    final List<String> traversedWords = new ArrayList<>();
    myAggregatedDictionary.traverse(traversedWords::add);

    assertEquals(traversedWords, new ArrayList<>(myAggregatedDictionary.getCachedDictionary().getWords()));
  }
  
  public void testGetSuggestions(){
    final List<String> suggestions = new ArrayList<>();
    myAggregatedDictionary.getSuggestions("AAAB", suggestions::add);
    assert suggestions.size() == 1;
    assert suggestions.contains("AAAA");
  }

  public void testNoSuggestions(){
    final List<String> suggestions = new ArrayList<>();
    myAggregatedDictionary.getSuggestions("EEEE", suggestions::add);
    assert suggestions.isEmpty();
  }
}
