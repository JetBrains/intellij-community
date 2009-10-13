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
import com.intellij.spellchecker.dictionary.Dictionary;
import com.intellij.spellchecker.dictionary.Loader;
import com.intellij.spellchecker.engine.SpellCheckerEngine;
import com.intellij.spellchecker.engine.SpellCheckerFactory;
import com.intellij.spellchecker.state.StateLoader;
import com.intellij.spellchecker.util.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class SpellCheckerManager {

  private static final int MAX_SUGGESTIONS_THRESHOLD = 5;
  private static final int MAX_METRICS = 1;

  private Project project;

  private SpellCheckerEngine spellChecker;

  private Dictionary userDictionary;

  public static SpellCheckerManager getInstance(Project project) {
    return ServiceManager.getService(project, SpellCheckerManager.class);
  }

  public SpellCheckerManager(Project project) {
    this.project = project;
    reloadConfiguration();
  }

  public Dictionary getUserDictionary() {
    return userDictionary;
  }

  public void reloadConfiguration() {
    spellChecker = SpellCheckerFactory.create();
    fillEngineDictionary();
  }

  private void fillEngineDictionary() {
    spellChecker.reset();
    final StateLoader stateLoader = new StateLoader(project);
    Loader[] loaders = new Loader[]{new FileLoader("english.dic"), new FileLoader("jetbrains.dic"), stateLoader};
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
      spellChecker.addToDictionary(transformed);
    }
  }

  public void updateUserWords(@Nullable Collection<String> words) {
    Set<String> transformed = spellChecker.getTransformation().transform(words);
    userDictionary.replaceAll(transformed);
    fillEngineDictionary();
    restartInspections();
  }


  @NotNull
  public static HighlightDisplayLevel getHighlightDisplayLevel() {
    return HighlightDisplayLevel.find(SpellCheckerSeveritiesProvider.TYPO);
  }


  @NotNull
  public List<String> getSuggestions(@NotNull String word) {
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
