package com.intellij.spellchecker.options;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Set;


public final class SpellCheckerConfigurable implements Configurable {

  private final SpellCheckerManager manager;
  private final SpellCheckerConfiguration configuration;

  private SpellCheckerOptions options;

  public SpellCheckerConfigurable(SpellCheckerManager manager, SpellCheckerConfiguration configuration) {
    this.manager = manager;
    this.configuration = configuration;
  }

  @Nls
  public String getDisplayName() {
    return SpellCheckerBundle.message("spelling");
  }

  @Nullable
  public Icon getIcon() {
    return null;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "reference.settings.ide.settings.spelling";
  }

  public JComponent createComponent() {
    if (options == null) {
      options = new SpellCheckerOptions(configuration);
    }
    return options.getRoot();
  }

  public boolean isModified() {
    if (options != null) {
      if (!same(options.getUserDictionaryWords(), configuration.USER_DICTIONARY_WORDS)) {
        return true;
      }
      if (!same(options.getIgnoredWords(), configuration.IGNORED_WORDS)) {
        return true;
      }
    }
    return false;
  }

  private static boolean same(Set<String> modified, Set<String> original) {
    if (original.size() != modified.size()) {
      return false;
    }
    modified.removeAll(original);
    return modified.size() == 0;
  }

  public void apply() throws ConfigurationException {
    if (options != null) {
      replaceAll(configuration.USER_DICTIONARY_WORDS, options.getUserDictionaryWords());
      replaceAll(configuration.IGNORED_WORDS, options.getIgnoredWords());
      manager.reloadAndRestartInspections();
    }
  }

  private static void replaceAll(Set<String> words, Set<String> newWords) {
    words.clear();
    words.addAll(newWords);
  }

  public void reset() {
    if (options != null) {
      options.setUserDictionaryWords(configuration.USER_DICTIONARY_WORDS);
      options.setIgnoredWords(configuration.IGNORED_WORDS);
    }
  }

  public void disposeUIResources() {
    if (options != null) {
      options.dispose();
      options = null;
    }
  }
}
