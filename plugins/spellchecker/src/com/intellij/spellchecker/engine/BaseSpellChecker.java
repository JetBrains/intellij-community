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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.spellchecker.compress.CompressedDictionary;
import com.intellij.spellchecker.dictionary.Dictionary;
import com.intellij.spellchecker.dictionary.EditableDictionary;
import com.intellij.spellchecker.dictionary.EditableDictionaryLoader;
import com.intellij.spellchecker.dictionary.Loader;
import com.intellij.util.Consumer;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;


public class BaseSpellChecker implements SpellCheckerEngine {


  private final Transformation transform = new Transformation();

  private final Set<EditableDictionary> dictionaries = new THashSet<EditableDictionary>();
  private final Set<Dictionary> bundledDictionaries = new THashSet<Dictionary>();
  private final Metrics metrics = new LevenshteinDistance();


  public BaseSpellChecker() {
  }


  public void loadDictionary(@NotNull Loader loader) {
    if (loader instanceof EditableDictionaryLoader) {
      final EditableDictionary dictionary = ((EditableDictionaryLoader)loader).getDictionary();
      if (dictionary != null) {
        addModifiableDictionary(dictionary);
      }
    }
    else {
      loadFixedDictionary(loader);
    }

  }

  private void loadFixedDictionary(final @NotNull Loader loader) {
    /*if (ApplicationManager.getApplication().isUnitTestMode()) {
      loadCompressedDictionary(loader);
    }
    else {
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        public void run() {
          loadCompressedDictionary(loader);
        }
      });
    }*/
    loadCompressedDictionary(loader);
  }

  private void loadCompressedDictionary(@NotNull Loader loader) {
    final CompressedDictionary dictionary = CompressedDictionary.create(loader, transform);
    if (dictionary != null) {
      addCompressedFixedDictionary(dictionary);
    }
  }

  private void addModifiableDictionary(@NotNull EditableDictionary dictionary) {
    dictionaries.add(dictionary);
  }

  private void addCompressedFixedDictionary(@NotNull Dictionary dictionary) {
    bundledDictionaries.add(dictionary);
  }

  public Transformation getTransformation() {
    return transform;
  }

  @NotNull
  private static List<String> restore(char startFrom, int i, int j, @Nullable Collection dictionaries) {
    if (dictionaries == null) {
      return Collections.emptyList();
    }
    List<String> results = new ArrayList<String>();

    for (Object o : dictionaries) {
      if (o instanceof Dictionary) {
        results.addAll(restore(startFrom, i, j, (Dictionary)o));
      }

    }
    return results;
  }

  @NotNull
  private static List<String> restore(final char first, final int i, final int j, @Nullable Dictionary dictionary) {
    if (dictionary == null) {
      return Collections.emptyList();
    }
    final List<String> result = new ArrayList<String>();
    if (dictionary instanceof CompressedDictionary) {
      result.addAll(((CompressedDictionary)dictionary).getWords(first));
    }
    else {
      dictionary.traverse(new Consumer<String>() {
        public void consume(String s) {
          if (StringUtil.isEmpty(s)) {
            return;
          }
          if (s.charAt(0) == first && s.length() >= i && s.length() <= j) {
            result.add(s);
          }
        }
      });
    }

    return result;
  }

  private static boolean isCorrect(@NotNull String transformed, @Nullable Collection dictionaries) {
    if (dictionaries == null) {
      return true;
    }

    for (Object o : dictionaries) {
      if (o instanceof Dictionary) {
        boolean result = isCorrect(transformed, (Dictionary)o);
        if (result) {
          return true;
        }
      }

    }
    return false;
  }

  private static boolean isCorrect(@NotNull String transformed, @Nullable Dictionary dictionary) {
    if (dictionary == null) {
      return true;
    }

    return dictionary.contains(transformed);
  }

  public boolean isCorrect(@NotNull String word) {
    final String transformed = transform.transform(word);
    if (transformed == null) {
      return true;
    }
    return isCorrect(transformed, bundledDictionaries) || isCorrect(transformed, dictionaries);


  }


  @NotNull
  public List<String> getSuggestions(final @NotNull String word, int threshold, int quality) {
    final String transformed = transform.transform(word);
    if (transformed == null) {
      return Collections.emptyList();
    }
    final List<Suggestion> suggestions = new ArrayList<Suggestion>();
    List<String> rawSuggestions = restore(transformed.charAt(0), 0, Integer.MAX_VALUE, bundledDictionaries);
    rawSuggestions.addAll(restore(word.charAt(0), 0, Integer.MAX_VALUE, dictionaries));
    for (String rawSuggestion : rawSuggestions) {
      final int distance = metrics.calculateMetrics(transformed, rawSuggestion);
      suggestions.add(new Suggestion(rawSuggestion, distance));
    }
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
    //if (StringUtil.isEmpty(prefix)) {
    return Collections.emptyList();
    //}

  }

  public void reset() {
    bundledDictionaries.clear();
    dictionaries.clear();
  }

  public boolean isDictionaryLoad(@NotNull String name) {
    return getBundledDictionaryByName(name) != null;
  }

  public void removeDictionary(@NotNull String name) {
    for (Iterator<Dictionary> iterator = bundledDictionaries.iterator(); iterator.hasNext();) {
      Dictionary dictionary = iterator.next();
      if (name.equals(dictionary.getName())) {
        iterator.remove();
        break;
      }
    }
  }

  @Nullable
  public Dictionary getBundledDictionaryByName(@NotNull String name) {
    if (bundledDictionaries == null) {
      return null;
    }
    for (Dictionary dictionary : bundledDictionaries) {
      if (name.equals(dictionary.getName())) {
        return dictionary;
      }
    }
    return null;
  }
}
