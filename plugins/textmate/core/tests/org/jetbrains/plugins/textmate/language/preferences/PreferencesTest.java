package org.jetbrains.plugins.textmate.language.preferences;

import com.intellij.openapi.util.text.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.TestUtil;
import org.jetbrains.plugins.textmate.bundles.TextMatePreferences;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope;
import org.junit.Test;

import java.util.*;

import static java.util.Collections.emptySet;
import static org.junit.Assert.*;

public class PreferencesTest {
  @Test
  public void retrievePreferencesBySelector_1() {
    final PreferencesRegistry preferencesRegistry = loadPreferences(TestUtil.PREFERENCES_TEST_BUNDLE);
    final List<Preferences> preferences = preferencesRegistry.getPreferences(TestUtil.scopeFromString("text.html.basic"));
    assertEquals(1, preferences.size());
    assertEquals(newHashSet(new TextMateAutoClosingPair("\"", "\"", null)), preferences.get(0).getSmartTypingPairs());
    assertEquals(newHashSet(new TextMateBracePair("`", "`")), preferences.get(0).getHighlightingPairs());
  }

  @Test
  public void retrievePreferencesBySelector_2() {
    final PreferencesRegistry preferencesRegistry = loadPreferences(TestUtil.PREFERENCES_TEST_BUNDLE);
    final List<Preferences> preferences = preferencesRegistry.getPreferences(TestUtil.scopeFromString("source.php string"));
    assertEquals(1, preferences.size());
    assertEquals(newHashSet(new TextMateAutoClosingPair("(", ")", null)), preferences.get(0).getSmartTypingPairs());
    assertEquals(newHashSet(new TextMateBracePair("[", "]")), preferences.get(0).getHighlightingPairs());
  }

  @Test
  public void retrievePreferencesBySelectorCorrespondingToSelectorWeight() {
    PreferencesRegistry preferencesRegistry = loadPreferences(TestUtil.PREFERENCES_TEST_BUNDLE);
    List<Preferences> preferences =
      preferencesRegistry.getPreferences(TestUtil.scopeFromString("text.html source.php string.quoted.double.php"));
    assertEquals(2, preferences.size());
    assertEquals(newHashSet(new TextMateAutoClosingPair("(", ")", null)), preferences.get(0).getSmartTypingPairs());
    assertEquals(newHashSet(new TextMateBracePair("[", "]")), preferences.get(0).getHighlightingPairs());

    assertEquals(newHashSet(new TextMateAutoClosingPair("\"", "\"", null)), preferences.get(1).getSmartTypingPairs());
    assertEquals(newHashSet(new TextMateBracePair("`", "`")), preferences.get(1).getHighlightingPairs());
  }

  @Test
  public void loadingWithTheSameScope() {
    final PreferencesRegistry preferencesRegistry = loadPreferences(TestUtil.PREFERENCES_TEST_BUNDLE);
    final Preferences preferences = mergeAll(preferencesRegistry.getPreferences(TestUtil.scopeFromString("same.scope")));
    assertEquals(newHashSet(new TextMateAutoClosingPair("[", "]", null),
                            new TextMateAutoClosingPair("(", ")", null)), preferences.getSmartTypingPairs());
  }

  @Test
  public void loadHighlightingPairs() {
    PreferencesRegistry preferencesRegistry = loadPreferences(TestUtil.MARKDOWN_TEXTMATE);
    Preferences preferences = mergeAll(preferencesRegistry.getPreferences(TestUtil.scopeFromString("text.html.markdown markup.raw")));
    assertEquals(newHashSet(new TextMateBracePair("[", "]"), new TextMateBracePair("`", "`")), preferences.getHighlightingPairs());
  }

  @Test
  public void loadSmartTypingPairs() {
    PreferencesRegistry preferencesRegistry = loadPreferences(TestUtil.MARKDOWN_TEXTMATE);
    Preferences preferences = mergeAll(preferencesRegistry.getPreferences(TestUtil.scopeFromString("text.html.markdown markup.raw")));
    assertEquals(newHashSet(
      new TextMateAutoClosingPair("{", "}", null),
      new TextMateAutoClosingPair("(", ")", null),
      new TextMateAutoClosingPair("\"", "\"", null)
    ), preferences.getSmartTypingPairs());
  }

