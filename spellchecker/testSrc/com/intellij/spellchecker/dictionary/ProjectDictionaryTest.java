// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.dictionary;

import com.intellij.spellchecker.inspection.SpellcheckerInspectionTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.spellchecker.dictionary.Dictionary.LookupStatus.Alien;
import static com.intellij.spellchecker.dictionary.Dictionary.LookupStatus.Present;
import static java.util.Arrays.asList;

public class ProjectDictionaryTest extends SpellcheckerInspectionTestCase {
  private static final String AAAA = "AAAA";
  private static final String BBBB = "BBBB";
  private static final Collection<String> PROJECT_WORDS = asList(AAAA, BBBB);
  private static final ProjectDictionary myProjectDictionary = createProjectDictionary(PROJECT_WORDS);

  private static ProjectDictionary createProjectDictionary(Collection<String> projectWords) {
    String currentUser = System.getProperty("user.name");
    ProjectDictionary projectDictionary = new ProjectDictionary(Collections.singleton(new UserDictionary(currentUser)));
    projectDictionary.setActiveName(currentUser);
    projectDictionary.addToDictionary(projectWords);
    return projectDictionary;
  }

  private static void doContainTest(String wordToCheck) {
    doContainTest(wordToCheck, Present);
  }

  private static void doContainTest(String wordToCheck, @NotNull Dictionary.LookupStatus status) {
    assertEquals(status, myProjectDictionary.lookup(wordToCheck));
  }

  public void testContainsProject() {
    doContainTest(BBBB);
  }

  public void testContainsNeg() {
    doContainTest("eeeee", Alien);
  }

  public void testWords() {
    Set<String> expected = ContainerUtil.newHashSet(AAAA, BBBB);
    assertEquals(expected, myProjectDictionary.getWords());
  }

  public void testEditableWords() {
    Set<String> expected = new HashSet<>(PROJECT_WORDS);
    assertEquals(expected, myProjectDictionary.getEditableWords());
  }

  public void testRemoveProjectWord() {
    ProjectDictionary projectDictionary = createProjectDictionary(PROJECT_WORDS);
    assertEquals(Present, projectDictionary.lookup(BBBB));
    projectDictionary.removeFromDictionary(BBBB);
    assertEquals(Alien, projectDictionary.lookup(BBBB));
  }

  public void testRemoveNotPresented() {
    ProjectDictionary projectDictionary = createProjectDictionary(PROJECT_WORDS);
    String eeee = "eeee";
    assertEquals(Alien, projectDictionary.lookup(eeee));
    projectDictionary.removeFromDictionary(eeee);
    assertEquals(Alien, projectDictionary.lookup(eeee));
  }

  public void testClear() {
    ProjectDictionary projectDictionary = createProjectDictionary(PROJECT_WORDS);
    projectDictionary.clear();

    // current behavior
    assert ContainerUtil.and(PROJECT_WORDS, w -> projectDictionary.lookup(w) == Alien);
  }

  public void testAdd() {
    ProjectDictionary projectDictionary = createProjectDictionary(PROJECT_WORDS);
    projectDictionary.addToDictionary("EEEE");

    assert projectDictionary.lookup("EEEE") == Present;
  }

  public void testAddCollection() {
    ProjectDictionary projectDictionary = createProjectDictionary(PROJECT_WORDS);
    List<String> wordsToAdd = asList("EEEE", "KKKK");
    projectDictionary.addToDictionary(wordsToAdd);

    assert ContainerUtil.and(wordsToAdd, word -> projectDictionary.lookup(word) == Present);
  }

  public void testReplace() {
    ProjectDictionary projectDictionary = createProjectDictionary(PROJECT_WORDS);
    List<String> wordsToReplace = asList("EEEE", "KKKK");
    projectDictionary.replaceAll(wordsToReplace);

    assert ContainerUtil.and(wordsToReplace, word -> projectDictionary.lookup(word) == Present);
    assert ContainerUtil.and(PROJECT_WORDS, projectWord -> projectDictionary.lookup(projectWord) == Alien);
  }

  public void testGetSuggestions() {
    List<String> suggestions = new ArrayList<>();
    myProjectDictionary.consumeSuggestions("AAAB", suggestions::add);
    assert suggestions.isEmpty(); // TODO: change current behavior
  }

  public void testNoSuggestions() {
    List<String> suggestions = new ArrayList<>();
    myProjectDictionary.consumeSuggestions("EEEE", suggestions::add);
    assert suggestions.isEmpty();
  }
}
