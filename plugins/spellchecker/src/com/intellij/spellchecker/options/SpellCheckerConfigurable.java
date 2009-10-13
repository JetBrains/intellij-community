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
import java.util.List;
import java.util.Set;


public final class SpellCheckerConfigurable implements Configurable {
  private SpellCheckerOptions options;
  private SpellCheckerManager manager;
  private final Project myProject;


  public SpellCheckerConfigurable(Project project) {
    myProject = project;
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
      options = new SpellCheckerOptions(manager);
    }
    return options.getRoot();
  }

  public boolean isModified() {
    if (options != null) {
      return wordsListIsModified();
    }
    return false;
  }


  private boolean wordsListIsModified() {
    assert options != null;
    List<String> newWords = options.getWords();
    Set<String> words = manager.getUserDictionary().getEditableWords();
    if (words == null && newWords == null) {
      return false;
    }
    if (words == null || newWords == null || newWords.size() != words.size()) {
      return true;
    }
    return !(words.containsAll(newWords) && newWords.containsAll(words));
  }


  public void apply() throws ConfigurationException {
     manager.updateUserWords(options.getWords());
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
