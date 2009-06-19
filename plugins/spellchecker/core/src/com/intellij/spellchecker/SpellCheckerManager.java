package com.intellij.spellchecker;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.spellchecker.engine.SpellChecker;
import com.intellij.spellchecker.engine.SpellCheckerFactory;
import com.intellij.spellchecker.options.SpellCheckerConfiguration;
import com.intellij.spellchecker.util.Strings;
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

  @NonNls
  private static final String DICT_URL = "english.dic";

  public static SpellCheckerManager getInstance() {
    return ServiceManager.getService(SpellCheckerManager.class);
  }

  private final SpellCheckerConfiguration configuration;
  private final SpellChecker spellChecker = SpellCheckerFactory.create();

  public SpellCheckerManager(SpellCheckerConfiguration configuration) {
    this.configuration = configuration;
    reloadConfiguration();
  }

  @NotNull
  public static HighlightDisplayLevel getHighlightDisplayLevel() {
    return HighlightDisplayLevel.find(SpellCheckerSeveritiesProvider.MISSPELLED);
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
    return spellChecker.getSuggestionsExt(word,MAX_SUGGESTIONS_THRESHOLD);
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

  public void addToDictionary(@NotNull String word) {
    String lowerCased = word.toLowerCase();
    configuration.USER_DICTIONARY_WORDS.add(lowerCased);
    spellChecker.addToDictionary(lowerCased);
  }

  public void ignoreAll(@NotNull String word) {
    String lowerCased = word.toLowerCase();
    configuration.IGNORED_WORDS.add(lowerCased);
    spellChecker.ignoreAll(lowerCased);
  }

  public final Set<String> getIgnoredWords() {
    return configuration.IGNORED_WORDS;
  }


  private void reloadConfiguration() {
    initDictionaries();
    spellChecker.reset();
    for (String word : ejectAll(configuration.IGNORED_WORDS)) {
      ignoreAll(word);
    }
    for (String word : ejectAll(configuration.USER_DICTIONARY_WORDS)) {
      addToDictionary(word);
    }
  }

  private void initDictionaries() {
    InputStream is = SpellCheckerManager.class.getResourceAsStream(DICT_URL);
    if (is != null) {
      try {
        addDictionary(is);
      }
      catch (IOException e) {
        LOG.error("Dictionary could not be load", e);
      }
    }
  }

  public void reloadAndRestartInspections() {
    reloadConfiguration();
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
    from.clear();
    return words;
  }
}
