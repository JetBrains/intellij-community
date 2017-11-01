/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.spellchecker.compress;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.spellchecker.DefaultBundledDictionariesProvider;
import com.intellij.spellchecker.StreamLoader;
import com.intellij.spellchecker.dictionary.Dictionary;
import com.intellij.spellchecker.dictionary.Loader;
import com.intellij.spellchecker.engine.Transformation;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.Consumer;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Set;

import static com.intellij.openapi.util.Pair.pair;
import static org.junit.Assert.*;

public class DictionaryTest {
  private static final String JETBRAINS_DIC = "jetbrains.dic";
  private static final String ENGLISH_DIC = "english.dic";

  private final Transformation myTransformation = new Transformation();

  @Test
  public void testJBDictionaryPerformance() {
    Dictionary dictionary = loadDictionaryPerformanceTest(JETBRAINS_DIC, 10);
    containsWordPerformanceTest(dictionary, 5);
    containsWordTest(dictionary);
  }

  @Test
  public void testEnglishDictionaryPerformance() {
    Dictionary dictionary = loadDictionaryPerformanceTest(ENGLISH_DIC, 500);
    containsWordPerformanceTest(dictionary, 200);
    containsWordTest(dictionary);
  }

  @Test
  public void testDictionaryLoadedFully() {
    final Set<String> onDisk = new THashSet<>();
    getLoader(JETBRAINS_DIC).load(s -> {
      assertNotNull(s);
      String t = myTransformation.transform(s);
      if (t != null) {
        onDisk.add(t);
      }
    });

    Dictionary dictionary = CompressedDictionary.create(getLoader(JETBRAINS_DIC), myTransformation);

    assertEquals(onDisk, dictionary.getWords());
  }
  
  @Test
  public void testGetSuggestions(){
    final ArrayList<String> suggestions = new ArrayList<>();
    Dictionary dictionary = CompressedDictionary.create(getLoader(ENGLISH_DIC), myTransformation);
    dictionary.getSuggestions("typpo",suggestions::add);
    assert suggestions.contains("typo");
    assert suggestions.stream().anyMatch(a -> a.charAt(0) == 't');
  }

  @Test
  public void testNoSuggestions(){
    final ArrayList<String> suggestions = new ArrayList<>();
    Dictionary dictionary = CompressedDictionary.create(getLoader(ENGLISH_DIC), myTransformation);
    dictionary.getSuggestions("руссский",suggestions::add);
    assert suggestions.isEmpty();
  }


  private Dictionary loadDictionaryPerformanceTest(final String name, int time) {
    final Ref<Dictionary> ref = Ref.create();

    PlatformTestUtil.startPerformanceTest(
      "load dictionary", time, () -> ref.set(CompressedDictionary.create(getLoader(name), myTransformation))
    ).assertTiming();

    assertFalse(ref.isNull());
    return ref.get();
  }

  private void containsWordPerformanceTest(final Dictionary dictionary, int time) {
    final Set<String> wordsToCheck = createWordSets(dictionary, 50000, 1).first;
    PlatformTestUtil.startPerformanceTest("contains word", time, () -> {
      for (String s : wordsToCheck) {
        assertEquals(Boolean.TRUE, dictionary.contains(s));
      }
    }).assertTiming();
  }

  private void containsWordTest(Dictionary dictionary) {
    Pair<Set<String>, Set<String>> sets = createWordSets(dictionary, 50000, 2);
    CompressedDictionary half = CompressedDictionary.create(new TestLoader(sets.first), myTransformation);
    for (String s : sets.second) {
      if (!sets.first.contains(s)) {
        assertEquals(s, Boolean.FALSE, half.contains(s));
      }
    }
  }

  private static StreamLoader getLoader(@NotNull String name) {
    return new StreamLoader(DefaultBundledDictionariesProvider.class.getResourceAsStream(name), name);
  }

  private Pair<Set<String>, Set<String>> createWordSets(Dictionary dictionary, int maxCount, int mod) {
    Set<String> wordsToStore = new THashSet<>();
    Set<String> wordsToCheck = new THashSet<>();

    Set<String> words = dictionary.getWords();
    assertNotNull(words);
    int counter = 0;
    for (String s : words) {
      String transformed = myTransformation.transform(s);
      if (transformed != null) {
        (counter % mod == 0 ? wordsToStore : wordsToCheck).add(transformed);
        if (++counter > maxCount) break;
      }
    }

    return pair(wordsToStore, wordsToCheck);
  }

  private static class TestLoader implements Loader {
    private final Set<String> myWords;

    public TestLoader(Set<String> words) {
      myWords = words;
    }

    @Override
    public void load(@NotNull Consumer<String> consumer) {
      for (String word : myWords) {
        consumer.consume(word);
      }
    }

    @Override
    public String getName() {
      return "test";
    }
  }
}
