// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.dictionary;

import com.intellij.spellchecker.inspection.SpellcheckerInspectionTestCase;

import java.util.*;

import static java.lang.Boolean.TRUE;
import static java.util.Arrays.asList;

public class ProjectDictionaryTest extends SpellcheckerInspectionTestCase {
  private static final String AAAA = "AAAA";
  private static final String BBBB = "BBBB";
  private static final Collection<String> PROJECT_WORDS = asList(AAAA, BBBB);
  private static final ProjectDictionary myProjectDictionary = createProjectDictionary(PROJECT_WORDS);

  private static ProjectDictionary createProjectDictionary(Collection<String> projectWords) {
    String currentUser = System.getProperty("user.name");
    final ProjectDictionary projectDictionary = new ProjectDictionary(Collections.singleton(new UserDictionary(currentUser)));
    projectDictionary.setActiveName(currentUser);
    projectDictionary.addToDictionary(projectWords);
    return projectDictionary;
  }

  private static void doContainTest(String wordToCheck) {
    doContainTest(wordToCheck, TRUE);
  }

  private static void doContainTest(String wordToCheck, Boolean expected) {
    assertEquals(expected, myProjectDictionary.contains(wordToCheck));
  }

  public void testContainsProject() {
    doContainTest(BBBB);
  }

  public void testContainsNeg() {
    doContainTest("eeeee", null);
  }

  public void testSize() {
    assertEquals(2, myProjectDictionary.size());
  }

  public void testWords() {
    final Set<String> expected = new HashSet<>();
    expected.addAll(asList(AAAA, BBBB));
    assertEquals(expected, myProjectDictionary.getWords());
  }

  public void testEditableWords() {
    final Set<String> expected = new HashSet<>();
    expected.addAll(PROJECT_WORDS);
    assertEquals(expected, myProjectDictionary.getEditableWords());
  }

  public void testRemoveProjectWord() {
    final ProjectDictionary projectDictionary = createProjectDictionary(PROJECT_WORDS);
    assertEquals(TRUE, projectDictionary.contains(BBBB));
    projectDictionary.removeFromDictionary(BBBB);
    assertEquals(null, projectDictionary.contains(BBBB));
  }

  public void testRemoveNotPresented() {
    final ProjectDictionary projectDictionary = createProjectDictionary(PROJECT_WORDS);
    final String eeee = "eeee";
    assertEquals(null, projectDictionary.contains(eeee));
    projectDictionary.removeFromDictionary(eeee);
    assertEquals(null, projectDictionary.contains(eeee));
  }

  public void testClear() {
    final ProjectDictionary projectDictionary = createProjectDictionary(PROJECT_WORDS);
    projectDictionary.clear();

    // current behavior
    assert PROJECT_WORDS.stream().allMatch(w -> projectDictionary.contains(w) == null);
  }

  public void testEmpty() {
    final ProjectDictionary projectDictionary = createProjectDictionary(asList());
    assertFalse(projectDictionary.isEmpty()); // current behavior
  }

  public void testNotEmpty() {
    assertFalse(myProjectDictionary.isEmpty());
  }

  public void testAdd() {
    final ProjectDictionary projectDictionary = createProjectDictionary(PROJECT_WORDS);
    projectDictionary.addToDictionary("EEEE");

    assert projectDictionary.contains("EEEE");
  }

  public void testAddCollection() {
    final ProjectDictionary projectDictionary = createProjectDictionary(PROJECT_WORDS);
    final List<String> wordsToAdd = asList("EEEE", "KKKK");
    projectDictionary.addToDictionary(wordsToAdd);

    assert wordsToAdd.stream().allMatch(projectDictionary::contains);
  }

  public void testReplace() {
    final ProjectDictionary projectDictionary = createProjectDictionary(PROJECT_WORDS);
    final List<String> wordsToReplace = asList("EEEE", "KKKK");
    projectDictionary.replaceAll(wordsToReplace);

    assert wordsToReplace.stream().allMatch(projectDictionary::contains);
    assert PROJECT_WORDS.stream().allMatch(projectWord -> projectDictionary.contains(projectWord) == null);
  }

  public void testTraverse() {
    final Set<String> traversedWords = new HashSet<>();
    myProjectDictionary.traverse(traversedWords::add);

    assertEquals(traversedWords, myProjectDictionary.getWords());
  }

  public void testGetSuggestions(){
    final List<String> suggestions = new ArrayList<>();
    myProjectDictionary.getSuggestions("AAAB", suggestions::add);
    assert suggestions.isEmpty(); // TODO: change current behavior
  }

  public void testNoSuggestions(){
    final List<String> suggestions = new ArrayList<>();
    myProjectDictionary.getSuggestions("EEEE", suggestions::add);
    assert suggestions.isEmpty();
  }
}
