package org.jetbrains.plugins.textmate.language.preferences;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.TestUtil;
import org.jetbrains.plugins.textmate.bundles.Bundle;
import org.jetbrains.plugins.textmate.plist.CompositePlistReader;
import org.jetbrains.plugins.textmate.plist.Plist;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class PreferencesTest {
  @Test
  public void retrievePreferencesBySelector_1() throws Exception {
    final PreferencesRegistry preferencesRegistry = loadPreferences(TestUtil.PREFERENCES_TEST_BUNDLE);
    final List<Preferences> preferences = preferencesRegistry.getPreferences("text.html.basic");
    assertEquals(1, preferences.size());
    assertEquals(newHashSet(new TextMateBracePair('"', '"')), preferences.get(0).getSmartTypingPairs());
    assertEquals(newHashSet(new TextMateBracePair('`', '`')), preferences.get(0).getHighlightingPairs());
  }

  @Test
  public void retrievePreferencesBySelector_2() throws Exception {
    final PreferencesRegistry preferencesRegistry = loadPreferences(TestUtil.PREFERENCES_TEST_BUNDLE);
    final List<Preferences> preferences = preferencesRegistry.getPreferences("source.php string");
    assertEquals(1, preferences.size());
    assertEquals(newHashSet(new TextMateBracePair('(', ')')), preferences.get(0).getSmartTypingPairs());
    assertEquals(newHashSet(new TextMateBracePair('[', ']')), preferences.get(0).getHighlightingPairs());
  }

  @Test
  public void retrievePreferencesBySelectorCorrespondingToSelectorWeight() throws Exception {
    final PreferencesRegistry preferencesRegistry = loadPreferences(TestUtil.PREFERENCES_TEST_BUNDLE);
    final List<Preferences> preferences = preferencesRegistry.getPreferences("text.html source.php string.quoted.double.php");
    assertEquals(2, preferences.size());
    assertEquals(newHashSet(new TextMateBracePair('(', ')')), preferences.get(0).getSmartTypingPairs());
    assertEquals(newHashSet(new TextMateBracePair('[', ']')), preferences.get(0).getHighlightingPairs());

    assertEquals(newHashSet(new TextMateBracePair('"', '"')), preferences.get(1).getSmartTypingPairs());
    assertEquals(newHashSet(new TextMateBracePair('`', '`')), preferences.get(1).getHighlightingPairs());
  }

  @Test
  public void loadingWithTheSameScope() throws Exception {
    final PreferencesRegistry preferencesRegistry = loadPreferences(TestUtil.PREFERENCES_TEST_BUNDLE);
    final Preferences preferences = mergeAll(preferencesRegistry.getPreferences("same.scope"));
    assertEquals(newHashSet(new TextMateBracePair('[', ']'), new TextMateBracePair('(', ')')), preferences.getSmartTypingPairs());
  }

  @Test
  public void loadHighlightingPairs() throws Exception {
    PreferencesRegistry preferencesRegistry = loadPreferences(TestUtil.MARKDOWN_TEXTMATE);
    Preferences preferences = mergeAll(preferencesRegistry.getPreferences("text.html.markdown markup.raw, text.html.markdown meta.link"));
    assertEquals(newHashSet(new TextMateBracePair('[', ']'), new TextMateBracePair('`', '`')), preferences.getHighlightingPairs());
  }

  @Test
  public void loadSmartTypingPairs() throws Exception {
    PreferencesRegistry preferencesRegistry = loadPreferences(TestUtil.MARKDOWN_TEXTMATE);
    Preferences preferences = mergeAll(preferencesRegistry.getPreferences("text.html.markdown markup.raw, text.html.markdown meta.link"));
    assertEquals(newHashSet(
      new TextMateBracePair('{', '}'),
      new TextMateBracePair('(', ')'),
      new TextMateBracePair('"', '"')
    ), preferences.getSmartTypingPairs());
  }

  @Test
  public void loadDisabledPairs() throws Exception {
    PreferencesRegistry preferencesRegistry = loadPreferences(TestUtil.LATEX);
    Preferences preferences = preferencesRegistry.getPreferences("text.tex constant.character.escape.tex").iterator().next();
    Set<TextMateBracePair> smartTypingPairs = preferences.getSmartTypingPairs();
    assertNotNull(smartTypingPairs);
    assertEquals(0, smartTypingPairs.size());
  }

  @NotNull
  private static PreferencesRegistry loadPreferences(@NotNull String bundleName) throws IOException {
    final Bundle bundle = TestUtil.getBundle(bundleName);
    assertNotNull(bundle);
    final PreferencesRegistry preferencesRegistry = new PreferencesRegistry();
    for (File file : bundle.getPreferenceFiles()) {
      for (Map.Entry<String, Plist > settingsPair : bundle.loadPreferenceFile(file, new CompositePlistReader())) {
        if (settingsPair != null) {
          preferencesRegistry.fillFromPList(settingsPair.getKey(), settingsPair.getValue());
        }
      }
    }
    return preferencesRegistry;
  }

  @NotNull
  private static Preferences mergeAll(@NotNull List<Preferences> preferences) {
    Set<TextMateBracePair> highlightingPairs = new HashSet<>();
    Set<TextMateBracePair> smartTypingParis = new HashSet<>();

    for (Preferences preference : preferences) {
      final Set<TextMateBracePair> localHighlightingPairs = preference.getHighlightingPairs();
      final Set<TextMateBracePair> localSmartTypingPairs = preference.getSmartTypingPairs();
      if (localHighlightingPairs != null) {
        highlightingPairs.addAll(localHighlightingPairs);
      }
      if (localSmartTypingPairs != null) {
        smartTypingParis.addAll(localSmartTypingPairs);
      }
    }
    return new Preferences("", highlightingPairs, smartTypingParis);
  }

  private static Set<TextMateBracePair> newHashSet(TextMateBracePair... pairs) {
    return new HashSet<>(Arrays.asList(pairs));
  }
}
