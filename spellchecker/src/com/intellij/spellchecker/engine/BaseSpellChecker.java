/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.spellchecker.engine;

import com.google.common.collect.*;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Pair;
import com.intellij.spellchecker.compress.CompressedDictionary;
import com.intellij.spellchecker.dictionary.Dictionary;
import com.intellij.spellchecker.dictionary.EditableDictionary;
import com.intellij.spellchecker.dictionary.Loader;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.openapi.util.io.FileUtil.isAncestor;
import static com.intellij.util.containers.ContainerUtil.concat;
import static com.intellij.util.text.EditDistance.optimalAlignment;
import static java.util.stream.Collectors.toList;

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
    if (ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isHeadlessEnvironment()) {
      addDictionary(CompressedDictionary.create(loader, transform));
    }
    else {
      loadDictionaryAsync(loader, this::addDictionary);
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

  @Override
  public void addModifiableDictionary(@NotNull EditableDictionary dictionary) {
    dictionaries.add(dictionary);
  }

  @Override
  public void addDictionary(@NotNull Dictionary dictionary) {
    bundledDictionaries.add(dictionary);
  }

  @Override
  public Transformation getTransformation() {
    return transform;
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
    final MinMaxPriorityQueue<Suggestion> suggestions = MinMaxPriorityQueue.orderedBy(Suggestion::compareTo).maximumSize(maxSuggestions).create();
    for (Dictionary dict : concat(bundledDictionaries, dictionaries)) {
      dict.getSuggestions(transformed, s -> suggestions.add(new Suggestion(s, optimalAlignment(transformed, s, true))));
    }
    if (suggestions.isEmpty()) {
      return Collections.emptyList();
    }
    int bestMetrics = suggestions.peek().getMetrics();
    return suggestions.stream()
      .filter(i -> bestMetrics - i.getMetrics() < quality)
      .sorted()
      .map(Suggestion::getWord)
      .collect(toList());
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

  @Override
  public void removeDictionariesRecursively(@NotNull String directory) {
    bundledDictionaries.stream()
      .map(Dictionary::getName)
      .filter(dict -> isAncestor(directory, dict, false) && isDictionaryLoad(dict))
      .forEach(this::removeDictionary);
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