/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.spellchecker;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.spellchecker.dictionary.EditableDictionary;
import com.intellij.spellchecker.dictionary.Loader;
import com.intellij.spellchecker.engine.SpellCheckerEngine;
import com.intellij.spellchecker.engine.SpellCheckerFactory;
import com.intellij.spellchecker.engine.SuggestionProvider;
import com.intellij.spellchecker.settings.SpellCheckerSettings;
import com.intellij.spellchecker.state.StateLoader;
import com.intellij.spellchecker.util.SPFileUtil;
import com.intellij.spellchecker.util.Strings;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.*;

public class SpellCheckerManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.spellchecker.SpellCheckerManager");

  private static final int MAX_SUGGESTIONS_THRESHOLD = 5;
  private static final int MAX_METRICS = 1;

  private final Project project;
  private SpellCheckerEngine spellChecker;
  private EditableDictionary userDictionary;
  private final SuggestionProvider suggestionProvider = new BaseSuggestionProvider(this);
  private final SpellCheckerSettings settings;

  public static SpellCheckerManager getInstance(Project project) {
    return ServiceManager.getService(project, SpellCheckerManager.class);
  }

  public SpellCheckerManager(Project project, SpellCheckerSettings settings) {
    this.project = project;
    this.settings = settings;
    fullConfigurationReload();
  }

  public void fullConfigurationReload() {
    spellChecker = SpellCheckerFactory.create(project);
    fillEngineDictionary();
  }

  public void updateBundledDictionaries(final List<String> removedDictionaries) {
    for (BundledDictionaryProvider provider : Extensions.getExtensions(BundledDictionaryProvider.EP_NAME)) {
      for (String dictionary : provider.getBundledDictionaries()) {
        boolean dictionaryShouldBeLoad = settings == null || !settings.getBundledDisabledDictionariesPaths().contains(dictionary);
        boolean dictionaryIsLoad = spellChecker.isDictionaryLoad(dictionary);
        if (dictionaryIsLoad && !dictionaryShouldBeLoad) {
          spellChecker.removeDictionary(dictionary);
        }
        else if (!dictionaryIsLoad && dictionaryShouldBeLoad) {
          final Class<? extends BundledDictionaryProvider> loaderClass = provider.getClass();
          final InputStream stream = loaderClass.getResourceAsStream(dictionary);
          if (stream != null) {
            spellChecker.loadDictionary(new StreamLoader(stream, dictionary));
          }
          else {
            LOG.warn("Couldn't load dictionary '" + dictionary + "' with loader '" + loaderClass + "'");
          }
        }
      }
    }
    if (settings != null && settings.getDictionaryFoldersPaths() != null) {
      final Set<String> disabledDictionaries = settings.getDisabledDictionariesPaths();
      for (String folder : settings.getDictionaryFoldersPaths()) {
        SPFileUtil.processFilesRecursively(folder, s -> {
          boolean dictionaryShouldBeLoad =!disabledDictionaries.contains(s);
          boolean dictionaryIsLoad = spellChecker.isDictionaryLoad(s);
          if (dictionaryIsLoad && !dictionaryShouldBeLoad) {
            spellChecker.removeDictionary(s);
          }
          else if (!dictionaryIsLoad && dictionaryShouldBeLoad) {
            spellChecker.loadDictionary(new FileLoader(s, s));
          }

        });

      }
    }

    if (removedDictionaries != null && !removedDictionaries.isEmpty()) {
      for (final String name : removedDictionaries) {
        spellChecker.removeDictionary(name);
      }
    }

    restartInspections();
  }

  public Project getProject() {
    return project;
  }

  public EditableDictionary getUserDictionary() {
    return userDictionary;
  }

  private void fillEngineDictionary() {
    spellChecker.reset();
    final StateLoader stateLoader = new StateLoader(project);
    stateLoader.load(s -> {
      //do nothing - in this loader we don't worry about word list itself - the whole dictionary will be restored
    });
    final List<Loader> loaders = new ArrayList<>();
    // Load bundled dictionaries from corresponding jars
    for (BundledDictionaryProvider provider : Extensions.getExtensions(BundledDictionaryProvider.EP_NAME)) {
      for (String dictionary : provider.getBundledDictionaries()) {
        if (settings == null || !settings.getBundledDisabledDictionariesPaths().contains(dictionary)) {
          final Class<? extends BundledDictionaryProvider> loaderClass = provider.getClass();
          final InputStream stream = loaderClass.getResourceAsStream(dictionary);
          if (stream != null) {
            loaders.add(new StreamLoader(stream, dictionary));
          }
          else {
            LOG.warn("Couldn't load dictionary '" + dictionary + "' with loader '" + loaderClass + "'");
          }
        }
      }
    }
    if (settings != null && settings.getDictionaryFoldersPaths() != null) {
      final Set<String> disabledDictionaries = settings.getDisabledDictionariesPaths();
      for (String folder : settings.getDictionaryFoldersPaths()) {
        SPFileUtil.processFilesRecursively(folder, s -> {
          if (!disabledDictionaries.contains(s)) {
            loaders.add(new FileLoader(s, s));
          }
        });

      }
    }
    loaders.add(stateLoader);
    for (Loader loader : loaders) {
      spellChecker.loadDictionary(loader);
    }
    userDictionary = stateLoader.getDictionary();
  }

  public boolean hasProblem(@NotNull String word) {
    return !spellChecker.isCorrect(word);
  }

  public void acceptWordAsCorrect(@NotNull String word, Project project) {
    final String transformed = spellChecker.getTransformation().transform(word);
    if (transformed != null) {
      userDictionary.addToDictionary(transformed);
      final PsiModificationTrackerImpl modificationTracker =
        (PsiModificationTrackerImpl)PsiManager.getInstance(project).getModificationTracker();
      modificationTracker.incCounter();
    }
  }

  public void updateUserDictionary(@Nullable Collection<String> words) {
    userDictionary.replaceAll(words);
    restartInspections();
  }

  @NotNull
  public static List<String> getBundledDictionaries() {
    final ArrayList<String> dictionaries = new ArrayList<>();
    for (BundledDictionaryProvider provider : Extensions.getExtensions(BundledDictionaryProvider.EP_NAME)) {
      ContainerUtil.addAll(dictionaries, provider.getBundledDictionaries());
    }
    return dictionaries;
  }

  @NotNull
  public static HighlightDisplayLevel getHighlightDisplayLevel() {
    return HighlightDisplayLevel.find(SpellCheckerSeveritiesProvider.TYPO);
  }

  @NotNull
  public List<String> getSuggestions(@NotNull String text) {
    return suggestionProvider.getSuggestions(text);
  }

  @NotNull
  protected List<String> getRawSuggestions(@NotNull String word) {
    if (!spellChecker.isCorrect(word)) {
      List<String> suggestions = spellChecker.getSuggestions(word, MAX_SUGGESTIONS_THRESHOLD, MAX_METRICS);
      if (!suggestions.isEmpty()) {
        if (Strings.isCapitalized(word)) {
          Strings.capitalize(suggestions);
        }
        else if (Strings.isUpperCase(word)) {
          Strings.upperCase(suggestions);
        }
        Set<String> unique = new LinkedHashSet<>(suggestions);
        return unique.size() < suggestions.size() ? new ArrayList<>(unique) : suggestions;
      }
    }
    return Collections.emptyList();
  }

  public static void restartInspections() {
    ApplicationManager.getApplication().invokeLater(() -> {
      Project[] projects = ProjectManager.getInstance().getOpenProjects();
      for (Project project1 : projects) {
        if (project1.isInitialized() && project1.isOpen() && !project1.isDefault()) {
          DaemonCodeAnalyzer.getInstance(project1).restart();
        }
      }
    });
  }
}
