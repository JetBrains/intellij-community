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
package com.intellij.spellchecker;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
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


  @NotNull
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
    spellChecker = SpellCheckerFactory.create();
    fillEngineDictionary();
  }


  public void updateBundledDictionaries() {
    for (BundledDictionaryProvider provider : Extensions.getExtensions(BundledDictionaryProvider.EP_NAME)) {
      for (String dictionary : provider.getBundledDictionaries()) {
        boolean dictionaryShouldBeLoad = this.settings == null || !this.settings.getBundledDisabledDictionariesPaths().contains(dictionary);
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
    if (this.settings != null && this.settings.getDictionaryFoldersPaths() != null) {
      final Set<String> disabledDictionaries = settings.getDisabledDictionariesPaths();
      for (String folder : this.settings.getDictionaryFoldersPaths()) {
        SPFileUtil.processFilesRecursively(folder, new Consumer<String>() {
          public void consume(final String s) {
            boolean dictionaryShouldBeLoad =!disabledDictionaries.contains(s);
            boolean dictionaryIsLoad = spellChecker.isDictionaryLoad(s);
            if (dictionaryIsLoad && !dictionaryShouldBeLoad) {
              spellChecker.removeDictionary(s);
            }
            else if (!dictionaryIsLoad && dictionaryShouldBeLoad) {
              spellChecker.loadDictionary(new FileLoader(s, s));
            }

          }
        });

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
    stateLoader.load(new Consumer<String>() {
      public void consume(String s) {
        //do nothing - in this loader we don't worry about word list itself - the whole dictionary will be restored
      }
    });
    final List<Loader> loaders = new ArrayList<Loader>();
    // Load bundled dictionaries from corresponding jars
    for (BundledDictionaryProvider provider : Extensions.getExtensions(BundledDictionaryProvider.EP_NAME)) {
      for (String dictionary : provider.getBundledDictionaries()) {
        if (this.settings == null || !this.settings.getBundledDisabledDictionariesPaths().contains(dictionary)) {
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
    if (this.settings != null && this.settings.getDictionaryFoldersPaths() != null) {
      final Set<String> disabledDictionaries = settings.getDisabledDictionariesPaths();
      for (String folder : this.settings.getDictionaryFoldersPaths()) {
        SPFileUtil.processFilesRecursively(folder, new Consumer<String>() {
          public void consume(final String s) {
            if (!disabledDictionaries.contains(s)) {
              loaders.add(new FileLoader(s, s));
            }
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

  public void acceptWordAsCorrect(@NotNull String word) {
    final String transformed = spellChecker.getTransformation().transform(word);
    if (transformed != null) {
      userDictionary.addToDictionary(transformed);
    }
  }

  public void updateUserDictionary(@Nullable Collection<String> words) {
    userDictionary.replaceAll(words);
    restartInspections();
  }



  @NotNull
  public List<String> getBundledDictionaries() {
    final ArrayList<String> dictionaries = new ArrayList<String>();
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
      if (suggestions.size() != 0) {
        boolean capitalized = Strings.isCapitalized(word);
        boolean upperCases = Strings.isUpperCase(word);
        if (capitalized) {
          Strings.capitalize(suggestions);
        }
        else if (upperCases) {
          Strings.upperCase(suggestions);
        }
      }
      List<String> result = new ArrayList<String>();
      for (String s : suggestions) {
        if (!result.contains(s)) {
          result.add(s);
        }
      }
      return result;
    }
    return Collections.emptyList();
  }

  @NotNull
  public List<String> getVariants(@NotNull String prefix) {

    return Collections.emptyList();
  }


  public void restartInspections() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        for (Project project : projects) {
          if (project.isInitialized() && project.isOpen() && !project.isDefault()) {
            DaemonCodeAnalyzer.getInstance(project).restart();
          }
        }
      }
    });
  }


}