  @Test
  public void loadDisabledPairs() {
    PreferencesRegistry preferencesRegistry = loadPreferences(TestUtil.LATEX);
    TextMateScope scope = TestUtil.scopeFromString("text.tex constant.character.escape.tex");
    Preferences preferences = preferencesRegistry.getPreferences(scope).iterator().next();
    @Nullable Set<TextMateAutoClosingPair> smartTypingPairs = preferences.getSmartTypingPairs();
    assertNotNull(smartTypingPairs);
    assertEquals(0, smartTypingPairs.size());
  }

  @Test
  public void loadIndentationRules() {
    PreferencesRegistry preferencesRegistry = loadPreferences(TestUtil.PHP_VSC);
    Preferences preferences = mergeAll(preferencesRegistry.getPreferences(TestUtil.scopeFromString("text.html.php")));
    assertFalse(preferences.getIndentationRules().isEmpty());
    assertNotNull(preferences.getIndentationRules().getIncreaseIndentPattern());
  }

  @Test
  public void loadOnEnterRules() {
    PreferencesRegistry preferencesRegistry = loadPreferences(TestUtil.RESTRUCTURED_TEXT);
    Preferences preferences = mergeAll(preferencesRegistry.getPreferences(TestUtil.scopeFromString("source.rst")));
    assertNotNull(preferences.getOnEnterRules());
    assertFalse(preferences.getOnEnterRules().isEmpty());
  }

  @NotNull
  private static PreferencesRegistry loadPreferences(@NotNull String bundleName) {
    Iterator<TextMatePreferences> preferences = TestUtil.readBundle(bundleName).readPreferences().iterator();
    assertNotNull(preferences);
    PreferencesRegistryImpl preferencesRegistry = new PreferencesRegistryImpl();
    while (preferences.hasNext()) {
      TextMatePreferences next = preferences.next();
      preferencesRegistry.addPreferences(new Preferences(next.getScopeName(),
                                                         next.getHighlightingPairs(),
                                                         next.getSmartTypingPairs(),
                                                         emptySet(),
                                                         null,
                                                         next.getIndentationRules(),
                                                         next.getOnEnterRules()));
    }
    return preferencesRegistry;
  }

  @NotNull
  private static Preferences mergeAll(@NotNull List<Preferences> preferences) {
    Set<TextMateBracePair> highlightingPairs = new HashSet<>();
    Set<TextMateAutoClosingPair> smartTypingPairs = new HashSet<>();
    Set<TextMateBracePair> surroundingPairs = new HashSet<>();
    Set<Character> autoCloseBefore = new HashSet<>();
    IndentationRules indentationRules = IndentationRules.empty();
    Set<OnEnterRule> onEnterRules = new HashSet<>();

    for (Preferences preference : preferences) {
      final Set<TextMateBracePair> localHighlightingPairs = preference.getHighlightingPairs();
      final @Nullable Set<TextMateAutoClosingPair> localSmartTypingPairs = preference.getSmartTypingPairs();
      final Set<TextMateBracePair> localSurroundingPairs = preference.getSurroundingPairs();
      final String localAutoCloseBefore = preference.getAutoCloseBefore();
      indentationRules = indentationRules.updateWith(preference.getIndentationRules());
      if (localHighlightingPairs != null) {
        highlightingPairs.addAll(localHighlightingPairs);
      }
      if (localSmartTypingPairs != null) {
        smartTypingPairs.addAll(localSmartTypingPairs);
      }
      if (localSurroundingPairs != null) {
        surroundingPairs.addAll(localSurroundingPairs);
      }
      if (localAutoCloseBefore != null) {
        for (char c : localAutoCloseBefore.toCharArray()) {
          autoCloseBefore.add(c);
        }
      }
      if (preference.getOnEnterRules() != null){
        onEnterRules.addAll(preference.getOnEnterRules());
      }
    }
    return new Preferences("",
                           highlightingPairs,
                           smartTypingPairs,
                           surroundingPairs,
                           Strings.nullize(Strings.join(autoCloseBefore, "")),
                           indentationRules,
                           onEnterRules);
  }

  private static <T> Set<T> newHashSet(T... pairs) {
    //noinspection SSBasedInspection
    return new HashSet<>(Arrays.asList(pairs));
  }
}