package org.jetbrains.plugins.textmate.language.preferences;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.TestUtil;
import org.jetbrains.plugins.textmate.bundles.TextMatePreferences;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class PreferencesTest {
  @Test
  public void retrievePreferencesBySelector_1() throws Exception {
    final PreferencesRegistry preferencesRegistry = loadPreferences(TestUtil.PREFERENCES_TEST_BUNDLE);
    final List<Preferences> preferences = preferencesRegistry.getPreferences(TestUtil.scopeFromString("text.html.basic"));
    assertEquals(1, preferences.size());
    assertEquals(newHashSet(new TextMateBracePair('"', '"')), preferences.get(0).getSmartTypingPairs());
    assertEquals(newHashSet(new TextMateBracePair('`', '`')), preferences.get(0).getHighlightingPairs());
  }

  @Test
  public void retrievePreferencesBySelector_2() throws Exception {
    final PreferencesRegistry preferencesRegistry = loadPreferences(TestUtil.PREFERENCES_TEST_BUNDLE);
    final List<Preferences> preferences = preferencesRegistry.getPreferences(TestUtil.scopeFromString("source.php string"));
    assertEquals(1, preferences.size());
    assertEquals(newHashSet(new TextMateBracePair('(', ')')), preferences.get(0).getSmartTypingPairs());
    assertEquals(newHashSet(new TextMateBracePair('[', ']')), preferences.get(0).getHighlightingPairs());
  }

  @Test
  public void retrievePreferencesBySelectorCorrespondingToSelectorWeight() throws Exception {
    PreferencesRegistry preferencesRegistry = loadPreferences(TestUtil.PREFERENCES_TEST_BUNDLE);
    List<Preferences> preferences =
      preferencesRegistry.getPreferences(TestUtil.scopeFromString("text.html source.php string.quoted.double.php"));
    assertEquals(2, preferences.size());
    assertEquals(newHashSet(new TextMateBracePair('(', ')')), preferences.get(0).getSmartTypingPairs());
    assertEquals(newHashSet(new TextMateBracePair('[', ']')), preferences.get(0).getHighlightingPairs());

    assertEquals(newHashSet(new TextMateBracePair('"', '"')), preferences.get(1).getSmartTypingPairs());
    assertEquals(newHashSet(new TextMateBracePair('`', '`')), preferences.get(1).getHighlightingPairs());
  }

  @Test
  public void loadingWithTheSameScope() throws Exception {
    final PreferencesRegistry preferencesRegistry = loadPreferences(TestUtil.PREFERENCES_TEST_BUNDLE);
    final Preferences preferences = mergeAll(preferencesRegistry.getPreferences(TestUtil.scopeFromString("same.scope")));
    assertEquals(newHashSet(new TextMateBracePair('[', ']'), new TextMateBracePair('(', ')')), preferences.getSmartTypingPairs());
  }

  @Test
  public void loadHighlightingPairs() throws Exception {
    PreferencesRegistry preferencesRegistry = loadPreferences(TestUtil.MARKDOWN_TEXTMATE);
    Preferences preferences = mergeAll(preferencesRegistry.getPreferences(TestUtil.scopeFromString("text.html.markdown markup.raw")));
    assertEquals(newHashSet(new TextMateBracePair('[', ']'), new TextMateBracePair('`', '`')), preferences.getHighlightingPairs());
  }

  @Test
  public void loadSmartTypingPairs() throws Exception {
    PreferencesRegistry preferencesRegistry = loadPreferences(TestUtil.MARKDOWN_TEXTMATE);
    Preferences preferences = mergeAll(preferencesRegistry.getPreferences(TestUtil.scopeFromString("text.html.markdown markup.raw")));
    assertEquals(newHashSet(
      new TextMateBracePair('{', '}'),
      new TextMateBracePair('(', ')'),
      new TextMateBracePair('"', '"')
    ), preferences.getSmartTypingPairs());
  }

  @Test
  public void loadDisabledPairs() throws Exception {
    PreferencesRegistry preferencesRegistry = loadPreferences(TestUtil.LATEX);
    TextMateScope scope = TestUtil.scopeFromString("text.tex constant.character.escape.tex");
    Preferences preferences = preferencesRegistry.getPreferences(scope).iterator().next();
    Set<TextMateBracePair> smartTypingPairs = preferences.getSmartTypingPairs();
    assertNotNull(smartTypingPairs);
    assertEquals(0, smartTypingPairs.size());
  }

  @Test
  public void loadIndentationRules() throws Exception {
    PreferencesRegistry preferencesRegistry = loadPreferences(TestUtil.PHP_VSC);
    Preferences preferences = mergeAll(preferencesRegistry.getPreferences(TestUtil.scopeFromString("text.html.php")));
    assertFalse(preferences.getIndentationRules().isEmpty());
    assertNotNull(preferences.getIndentationRules().getIncreaseIndentPattern());
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
                                                         next.getIndentationRules()));
    }
    return preferencesRegistry;
  }

  @NotNull
  private static Preferences mergeAll(@NotNull List<Preferences> preferences) {
    Set<TextMateBracePair> highlightingPairs = new HashSet<>();
    Set<TextMateBracePair> smartTypingParis = new HashSet<>();
    IndentationRules indentationRules = IndentationRules.empty();

    for (Preferences preference : preferences) {
      final Set<TextMateBracePair> localHighlightingPairs = preference.getHighlightingPairs();
      final Set<TextMateBracePair> localSmartTypingPairs = preference.getSmartTypingPairs();
      indentationRules = indentationRules.updateWith(preference.getIndentationRules());
      if (localHighlightingPairs != null) {
        highlightingPairs.addAll(localHighlightingPairs);
      }
      if (localSmartTypingPairs != null) {
        smartTypingParis.addAll(localSmartTypingPairs);
      }
    }
    return new Preferences("", highlightingPairs, smartTypingParis, indentationRules);
  }

  private static Set<TextMateBracePair> newHashSet(TextMateBracePair... pairs) {
    //noinspection SSBasedInspection
    return new HashSet<>(Arrays.asList(pairs));
  }
}