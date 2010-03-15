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
package com.intellij.spellchecker.engine;

import com.intellij.spellchecker.dictionary.Dictionary;
import com.intellij.spellchecker.dictionary.Loader;
import com.intellij.spellchecker.dictionary.UserDictionary;
import com.intellij.spellchecker.trie.Action;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;


public class BaseSpellChecker implements SpellCheckerEngine {

  private Dictionary engineDictionary;

  private Transformation transform = new Transformation();

  private Metrics metrics = new LevenshteinDistance();

  private Consumer<String> consumer = new Consumer<String>() {
    public void consume(@Nullable String word) {
      final String transformed = transform.transform(word);
      if (transformed != null) {
        engineDictionary.addToDictionary(transformed);
      }
    }
  };

  public BaseSpellChecker() {
    ensureEngineDictionary();
  }

  public void loadDictionary(@NotNull Loader loader) {
    loader.load(consumer);
  }

  public Transformation getTransformation() {
    return transform;
  }

  private void ensureEngineDictionary() {
    if (engineDictionary == null) {
      engineDictionary = new UserDictionary("engine");
    }
  }


  public void addToDictionary(String word) {
    final String transformed = transform.transform(word);
    if (transformed != null) {
      engineDictionary.addToDictionary(transformed);
    }
  }

  public boolean isCorrect(@NotNull String word) {
    final String transformed = transform.transform(word);
    return transformed != null && engineDictionary.contains(transformed);
  }

  @NotNull
  public List<String> getSuggestions(final @NotNull String word, int threshold, int quality) {
    final String transformed = transform.transform(word);
    if (transformed == null) {
      return Collections.emptyList();
    }
    final List<Suggestion> suggestions = new ArrayList<Suggestion>();
    engineDictionary.traverse(new Action() {
      public void run(String entry) {
        if (transformed.charAt(0) == entry.charAt(0)/* && Math.abs(mpw.length() - entry.getKey().length()) <= 1*/) {
          final int distance = metrics.calculateMetrics(transformed, entry);
          suggestions.add(new Suggestion(entry, distance));
        }
      }
    });
    List<String> result = new ArrayList<String>();
    if (suggestions.isEmpty()) {
      return result;
    }
    Collections.sort(suggestions);
    int bestMetrics = suggestions.get(0).getMetrics();
    for (int i = 0; i < threshold; i++) {

      if (suggestions.size() < i || bestMetrics - suggestions.get(i).getMetrics() > quality) {
        break;
      }
      result.add(i, suggestions.get(i).getWord());
    }
    return result;
  }

  @NotNull
  public List<String> getVariants(@NotNull String prefix) {
    return null;
  }

  public void reset() {
    engineDictionary = null;
    ensureEngineDictionary();
  }
}
