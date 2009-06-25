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
      options = new SpellCheckerOptions(configuration, manager);
    }

    return options.getRoot();
  }

  public boolean isModified() {
    if (options != null) {
      if (dictionaryListWasChanged()) {
        return true;
      }

    }
    return false;
  }


  private boolean dictionaryListWasChanged() {
    return !same(options.getUserDictionaryWordsSet(), options.getShownDictionary().getWords());
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
      boolean reload = false;
      if (dictionaryListWasChanged()) {
        options.getShownDictionary().replaceAllDictionaryWords(options.getUserDictionaryWordsSet());
        reload = true;
      }
      
      /***replaceAll(configuration.activeDictionary.dictionaryWords, options.getUserDictionaryWords());
       replaceAll(configuration.activeDictionary.ignoredWords, options.getIgnoredWords());*//**//*
      manager.restartInspections();*/
      if (reload) {
        manager.applyConfiguration();
        manager.restartInspections();
      }
    }
  }


  public void reset() {
  }

  public void disposeUIResources() {
    if (options != null) {
      options.dispose();
      options = null;
    }
  }
}
