/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.spellchecker.DefaultBundledDictionariesProvider;
import com.intellij.spellchecker.StreamLoader;
import com.intellij.spellchecker.dictionary.Dictionary;
import com.intellij.spellchecker.dictionary.Loader;
import com.intellij.spellchecker.engine.Transformation;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ThrowableRunnable;
import gnu.trove.THashSet;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

@SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors"})
public class DictionaryTest extends TestCase {

  private Dictionary dictionary;

  private final Map<String, Integer> sizes = new HashMap<String, Integer>();
  private final Map<String, Integer> times = new HashMap<String, Integer>();
  private static final String JETBRAINS_DIC = "jetbrains.dic";
  private static final String ENGLISH_DIC = "english.dic";

  {
    sizes.put(JETBRAINS_DIC, 1000);
    sizes.put(ENGLISH_DIC, 140000);
  }

  {
    times.put(JETBRAINS_DIC, 1000);
    times.put(ENGLISH_DIC, 50000);
  }

  public void testDictionary() throws IOException {
    final String[] names = {JETBRAINS_DIC, ENGLISH_DIC};
    for (String name : names) {
      loadDictionaryTest(name, sizes.get(name));
      loadHalfDictionaryTest(name, 50000);
    }
  }

  public void testDictionaryLoadedFully() {
    //cleanupDictionary();
    final Transformation transform = new Transformation();
    CompressedDictionary dictionary = CompressedDictionary.create(englishLoader(), transform);

    final Set<String> onDisk = new THashSet<String>();
    englishLoader().load(new Consumer<String>() {
      @Override
      public void consume(String s) {
        assert s != null;
        String t = transform.transform(s);
        if (t == null) {
          return;
        }
        onDisk.add(t);
      }
    });

    List<String> odList = new ArrayList<String>(onDisk);
    Collections.sort(odList);
    List<String> loaded = new ArrayList<String>(dictionary.getWords());
    Collections.sort(loaded);

    assertEquals(odList, loaded);
  }

  public void cleanupDictionary() {
    final Set<String> onDisk = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);
    englishLoader().load(new Consumer<String>() {
      @Override
      public void consume(String s) {
        assert s != null;
        onDisk.add(s);
      }
    });

    List<String> odList = new ArrayList<String>(onDisk);
    Collections.sort(odList);

    File file = new File("C:\\Work\\Idea\\community\\spellchecker\\src\\com\\intellij\\spellchecker\\english.2");
    try {
      FileUtil.writeToFile(file, StringUtil.join(odList, "\n"));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static StreamLoader englishLoader() {
    return new StreamLoader(DefaultBundledDictionariesProvider.class.getResourceAsStream(ENGLISH_DIC), ENGLISH_DIC);
  }

  public void loadDictionaryTest(@NotNull final String name, int wordCount) throws IOException {
    final Transformation transform = new Transformation();
    PlatformTestUtil.startPerformanceTest("load dictionary", times.get(name), new ThrowableRunnable() {
      @Override
      public void run() throws Exception {
        dictionary = CompressedDictionary
          .create(new StreamLoader(DefaultBundledDictionariesProvider.class.getResourceAsStream(name), name), transform);
      }
    }).cpuBound().assertTiming();

    final Set<String> wordsToStoreAndCheck = createWordSets(name, 50000, 1).getFirst();
    PlatformTestUtil.startPerformanceTest("words contain",2000, new ThrowableRunnable() {
      @Override
      public void run() throws Exception {
        for (String s : wordsToStoreAndCheck) {
          assertTrue(dictionary.contains(s));
        }
      }
    }).cpuBound().assertTiming();
  }

  private static Loader createLoader(final Set<String> words) {
    return new Loader() {
      @Override
      public void load(@NotNull Consumer<String> consumer) {
        for (String word : words) {
          consumer.consume(word);
        }
      }

      @Override
      public String getName() {
        return "test";
      }
    };
  }

  @SuppressWarnings({"unchecked"})
  private static Pair<Set<String>, Set<String>> createWordSets(String name, final int maxCount, final int mod) {
    Loader loader = new StreamLoader(DefaultBundledDictionariesProvider.class.getResourceAsStream(name), name);
    final Set<String> wordsToStore = new THashSet<String>();
    final Set<String> wordsToCheck = new THashSet<String>();
    final Transformation transform = new Transformation();
    loader.load(new Consumer<String>() {
      private int counter = 0;

      @Override
      public void consume(String s) {
        if (counter > maxCount) {
          return;
        }
        String transformed = transform.transform(s);
        if (transformed != null) {

          if (counter % mod == 0) {
            wordsToStore.add(transformed);
          }
          else {
            wordsToCheck.add(transformed);
          }
          counter++;
        }
      }
    });

    return new Pair(wordsToStore, wordsToCheck);
  }


  public static void loadHalfDictionaryTest(final String name, final int maxCount) {
    final Pair<Set<String>, Set<String>> sets = createWordSets(name, maxCount, 2);
    final Loader loader = createLoader(sets.getFirst());
    CompressedDictionary dictionary = CompressedDictionary.create(loader, new Transformation());
    for (String s : sets.getSecond()) {
      if (!sets.getFirst().contains(s)) {
        assertFalse(s, dictionary.contains(s));
      }
    }
  }


}
