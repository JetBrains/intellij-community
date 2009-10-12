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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.spellchecker.engine.SpellChecker;
import com.intellij.spellchecker.engine.SpellCheckerFactory;
import com.intellij.spellchecker.options.SpellCheckerConfiguration;
import com.intellij.spellchecker.options.ProjectDictionaryState;
import com.intellij.spellchecker.options.CachedDictionaryState;
import com.intellij.spellchecker.util.Strings;
import com.intellij.spellchecker.dictionary.Dictionary;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.*;

/**
 * Spell checker inspection provider.
 */
public final class SpellCheckerManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.spellchecker.SpellCheckerManager");
  private static final int MAX_SUGGESTIONS_THRESHOLD = 10;
  private Project project;
  private static HighlightDisplayLevel level;
  private Set<String> dictionaries = new HashSet<String>();

  private Dictionary projectWordList;
  private Dictionary cachedWordList;

  public Set<String> getDictionaries() {
    return dictionaries;
  }

  @NonNls
  private static final String[] DICT_URLS = new String[]{"english.dic", "jetbrains.dic"};


  public static SpellCheckerManager getInstance(Project project) {
    return ServiceManager.getService(project, SpellCheckerManager.class);
  }

  private final SpellCheckerConfiguration configuration;
  private final SpellChecker spellChecker = SpellCheckerFactory.create();


  public SpellCheckerManager(SpellCheckerConfiguration configuration, final Project project) {
    this.configuration = configuration;
    this.project = project;
    reloadConfiguration();
  }

  @NotNull
  public static HighlightDisplayLevel getHighlightDisplayLevel() {
    return HighlightDisplayLevel.find(SpellCheckerSeveritiesProvider.TYPO);
  }


  @NotNull
  public SpellChecker getSpellChecker() {
    return spellChecker;
  }


  public boolean hasProblem(@NotNull String word) {
    return !isIgnored(word) && !spellChecker.isCorrect(word);
  }

  private boolean isIgnored(@NotNull String word) {
    return spellChecker.isIgnored(word.toLowerCase());
  }

  public List<String> getVariants(@NotNull String prefix) {
    return spellChecker.getVariants(prefix);
  }

  @NotNull
  public List<String> getSuggestions(@NotNull String word) {
    if (!isIgnored(word) && !spellChecker.isCorrect(word)) {
      List<String> suggestions = spellChecker.getSuggestions(word, MAX_SUGGESTIONS_THRESHOLD);
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
  public List<String> getSuggestionsExt(@NotNull String word) {
    return spellChecker.getSuggestionsExt(word, MAX_SUGGESTIONS_THRESHOLD);
  }


  /**
   * Load dictionary from stream.
   *
   * @param inputStream Dictionary input stream
   * @throws java.io.IOException if dictionary load with problems
   */
  public void addDictionary(@NotNull InputStream inputStream) throws IOException {
    addDictionary(inputStream, Charset.defaultCharset().name());
  }

  /**
   * Load dictionary from stream.
   *
   * @param inputStream Dictionary input stream
   * @param encoding    Encoding
   * @throws java.io.IOException if dictionary load with problems
   */
  public void addDictionary(@NotNull InputStream inputStream, @NonNls String encoding) throws IOException {
    addDictionary(inputStream, encoding, Locale.getDefault());
  }

  /**
   * Load dictionary from stream.
   *
   * @param inputStream Dictionary input stream
   * @param encoding    Encoding
   * @param locale      Locale of dictionary
   * @throws java.io.IOException if dictionary load with problems
   */
  public void addDictionary(@NotNull InputStream inputStream, @NonNls String encoding, @NonNls @NotNull Locale locale) throws IOException {
    spellChecker.addDictionary(inputStream, encoding, locale);
  }

  public void acceptWordAsCorrect(@NotNull String word) {
    String lowerCased = word.toLowerCase();
    projectWordList.acceptWord(word);
    cachedWordList.acceptWord(word);
    spellChecker.addToDictionary(lowerCased);
  }

   public void reloadConfiguration() {
    initDictionaries();
    spellChecker.reset();

    projectWordList = ServiceManager.getService(project, ProjectDictionaryState.class).getDictionary();
    cachedWordList = ServiceManager.getService(project, CachedDictionaryState.class).getDictionary();

    for (String word : projectWordList.getWords()) {
      cachedWordList.acceptWord(word);
    }


    for (String word : ejectAll(cachedWordList.getWords())) {
      String lowerCased = word.toLowerCase();
      spellChecker.addToDictionary(lowerCased);
    }

  }

  public void applyConfiguration() {
    initDictionaries();
    spellChecker.reset();

    assert projectWordList != null;
    assert cachedWordList != null;

    for (String word : projectWordList.getWords()) {
      cachedWordList.acceptWord(word);
    }


    for (String word : ejectAll(cachedWordList.getWords())) {
      String lowerCased = word.toLowerCase();
      spellChecker.addToDictionary(lowerCased);
    }

  }

  private void initDictionaries() {
    for (String dictUrl : DICT_URLS) {
      initDictionary(dictUrl);
    }
  }

  private void initDictionary(String url) {
    InputStream is = SpellCheckerManager.class.getResourceAsStream(url);
    if (is != null) {
      try {
        dictionaries.add(url);
        addDictionary(is);
      }
      catch (IOException e) {
        LOG.error("Dictionary could not be loaded", e);
      }
    }
  }

  public void restartInspections() {
    //reloadConfiguration();
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

  private HashSet<String> ejectAll(Set<String> from) {
    HashSet<String> words = new HashSet<String>(from);
  //  from.clear();
    return words;
  }

  public Dictionary getProjectWordList() {
    return projectWordList;
  }

  public Dictionary getCachedWordList() {
    return cachedWordList;
  }

  
}
