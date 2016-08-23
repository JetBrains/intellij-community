/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Pair;
import com.intellij.util.text.EditDistance;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.spellchecker.compress.CompressedDictionary;
import com.intellij.spellchecker.dictionary.Dictionary;
import com.intellij.spellchecker.dictionary.EditableDictionary;
import com.intellij.spellchecker.dictionary.EditableDictionaryLoader;
import com.intellij.spellchecker.dictionary.Loader;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class BaseSpellChecker implements SpellCheckerEngine {
  static final Logger LOG = Logger.getInstance("#com.intellij.spellchecker.engine.BaseSpellChecker");

  private final Transformation transform = new Transformation();
  private final Set<EditableDictionary> dictionaries = new HashSet<>();
  private final List<Dictionary> bundledDictionaries = ContainerUtil.createLockFreeCopyOnWriteList();

  private final AtomicBoolean myLoadingDictionaries = new AtomicBoolean(false);
  private final List<Pair<Loader, Consumer<Dictionary>>> myDictionariesToLoad = ContainerUtil.createLockFreeCopyOnWriteList();
  private final Project myProject;

  public BaseSpellChecker(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void loadDictionary(@NotNull Loader loader) {
    if (loader instanceof EditableDictionaryLoader) {
      final EditableDictionary dictionary = ((EditableDictionaryLoader)loader).getDictionary();
      if (dictionary != null) {
        addModifiableDictionary(dictionary);
      }
    }
    else {
      loadCompressedDictionary(loader);
    }
  }

  private void loadCompressedDictionary(@NotNull Loader loader) {
    if (ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isHeadlessEnvironment()) {
      final CompressedDictionary dictionary = CompressedDictionary.create(loader, transform);
      addCompressedFixedDictionary(dictionary);
    }
    else {
      loadDictionaryAsync(loader, this::addCompressedFixedDictionary);
    }
  }

  private void loadDictionaryAsync(@NotNull final Loader loader, @NotNull final Consumer<Dictionary> consumer) {
    if (myLoadingDictionaries.compareAndSet(false, true)) {
      LOG.debug("Loading " + loader.getName());
      doLoadDictionaryAsync(loader, consumer);
    }
    else {
      queueDictionaryLoad(loader, consumer);
    }
  }

  private void doLoadDictionaryAsync(Loader loader, Consumer<Dictionary> consumer) {
    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(() -> {
      LOG.debug("Loading " + loader.getName());
      Application app = ApplicationManager.getApplication();
      app.executeOnPooledThread(() -> {
        if (app.isDisposed()) return;

        CompressedDictionary dictionary = CompressedDictionary.create(loader, transform);
        LOG.debug(loader.getName() + " loaded!");
        consumer.consume(dictionary);

        while (!myDictionariesToLoad.isEmpty()) {
          if (app.isDisposed()) return;

          Pair<Loader, Consumer<Dictionary>> nextDictionary = myDictionariesToLoad.remove(0);
          Loader nextDictionaryLoader = nextDictionary.getFirst();
          dictionary = CompressedDictionary.create(nextDictionaryLoader, transform);
          LOG.debug(nextDictionaryLoader.getName() + " loaded!");
          nextDictionary.getSecond().consume(dictionary);
        }

        LOG.debug("Loading finished, restarting daemon...");
        myLoadingDictionaries.set(false);
        UIUtil.invokeLaterIfNeeded(() -> {
          if (app.isDisposed()) return;

          for (final Project project : ProjectManager.getInstance().getOpenProjects()) {
            if (project.isInitialized() && project.isOpen() && !project.isDefault()) {
              DaemonCodeAnalyzer instance = DaemonCodeAnalyzer.getInstance(project);
              if (instance != null) instance.restart();
            }
          }
        });
      });
    });
  }

  private void queueDictionaryLoad(final Loader loader, final Consumer<Dictionary> consumer) {
    LOG.debug("Queuing load for: " + loader.getName());
    myDictionariesToLoad.add(Pair.create(loader, consumer));
  }

  private void addModifiableDictionary(@NotNull EditableDictionary dictionary) {
    dictionaries.add(dictionary);
  }

  private void addCompressedFixedDictionary(@NotNull Dictionary dictionary) {
    bundledDictionaries.add(dictionary);
  }

  @Override
  public Transformation getTransformation() {
    return transform;
  }

  private static void restore(char startFrom, int i, int j, Collection<? extends Dictionary> dictionaries, Collection<String> result) {
    for (Dictionary o : dictionaries) {
      restore(startFrom, i, j, o, result);
    }
  }

  private static void restore(final char first, final int i, final int j, Dictionary dictionary, final Collection<String> result) {
    if (dictionary instanceof CompressedDictionary) {
      ((CompressedDictionary)dictionary).getWords(first, i, j, result);
    }
    else {
      dictionary.traverse(s -> {
        if (!StringUtil.isEmpty(s) && s.charAt(0) == first && s.length() >= i && s.length() <= j) {
          result.add(s);
        }
      });
    }
  }

  /**
   * @param transformed
   * @param dictionaries
   * @return -1 (all)failed / 0 (any) ok / >0 all alien
   */
  private static int isCorrect(@NotNull String transformed, @Nullable Collection<? extends Dictionary> dictionaries) {
    if (dictionaries == null) {
      return -1;
    }

    int errors = 0;
    for (Dictionary dictionary : dictionaries) {
      if (dictionary == null) continue;
      Boolean contains = dictionary.contains(transformed);
      if (contains==null) ++errors;
      else if (contains) return 0;
    }
    if (errors == dictionaries.size()) return errors;
    return -1;
  }

  @Override
  public boolean isCorrect(@NotNull String word) {
    final String transformed = transform.transform(word);
    if (myLoadingDictionaries.get() || transformed == null) {
      return true;
    }
    int bundled = isCorrect(transformed, bundledDictionaries);
    int user = isCorrect(transformed, dictionaries);
    return bundled == 0 || user == 0 || bundled > 0 && user > 0;
  }

  @Override
  @NotNull
  public List<String> getSuggestions(@NotNull String word, int maxSuggestions, int quality) {
    String transformed = transform.transform(word);
    if (transformed == null) return Collections.emptyList();

    List<String> rawSuggestions = new ArrayList<>();
    restore(transformed.charAt(0), 0, Integer.MAX_VALUE, bundledDictionaries, rawSuggestions);
    restore(word.charAt(0), 0, Integer.MAX_VALUE, dictionaries, rawSuggestions);
    if (rawSuggestions.isEmpty()) return Collections.emptyList();

    List<Suggestion> suggestions = new ArrayList<>(rawSuggestions.size());
    for (String rawSuggestion : rawSuggestions) {
      int distance = EditDistance.optimalAlignment(transformed, rawSuggestion, true);
      suggestions.add(new Suggestion(rawSuggestion, distance));
    }

    Collections.sort(suggestions);
    int limit = Math.min(maxSuggestions, suggestions.size());
    List<String> result = new ArrayList<>(limit);
    int bestMetrics = suggestions.get(0).getMetrics();
    for (int i = 0; i < limit; i++) {
      Suggestion suggestion = suggestions.get(i);
      if (bestMetrics - suggestion.getMetrics() > quality) {
        break;
      }
      result.add(i, suggestion.getWord());
    }
    return result;
  }

  @Override
  @NotNull
  public List<String> getVariants(@NotNull String prefix) {
    return Collections.emptyList();
  }

  @Override
  public void reset() {
    bundledDictionaries.clear();
    dictionaries.clear();
  }

  @Override
  public boolean isDictionaryLoad(@NotNull String name) {
    return getBundledDictionaryByName(name) != null;
  }

  @Override
  public void removeDictionary(@NotNull String name) {
    final Dictionary dictionaryByName = getBundledDictionaryByName(name);
    if (dictionaryByName != null) {
      bundledDictionaries.remove(dictionaryByName);
    }
  }

  @Nullable
  public Dictionary getBundledDictionaryByName(@NotNull String name) {
    for (Dictionary dictionary : bundledDictionaries) {
      if (name.equals(dictionary.getName())) {
        return dictionary;
      }
    }
    return null;
  }
}