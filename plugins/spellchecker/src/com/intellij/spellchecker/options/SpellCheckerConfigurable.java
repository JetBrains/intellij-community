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
package com.intellij.spellchecker.options;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Set;


public final class SpellCheckerConfigurable implements Configurable {
  private SpellCheckerManager manager;
  private final Project myProject;
  private final SpellCheckerConfiguration configuration;

  private SpellCheckerOptions options;

  public SpellCheckerConfigurable(Project project, SpellCheckerConfiguration configuration) {
    myProject = project;
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
    manager = SpellCheckerManager.getInstance(myProject);
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
        options.getShownDictionary().replaceAllWords(options.getUserDictionaryWordsSet());
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
